# 06. 실습 시나리오

모든 명령은 WSL Ubuntu에서 실행합니다. 편의를 위해 별칭을 씁니다.
```bash
alias k=kubectl
export A=payment-dev D=data-dev
```
> 터미널을 두세 개 열고 하나는 `k get pods -A -w`(또는 `k9s`)로 계속 지켜보면 훨씬 이해가 잘 됩니다.

---

## Lab 0. 로컬 smoke test (K8s 이전)
```bash
cd apps && docker compose up --build
# 브라우저: http://localhost:8088
docker compose exec mysql mysql -uroot -proot-pass payments -e "select * from accounts; select count(*) from payment_ledger;"
docker compose down -v
```
**확인**: 결제 → ACCEPTED → COMPLETED, 잔고가 감소. user-404는 REJECTED.

---

## Lab 1. 클러스터와 노드 둘러보기
```bash
k get nodes -L tier -o wide
docker ps --filter name=k8s-lab        # 노드 = 컨테이너
docker exec -it k8s-lab-worker crictl ps   # 노드 안의 containerd 컨테이너 목록
k get pods -n kube-system -o wide       # static pod(apiserver, etcd…) + DaemonSet
```
**질문**: 왜 control-plane에는 우리 앱 Pod가 안 뜰까? → `k describe node k8s-lab-control-plane | grep Taints`

---

## Lab 2. 배포하고 E2E 확인
GitOps 이전 단계로, `kubectl`과 `helm`으로 **직접** 배포합니다. 저장소 루트에서 실행하세요.
> Argo CD로 관리 중인 환경에서는 이 방식을 쓰지 마세요 (selfHeal이 수동 변경을 되돌립니다).

### 2-1. 이미지 빌드 → kind 노드에 적재
레지스트리 없이 `kind load`로 노드(컨테이너) 안의 containerd 이미지 저장소에 직접 복사합니다.
그래서 `imagePullPolicy: IfNotPresent`이면 pull 없이 바로 쓸 수 있습니다.
```bash
TAG=$(git rev-parse --short HEAD); echo $TAG     # 이미지 태그 = git short sha

docker build -t k8s-lab/frontend:$TAG apps/frontend
kind load docker-image k8s-lab/frontend:$TAG --name k8s-lab

docker build -t k8s-lab/payment-api:$TAG apps/payment-api
kind load docker-image k8s-lab/payment-api:$TAG --name k8s-lab

docker build -t k8s-lab/balance-worker:$TAG apps/balance-worker
kind load docker-image k8s-lab/balance-worker:$TAG --name k8s-lab

# 노드 안에 정말 들어갔는지 확인
docker exec k8s-lab-worker crictl images | grep k8s-lab
```

### 2-2. 데이터 계층 (Kustomize): `data-dev`
```bash
kubectl kustomize deploy/kustomize/data/overlays/dev | less   # 무엇이 적용될지 먼저 읽기
kubectl apply -k deploy/kustomize/data/overlays/dev

# StatefulSet과 토픽 생성 Job이 준비될 때까지 대기
kubectl rollout status statefulset/mysql -n $D --timeout=300s
kubectl rollout status statefulset/kafka -n $D --timeout=300s
kubectl wait --for=condition=complete job/kafka-topic-init -n $D --timeout=300s
```

### 2-3. 앱 네임스페이스 + 라벨
NetworkPolicy가 이 라벨이 붙은 네임스페이스에서 오는 접근만 허용합니다.
```bash
kubectl create namespace $A --dry-run=client -o yaml | kubectl apply -f -   # 이미 있어도 에러 없음
kubectl label namespace $A k8slab.dev/role=app --overwrite
kubectl get ns $A --show-labels
```

### 2-4. 애플리케이션 (Helm): `payment-dev`
```bash
# 모니터링(01-environments §4.4)을 설치했으면 ServiceMonitor CRD가 있다 → MON=true, 없으면 false
kubectl get crd servicemonitors.monitoring.coreos.com
MON=true

CHART=deploy/helm/payment-apps
helm template payment-apps $CHART -f $CHART/values-dev.yaml --set global.imageTag=$TAG | less   # 렌더링 결과 먼저 보기

helm upgrade --install payment-apps $CHART \
  -n $A \
  -f $CHART/values-dev.yaml \
  --set global.imageTag=$TAG \
  --set serviceMonitor.enabled=$MON \
  --wait --timeout 10m

helm list -n $A
```

### 2-5. Grafana 대시보드 (MON=true일 때만)
```bash
kubectl apply -k deploy/monitoring
```

### 2-6. 확인
```bash
k get pods -n $D -o wide     # kafka-0, mysql-0 은 worker2(tier=data)에만
k get pods -n $A -o wide     # payment-api 2개가 두 노드에 분산
```
브라우저에서 `http://payment.localtest.me` → 결제 요청 → "응답 Pod" 열에서 두 Pod가 번갈아 응답하는지 확인합니다.

---

## Lab 3. Service 로드밸런싱과 인메모리 상태의 한계
```bash
for i in $(seq 1 10); do curl -s http://payment.localtest.me/api/whoami; echo; done   # Pod 분산 확인
```
1. FE에서 "10건 연속 요청" → 일부 행의 응답 Pod에 `(404)`가 잠깐 찍혔다가 COMPLETED로 바뀌는지 확인
2. payment-api Pod 하나를 삭제 → 그 Pod가 가지고 있던 결제 이력이 사라지는 것을 확인
```bash
k delete pod -n $A $(k get pod -n $A -l app.kubernetes.io/name=payment-api -o name | head -1 | cut -d/ -f2)
curl -s http://payment.localtest.me/api/payments | jq length   # 여러 번 호출하면 Pod마다 결과가 다름
```
**배운 점**: 왜 앱은 Stateless여야 하고 상태는 외부 저장소에 두는가 ([04-workflows §4](04-workflows.md#4-멀티-replica와-인메모리-상태-문제))

---

## Lab 4. worker 장애와 멱등성
```bash
# 터미널 1: worker 로그
k logs -n $A -l app.kubernetes.io/name=balance-worker -f --max-log-requests 5
# 터미널 2: FE에서 연속 요청을 누르는 동시에 worker 삭제
k delete pod -n $A -l app.kubernetes.io/name=balance-worker
# 정합성 확인
k exec -n $D mysql-0 -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" payments -e "
  select status, count(*) from payment_ledger group by status;
  select * from accounts;"'
k create job -n $D --from=cronjob/balance-reconcile check-1 && k logs -n $D job/check-1 -f
```
**확인**: reconcile Job이 `OK`를 출력하고, 같은 payment_id가 두 번 반영되지 않습니다.

---

## Lab 5. Kafka consumer group과 파티션
```bash
KG="k exec -n $D kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092"
$KG --list                                 # balance-worker, payment-api-<pod> …
$KG --describe --group balance-worker      # 파티션 3개를 누가 가져갔나

k scale deploy/balance-worker -n $A --replicas=3 && sleep 20 && $KG --describe --group balance-worker
k scale deploy/balance-worker -n $A --replicas=4 && sleep 20 && $KG --describe --group balance-worker
```
**확인**: replicas=4이면 한 consumer는 파티션을 받지 못합니다 (`-`로 표시).
> ArgoCD로 관리 중이면 selfHeal이 replicas를 되돌립니다 → values 파일을 수정해 push하세요.

---

## Lab 6. DLT (Dead Letter Topic)
```bash
# 깨진 메시지를 직접 넣는다
k exec -it -n $D kafka-0 -- /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic payment.requested --property parse.key=true --property key.separator=:
> user-1:{this is not json
> user-1:{"paymentId":"bad-1","userId":"user-1","type":"REFUND","amount":10,"requestedAt":"2026-01-01T00:00:00Z"}
# (Ctrl+C)

k exec -n $D kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.requested.dlt --from-beginning --timeout-ms 5000 \
  --property print.headers=true
```
**확인**: 두 메시지가 DLT에 들어가고(`kafka_dlt-exception-message` 헤더), 이후의 정상 결제는 계속 처리됩니다.

---

## Lab 7. Probe와 자가 치유
```bash
# 의존성 장애: MySQL을 잠시 0으로
k scale sts/mysql -n $D --replicas=0
curl -s -o /dev/null -w '%{http_code}\n' http://payment.localtest.me/api/balances/user-1   # 약 30초 뒤 500 (Hikari 연결 타임아웃)
k get pods -n $A    # payment-api는 계속 Ready → readiness에 DB를 넣을지 말지는 설계 선택 (넣으면 DB 장애 시 전체 트래픽 차단)
k scale sts/mysql -n $D --replicas=1
k get pvc -n $D        # PVC는 그대로 → 데이터 유지

# liveness: 컨테이너 안에서 java 프로세스를 멈춘다 (SIGSTOP)
POD=$(k get pod -n $A -l app.kubernetes.io/name=payment-api -o jsonpath='{.items[0].metadata.name}')
k debug -n $A $POD -it --image=busybox:1.37 --target=app -- sh -c 'kill -STOP 1'
k get pod -n $A $POD -w      # RESTARTS 증가
k describe pod -n $A $POD | grep -A5 'Last State'
```

---

## Lab 8. 롤링 업데이트와 롤백
```bash
# 코드 수정 (예: PaymentController whoami 응답에 version 필드 추가) 후
docker build -t k8s-lab/payment-api:v2 apps/payment-api
kind load docker-image k8s-lab/payment-api:v2 --name k8s-lab
helm upgrade payment-apps deploy/helm/payment-apps -n $A -f deploy/helm/payment-apps/values-dev.yaml --reuse-values --set global.imageTag=v2
k rollout status deploy/payment-api -n $A
k get rs -n $A -l app.kubernetes.io/name=payment-api      # old RS는 0으로 남음

# 무중단 확인: 다른 터미널에서
while true; do curl -s -o /dev/null -w '%{http_code} ' http://payment.localtest.me/api/whoami; sleep 0.2; done

helm rollback payment-apps -n $A     # 또는 k rollout undo deploy/payment-api -n $A
```
> frontend/worker는 v2 태그 이미지가 없으므로 `--set global.imageTag`는 payment-api만 빌드한 경우 주의하세요 (모두 빌드하거나 앱별 태그를 values로 분리하는 것도 연습 과제입니다).

---

## Lab 9. HPA 부하 테스트
```bash
k get hpa -n $A -w        # 터미널 1
k top pods -n $A           # 터미널 2

# 터미널 3: 클러스터 안에서 부하 생성
k run load -n $A --rm -it --image=busybox:1.37 --restart=Never -- \
  sh -c 'while true; do wget -q -O- http://payment-api:8080/api/payments > /dev/null; done'
```
**확인**: CPU가 60%를 넘으면 replicas 2 → 최대 5. 부하를 끄면 약 60초 뒤에 줄어듭니다(`stabilizationWindowSeconds`).

---

## Lab 10. NetworkPolicy
```bash
# 라벨 없는 네임스페이스(default)에서 mysql 접근 → 차단(timeout)
k run np-test --rm -it --image=busybox:1.37 --restart=Never -- nc -zv -w 3 mysql.data-dev.svc.cluster.local 3306

# payment-dev 네임스페이스지만 라벨이 api/worker가 아닌 Pod → mysql 차단, kafka 허용
k run np-test -n $A --rm -it --image=busybox:1.37 --restart=Never -- sh -c \
  'nc -zv -w 3 mysql.data-dev.svc.cluster.local 3306; nc -zv -w 3 kafka.data-dev.svc.cluster.local 9092'

# payment-api 라벨로 위장하면? → 허용 (라벨 기반 정책의 한계 → 실무에서는 RBAC로 Pod 생성 권한도 제한)
k run np-test -n $A --rm -it --image=busybox:1.37 --restart=Never -l app.kubernetes.io/name=payment-api -- \
  nc -zv -w 3 mysql.data-dev.svc.cluster.local 3306
```

---

## Lab 11. 노드 drain과 PDB
```bash
k get pdb -n $A
k drain k8s-lab-worker --ignore-daemonsets --delete-emptydir-data
k get pods -n $A -o wide    # 모두 worker2로 이동, PDB가 동시 축출을 제한
k uncordon k8s-lab-worker

# 데이터 노드 drain → kafka/mysql은 갈 곳이 없어 Pending
k drain k8s-lab-worker2 --ignore-daemonsets --delete-emptydir-data
k get pods -n $D            # Pending
k describe pod mysql-0 -n $D | tail -5    # node affinity / volume node affinity 불일치
k uncordon k8s-lab-worker2
```
**배운 점**: local PV + required affinity = 노드 장애에 취약 → 운영에서는 복제(다중 브로커, MySQL replication)와 네트워크 스토리지를 씁니다.

---

## Lab 12. RBAC
```bash
k auth can-i --list -n $D --as dev-user
k get pods -n $D --as dev-user           # OK
k get secrets -n $D --as dev-user        # Forbidden
k delete pod mysql-0 -n $D --as dev-user # Forbidden
```

---

## Lab 13. ConfigMap 변경 → 롤링 재시작
```bash
helm upgrade payment-apps deploy/helm/payment-apps -n $A -f deploy/helm/payment-apps/values-dev.yaml \
  --reuse-values --set frontend.pollIntervalMs=3000
k get pods -n $A -l app.kubernetes.io/name=frontend -w   # checksum이 바뀌어 새 Pod 생성
```

---

## Lab 14. GitOps 전환
[05-tooling.md §4](05-tooling.md#4-argo-cd)를 따라 진행합니다.
1. [05-tooling.md §4 시작하기](05-tooling.md#시작하기)의 3) root 등록 → UI에서 root → 3개 Application 트리 확인
2. `values-dev.yaml`에서 `balanceWorker.replicas: 2`로 바꿔 push → 자동 반영
3. `k delete svc frontend -n $A` → selfHeal로 복구
4. Git에서 `templates/serviceaccounts.yaml`의 하나를 지우고 push → prune 확인 후 되돌리기

---

## Lab 15. Grafana 관찰
`http://grafana.localtest.me` (admin/admin) → Dashboards → **K8s Lab - Payment Platform**
- Lab 9 부하 중 HTTP rate, CPU, Pod 수가 함께 증가
- worker를 0으로 줄인 뒤 결제를 여러 건 요청 → 모두 ACCEPTED로 쌓임 → worker를 다시 켜면 **Consumer lag**가 순간 치솟았다가 0으로 해소
  (lag 메트릭은 consumer가 직접 보고하므로 worker가 0개일 때는 값이 없습니다. 이때는 Lab 5의 `kafka-consumer-groups.sh --describe`로 LAG 열을 확인하세요)
