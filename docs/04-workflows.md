# 04. 상세 워크플로우

1. [결제 요청 처리 흐름 (정상)](#1-결제-요청-처리-흐름-정상)
2. [요청 라우팅 내부 동작: Ingress → Service → Pod](#2-요청-라우팅-내부-동작)
3. [Kafka 파티션과 Consumer Group](#3-kafka-파티션과-consumer-group)
4. [멀티 replica와 인메모리 상태 문제](#4-멀티-replica와-인메모리-상태-문제)
5. [장애 시나리오](#5-장애-시나리오)
6. [Pod 기동과 종료 라이프사이클](#6-pod-기동과-종료-라이프사이클)
7. [롤링 업데이트](#7-롤링-업데이트)
8. [HPA 오토스케일링 루프](#8-hpa-오토스케일링-루프)
9. [배포 워크플로우: 수동 → GitOps](#9-배포-워크플로우)
10. [모니터링 수집 흐름](#10-모니터링-수집-흐름)

---

## 1. 결제 요청 처리 흐름 (정상)

**핵심**: API는 DB에 쓰지 않습니다. 인메모리에 저장하고 Kafka에 던진 다음 바로 `202 Accepted`를 돌려줍니다. 실제 잔고 반영은 worker가 **비동기**로 처리합니다.

```mermaid
sequenceDiagram
  autonumber
  actor U as 브라우저
  participant T as Traefik (Ingress)
  participant A as payment-api Pod
  participant M as 인메모리<br/>ConcurrentHashMap
  participant K as Kafka
  participant W as balance-worker
  participant D as MySQL

  U->>T: POST /api/payments {userId, type, amount}
  T->>A: Service(payment-api) → Pod 하나 선택
  A->>M: save(ACCEPTED)
  A-)K: send(payment.requested, key=userId) 비동기
  A-->>U: 202 Accepted {id, status: ACCEPTED}<br/>X-Served-By: payment-api-xxx

  Note over K,W: 같은 userId → 같은 파티션 → 순서 보장

  K->>W: poll() payment.requested
  activate W
  W->>D: BEGIN
  W->>D: SELECT ledger WHERE payment_id=? (있으면 저장된 결과 반환)
  W->>D: UPDATE accounts SET balance = balance + delta<br/>WHERE user_id=? AND balance + delta >= 0
  alt 1 row updated
    W->>D: INSERT ledger status=COMPLETED, balance_after
  else 0 rows (잔고 부족 / 계좌 없음)
    W->>D: INSERT ledger status=REJECTED, reason
  end
  W->>D: COMMIT
  W->>K: send(payment.result) 동기 대기
  W->>K: offset commit (ack-mode: record)
  deactivate W

  K-)A: payment.result (모든 api Pod가 각자 수신)
  A->>M: applyResult(COMPLETED, balanceAfter)

  loop 1초마다 폴링
    U->>T: GET /api/payments/{id}
    T->>A: (아무 Pod)
    A->>M: find(id)
    A-->>U: 200 {status: COMPLETED} 또는 404
  end
  U->>A: GET /api/balances/{userId}
  A->>D: SELECT balance (api_ro 계정)
  A-->>U: {balance}
```

| 단계 | 사용하는 K8s 요소 |
|---|---|
| 1~2 | Ingress, Traefik, Service(ClusterIP), EndpointSlice |
| 4 | Kafka bootstrap 주소 = Service DNS `kafka.data-dev.svc.cluster.local` |
| 7~13 | NetworkPolicy(app 네임스페이스 → mysql 허용), Secret(DB 비밀번호) |
| 17 | Downward API(POD_NAME → consumer group id) |

---

## 2. 요청 라우팅 내부 동작

```mermaid
flowchart LR
  B["브라우저<br/>payment.localtest.me:80"] --> DD["Docker Desktop<br/>포트 포워딩"]
  DD --> NP["kind control-plane 컨테이너<br/>:30080 NodePort"]
  NP --> KP1["kube-proxy<br/>iptables/nftables 규칙"]
  KP1 --> TR["Traefik Pod"]
  TR -->|"Host + Path 매칭<br/>Ingress 규칙 watch"| SVC["Service payment-api<br/>ClusterIP 10.96.x.x:8080"]
  SVC -.->|"selector<br/>app.kubernetes.io/name=payment-api"| ES[("EndpointSlice<br/>Ready Pod IP 목록")]
  SVC --> KP2["kube-proxy 규칙<br/>무작위 분산"]
  KP2 --> P1["payment-api-aaa<br/>10.244.1.5 (worker)"]
  KP2 --> P2["payment-api-bbb<br/>10.244.2.7 (worker2)"]
  CD["CoreDNS"] -.->|"payment-api.payment-dev.svc<br/>→ ClusterIP"| TR
```

- **Service는 프로세스가 아닙니다.** 가상 IP와 kube-proxy가 만든 커널 규칙일 뿐이고, 실제 트래픽은 Pod IP로 바로 갑니다.
- **readinessProbe에 실패한 Pod는 EndpointSlice에서 빠집니다** → 트래픽을 받지 않습니다 (재시작은 하지 않음).
- Traefik은 Service의 EndpointSlice를 직접 읽어 Pod로 바로 보내기도 합니다 (구현마다 다름).
- 확인: `kubectl get endpointslices -n payment-dev -l kubernetes.io/service-name=payment-api -o wide`

---

## 3. Kafka 파티션과 Consumer Group

```mermaid
flowchart LR
  subgraph PRODUCERS["Producers"]
    A1["payment-api-aaa"]
    A2["payment-api-bbb"]
  end

  subgraph REQ["topic: payment.requested"]
    P0["partition 0<br/>user-2"]
    P1["partition 1<br/>user-1, user-404"]
    P2["partition 2<br/>user-3"]
  end

  subgraph G1["group: balance-worker (파티션 분배)"]
    W1["worker-1 → p0, p1"]
    W2["worker-2 → p2"]
  end

  subgraph RES["topic: payment.result"]
    R["partition 0..2"]
  end

  subgraph G2["group: payment-api-aaa"]
    C1["payment-api-aaa → 전체 파티션"]
  end
  subgraph G3["group: payment-api-bbb"]
    C2["payment-api-bbb → 전체 파티션"]
  end

  A1 & A2 -->|"key=userId<br/>hash(key) % 3"| REQ
  P0 & P1 --> W1
  P2 --> W2
  W1 & W2 --> RES
  R --> C1
  R --> C2
```

| 규칙 | 결과 |
|---|---|
| 하나의 파티션은 group 안에서 **한 consumer만** 읽는다 | worker replica를 4개 이상으로 늘려도 3개만 일한다 (파티션 수가 병렬도의 상한) |
| 같은 key는 항상 같은 파티션 | 한 사용자의 결제는 **순서대로** 처리된다 → 잔고 계산 race 없음 |
| group이 다르면 각자 전체를 읽는다 | api Pod마다 다른 group을 쓰면 **브로드캐스트**처럼 동작 |
| replica 수가 바뀌면 rebalance | worker를 스케일하면 잠깐 소비가 멈췄다가 파티션이 재분배된다 |

```bash
# 파티션 할당과 lag 확인
kubectl exec -n data-dev kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group balance-worker
```

---

## 4. 멀티 replica와 인메모리 상태 문제

payment-api는 결제 상태를 **Pod 메모리**에 보관합니다. replica가 2개 이상이면 다음과 같은 일이 생깁니다.

```mermaid
sequenceDiagram
  autonumber
  actor U as 브라우저
  participant S as Service payment-api
  participant A as Pod A
  participant B as Pod B
  participant K as Kafka

  U->>S: POST /api/payments
  S->>A: (A로 분산)
  A->>A: 메모리에 ACCEPTED 저장
  A-->>U: 202 {id: p1}

  U->>S: GET /api/payments/p1
  S->>B: (이번엔 B로 분산)
  B-->>U: 404 Not Found (B는 p1을 모름)
  Note over U: FE는 404를 "아직 ACCEPTED"로 처리

  K-)A: payment.result p1 (group payment-api-A)
  K-)B: payment.result p1 (group payment-api-B)
  B->>B: 없던 p1을 결과로 새로 생성 (upsert)

  U->>S: GET /api/payments/p1
  S->>B: (다시 B로 분산)
  B-->>U: 200 COMPLETED ✅
```

**배울 점**
- K8s Deployment의 Pod는 언제든 죽고, 바뀌고, 늘어납니다 → **애플리케이션은 Stateless여야 합니다.**
- 이 프로젝트는 "모든 Pod가 결과를 구독"하는 방식으로 최종 일관성(eventual consistency)을 맞췄지만, Pod가 재시작되면 그 Pod의 기록은 사라집니다.
- 실무 해법: **Redis 같은 외부 저장소**, 또는 sticky session(권장하지 않음), 또는 DB 조회.
- 직접 보기: FE 화면의 "응답 Pod" 열에 `(404)`가 찍히는 것을 확인하세요.

---

## 5. 장애 시나리오

### 5-1. worker가 처리 도중 죽음 → 재전송 → 멱등성으로 중복 방지

```mermaid
sequenceDiagram
  autonumber
  participant K as Kafka
  participant W1 as worker (old)
  participant W2 as worker (new)
  participant D as MySQL

  K->>W1: payment p1 (offset 42)
  W1->>D: BEGIN / UPDATE balance / INSERT ledger / COMMIT ✅
  W1--xK: 💥 Pod 종료 (result 발행·offset 커밋 전)
  Note over K: offset 42 미커밋 → 재전달 대상
  Note over W2: Deployment가 새 Pod 생성<br/>consumer group rebalance
  K->>W2: payment p1 (offset 42) 다시 전달
  W2->>D: SELECT ledger WHERE payment_id = p1
  D-->>W2: 이미 있음 (이전 처리 결과)
  W2->>K: payment.result p1 (이전과 같은 결과) 재발행
  W2->>K: offset 42 커밋
  Note over D: 잔고는 한 번만 반영됨 ✅
```

Kafka의 기본 보장은 **at-least-once**(최소 한 번)입니다. 그래서 consumer는 **멱등(idempotent)**해야 합니다. 이 프로젝트는 `payment_ledger.payment_id` **UNIQUE 제약**으로 멱등성을 구현합니다 (PK는 `AUTO_INCREMENT`로 생성하는 `id`). 먼저 조회해서 있으면 저장된 결과를 돌려주고, 두 consumer가 동시에 같은 결제를 처리하는 경합은 UNIQUE 위반 → 트랜잭션 롤백(잔고 반영 포함) → 재시도로 막습니다.

> ⚠️ dev는 `balanceWorker.ddlAuto: create`라 worker Pod가 **새로 뜰 때마다 테이블이 DROP/재생성**됩니다. 이 시나리오(새 Pod가 재전달 메시지 처리)를 관찰하려면 `--set balanceWorker.ddlAuto=update`로 배포하세요.

### 5-2. 잘못된 메시지 → 재시도 → DLT

```mermaid
flowchart LR
  M["메시지 수신"] --> P{"처리"}
  P -->|성공| OK["result 발행<br/>offset 커밋"]
  P -->|"JacksonException<br/>IllegalArgumentException"| DLT["payment.requested.dlt<br/>(즉시, 재시도 안 함)"]
  P -->|"기타 예외<br/>DB 일시 장애 등"| R{"재시도<br/>1초 × 2회"}
  R -->|성공| OK
  R -->|실패| DLT
  DLT --> C["offset 커밋 → 다음 메시지 진행<br/>(poison pill로 파티션이 막히지 않음)"]
```

### 5-3. 장애 유형별 K8s 동작

| 장애 | K8s 반응 | 애플리케이션 영향 |
|---|---|---|
| payment-api 컨테이너 hang | livenessProbe 실패 → **컨테이너 재시작** | 그 Pod의 인메모리 상태 유실 |
| payment-api가 DB/Kafka 연결 불가 | readiness는 유지(기본 설정) | 요청은 받지만 전송 실패 시 FAILED |
| Pod OOMKilled | 컨테이너 재시작, `kubectl describe`에 `OOMKilled` | `-XX:MaxRAMPercentage=75`로 힙을 limit 안에 맞춤 |
| worker Pod 삭제 | Deployment가 새 Pod 생성, 처리 중이던 메시지는 재전달 | 멱등성으로 안전 |
| kafka-0 Pod 삭제 | StatefulSet이 **같은 이름·같은 PVC**로 재생성 | 데이터 보존, 잠시 produce 실패 → FAILED |
| mysql-0 Pod 삭제 | 같은 PVC로 재생성 | worker는 재시도 후 처리, 실패 시 DLT |
| worker2 노드 장애 | kafka/mysql은 required affinity라 **다른 노드로 못 감** → Pending | 실습 [06-labs.md](06-labs.md) 참고 |

---

## 6. Pod 기동과 종료 라이프사이클

```mermaid
stateDiagram-v2
  [*] --> Pending: 스케줄링 대기
  Pending --> Init: 노드 배정 + 이미지 준비
  state Init {
    [*] --> wait_kafka: initContainer
    wait_kafka --> wait_mysql: nc -z kafka 9092 성공
    wait_mysql --> [*]: nc -z mysql 3306 성공
  }
  Init --> Starting: app 컨테이너 시작
  Starting --> Starting: startupProbe 실패 (최대 30×5s)
  Starting --> Running_NotReady: startupProbe 성공
  Running_NotReady --> Ready: readinessProbe 성공 → EndpointSlice 등록
  Ready --> Running_NotReady: readiness 실패 → 트래픽 제외
  Ready --> Restarting: livenessProbe 실패
  Restarting --> Starting
  Ready --> Terminating: 삭제 / 롤링 업데이트 / 스케일 다운
  state Terminating {
    [*] --> remove_endpoint: EndpointSlice에서 제거 (동시에)
    remove_endpoint --> prestop: preStop sleep 5s
    prestop --> sigterm: SIGTERM → Spring graceful shutdown (최대 20s)
    sigterm --> [*]
  }
  Terminating --> [*]: 종료 (terminationGracePeriodSeconds 40s 초과 시 SIGKILL)
```

**preStop sleep이 필요한 이유**: Pod 삭제 시 "EndpointSlice에서 제거"와 "SIGTERM 전송"이 **동시에** 시작됩니다. kube-proxy와 Traefik이 변경을 반영하기 전에 앱이 먼저 내려가면 일부 요청이 실패합니다. 몇 초 기다리면 새 요청이 더 이상 들어오지 않습니다.

---

## 7. 롤링 업데이트

`maxSurge: 1, maxUnavailable: 0` 설정이므로 **새 Pod가 Ready가 된 뒤에** 기존 Pod를 하나씩 내립니다.

```mermaid
sequenceDiagram
  participant D as Deployment controller
  participant RSo as ReplicaSet (old, v1)
  participant RSn as ReplicaSet (new, v2)

  Note over RSo: v1 Pod ×2 Ready
  D->>RSn: 생성, replicas=1 (surge)
  Note over RSn: v2 Pod 1개 → startup → Ready
  D->>RSo: replicas=1
  D->>RSn: replicas=2
  Note over RSn: v2 Pod 2번째 Ready
  D->>RSo: replicas=0 (기록용으로 남김, revisionHistoryLimit=5)
  Note over D: kubectl rollout undo 로 RSo를 다시 키울 수 있음
```

```bash
kubectl rollout status deploy/payment-api -n payment-dev
kubectl rollout history deploy/payment-api -n payment-dev
kubectl rollout undo deploy/payment-api -n payment-dev   # (GitOps에서는 git revert 권장)
```

---

## 8. HPA 오토스케일링 루프

```mermaid
flowchart LR
  K["kubelet / cAdvisor<br/>컨테이너 CPU 사용량"] --> MS["metrics-server<br/>metrics.k8s.io API"]
  MS --> HPA["HPA controller<br/>15초마다 평가"]
  HPA -->|"desired = ceil(current × 실제사용률 / 목표 60%)"| DEP["Deployment payment-api<br/>spec.replicas 변경"]
  DEP --> RS["ReplicaSet"] --> PODS["Pod 2 → 5"]
  PODS --> K
  HPA -.->|"scaleDown 안정화 60초"| DEP
```

- 사용률은 **requests 대비** 비율입니다 (`requests.cpu: 100m`에서 80m 사용 → 80%). requests가 없으면 HPA가 동작하지 않습니다.
- HPA가 replicas를 관리하므로 Helm 템플릿은 HPA를 켜면 `spec.replicas`를 렌더링하지 않고, ArgoCD는 `ignoreDifferences`로 이 필드를 무시합니다.
- worker는 CPU보다 **consumer lag**를 기준으로 스케일하는 게 맞습니다 → KEDA(Kafka scaler)가 다음 학습 주제입니다.

---

## 9. 배포 워크플로우

### 9-1. 전체 부트스트랩 순서

```mermaid
flowchart TB
  S0["00-setup-wsl.sh<br/>kubectl · kind · helm"] --> S1["10-cluster-up.sh<br/>kind 3노드"]
  S1 --> S2["20-platform.sh<br/>Traefik · metrics-server<br/>kube-prometheus-stack · ArgoCD"]
  S2 --> S3["30-build-load.sh<br/>docker build → kind load"]
  S3 --> CHOICE{"배포 방식"}
  CHOICE -->|"학습 1단계"| M["40-deploy-manual.sh<br/>kubectl apply -k (data)<br/>helm upgrade --install (apps)"]
  CHOICE -->|"학습 2단계"| G["50-gitops.sh<br/>ArgoCD root Application 등록"]
  M -.->|"익숙해지면 전환<br/>(스크립트가 helm 릴리스 정리)"| G
```

### 9-2. GitOps 변경 반영 흐름 (이미지 업데이트)

```mermaid
sequenceDiagram
  autonumber
  actor Dev as 개발자 (WSL)
  participant Docker as Docker / kind 노드
  participant Git as GitHub
  participant Argo as Argo CD
  participant K8s as 클러스터

  Dev->>Dev: 코드 수정 + git commit
  Dev->>Docker: ./scripts/30-build-load.sh<br/>(tag = git short sha)
  Note over Docker: 레지스트리 대신 kind 노드에 직접 적재
  Dev->>Dev: values-dev.yaml global.imageTag = abc1234
  Dev->>Git: git commit + push
  loop 기본 3분 주기 polling (또는 UI Refresh / webhook)
    Argo->>Git: 원하는 상태(desired) 조회
  end
  Argo->>Argo: helm template / kustomize build<br/>→ live 상태와 diff → OutOfSync
  Argo->>K8s: wave 0: data-dev (Kustomize)
  Argo->>K8s: wave 1: payment-apps-dev (Helm) → 롤링 업데이트
  Argo->>K8s: wave 2: monitoring-dashboards
  Argo->>K8s: PostSync 훅: kafka-topic-init Job
  K8s-->>Argo: Health 상태 (Progressing → Healthy)
  Argo-->>Dev: Synced / Healthy
  Note over Argo,K8s: 누가 kubectl edit로 직접 바꾸면<br/>selfHeal이 Git 상태로 되돌림
```

> 실무에서는 CI(GitHub Actions)가 이미지를 레지스트리에 push하고 values 태그를 자동으로 커밋합니다 (또는 Argo CD Image Updater 사용).

### 9-3. App of Apps 구조

```mermaid
flowchart TB
  ROOT["Application: root<br/>source: deploy/argocd (Helm)"] --> A1["Application: data-dev<br/>wave 0<br/>deploy/kustomize/data/overlays/dev"]
  ROOT --> A2["Application: payment-apps-dev<br/>wave 1<br/>deploy/helm/payment-apps + values-dev.yaml"]
  ROOT --> A3["Application: monitoring-dashboards<br/>wave 2<br/>deploy/monitoring"]
  A1 --> R1["Namespace · StatefulSet ×2 · Service ×3<br/>Job · CronJob · NetworkPolicy · RBAC"]
  A2 --> R2["Deployment ×3 · Service ×3 · Ingress<br/>HPA · PDB · ServiceMonitor · Secret"]
  A3 --> R3["ConfigMap (grafana_dashboard=1)"]
```

---

## 10. 모니터링 수집 흐름

```mermaid
flowchart LR
  subgraph APP["payment-dev"]
    API["payment-api<br/>/actuator/prometheus"]
    WK["balance-worker<br/>/actuator/prometheus"]
    SM["ServiceMonitor<br/>selector k8slab.dev/metrics=true"]
  end
  subgraph MON["monitoring"]
    OP["Prometheus Operator"]
    PR["Prometheus"]
    GR["Grafana"]
    SC["Grafana sidecar"]
    NE["node-exporter<br/>DaemonSet"]
    KSM["kube-state-metrics"]
    CM["ConfigMap<br/>grafana_dashboard=1"]
  end
  OP -->|"watch CRD"| SM
  OP -->|"scrape 설정 생성"| PR
  PR -->|"15s scrape"| API & WK
  PR --> NE & KSM
  SC -->|"라벨로 탐색"| CM --> GR
  GR -->|PromQL| PR
```

| 메트릭 | 출처 | 의미 |
|---|---|---|
| `payments_requested_total{type}` | payment-api (Micrometer counter) | 접수된 결제 수 |
| `payments_processed_total{status}` | balance-worker | 처리 결과별 수 |
| `payments_results_total{status}` | payment-api | api Pod가 받은 결과 이벤트 수 |
| `kafka_consumer_fetch_manager_records_lag_max` | worker의 Kafka 클라이언트 | consumer lag (밀린 메시지) |
| `http_server_requests_seconds_*` | Spring MVC | 요청 수와 지연 시간 |
| `container_cpu_usage_seconds_total` | kubelet/cAdvisor | Pod CPU |
