# 08. 처음 접할 때 헷갈리는 것들 (FAQ)

실습하면서 실제로 나온 질문을 모았습니다. 각 항목은 **결론 → 이유 → 확인 명령** 순서이며, 자세한 내용은 링크한 문서에 있습니다.

- [A. 환경과 도구](#a-환경과-도구)
- [B. 어디에 설치되고, 어디서 실행되나](#b-어디에-설치되고-어디서-실행되나)
- [C. 확장](#c-확장)
- [D. 데이터와 메시징](#d-데이터와-메시징)

---

## A. 환경과 도구

### A-1. kind로 띄우기 전에 docker compose 컨테이너부터 올려야 하나요?
**아니요. 둘은 독립된 환경입니다.**

| | docker compose (Lab 0) | kind (Lab 2~) |
|---|---|---|
| 목적 | K8s 없이 **앱 코드만** 빠르게 확인 (선택) | 실제 K8s 실습 |
| MySQL·Kafka | Docker 컨테이너 | 클러스터 **안의 Pod** (StatefulSet) |
| 데이터 위치 | compose 익명 볼륨 | data 노드 안의 PVC |

둘을 동시에 띄우면 메모리만 이중으로 씁니다. 겹치는 건 **이미지**뿐이고, 그것도 `kind load`로 노드에 넣어야 쓸 수 있습니다([A-8](#a-8-docker-build로-이미지를-만들었는데-pod가-errimagepull이에요)).

### A-2. "저장소 루트(k8s-lab/)에서 실행"은 어디를 말하나요?
**`deploy/`, `apps/`, `docs/`가 바로 보이는 폴더**입니다. clone할 때 붙인 폴더 이름(`~/k8s-lab`)일 수도 있고, Windows 폴더를 그대로 쓴다면 `/mnt/c/dev/k8s`입니다. 명령에 나오는 `deploy/kind/kind-config.yaml` 같은 경로가 모두 이 위치 기준의 상대 경로라서 중요합니다.
```bash
git rev-parse --show-toplevel     # 저장소 루트 출력
```
> 폴더 이름 `k8s-lab`과 **클러스터 이름** `k8s-lab`(`kind-config.yaml`의 `name:`)은 다른 것입니다. `kind-k8s-lab`(kubectl 컨텍스트), `k8s-lab-worker`(노드 컨테이너)는 클러스터 이름에서 나왔습니다.

### A-3. PowerShell에서 `wsl`을 실행하면 `execvpe(bash) failed`가 나요
**WSL 기본 배포판이 `docker-desktop`으로 잡혀 있어서 그렇습니다.** Docker Desktop이 내부용으로 쓰는 최소 배포판이라 bash가 없습니다.
```powershell
wsl -l -v                  # * 표시가 기본 배포판
wsl --set-default Ubuntu   # 기본값 변경 (또는 매번 wsl -d Ubuntu)
```

### A-4. kubectl을 다시 설치하면 기존 것은 어떻게 되나요? 출력에 Kustomize는 왜 나오나요?
- `sudo install -m 0755 /tmp/kubectl /usr/local/bin/kubectl`은 **같은 경로의 기존 파일을 교체**합니다. `which -a kubectl`에 경로가 하나만 나오면 버전이 섞이지 않습니다. Docker Desktop이 넣어 둔 kubectl이 있었다면, Docker Desktop을 업데이트한 뒤 다시 덮어써질 수 있습니다.
- **Kustomize가 kubectl에 들어 있습니다.** 그래서 `kubectl apply -k`를 바로 쓸 수 있습니다.
- kubectl과 클러스터의 버전은 **마이너 버전 ±1까지** 공식 지원됩니다. `kubectl version`의 Client와 Server 줄을 비교해 보세요.
```bash
which -a kubectl && ls -l $(which kubectl)   # 링크(->)인지 실제 파일인지
```

### A-5. `curl ... | tar -xz -C /tmp`로 helm을 받았는데 /tmp에 tar.gz가 없어요
**정상입니다.** `|`(파이프)로 받은 데이터를 바로 tar에 넘겨 풀었기 때문에 압축 파일은 디스크에 저장되지 않습니다. 풀린 결과는 `/tmp/linux-amd64/helm`에 있습니다.

| 부분 | 의미 |
|---|---|
| `curl -f` | HTTP 에러(404 등)면 실패로 끝냄. 에러 HTML이 tar로 넘어가는 것을 막음 |
| `-sSL` | 진행률은 숨기고(`-s`), 에러는 보여 주고(`-S`), 리다이렉트를 따라감(`-L`) |
| `tar -xz -C /tmp` | gzip을 풀고(`-z`) 압축을 해제해서(`-x`) /tmp에 둠(`-C`). `-f`가 없으므로 파이프에서 읽음 |
| `install -m 0755` | `cp` + `chmod 755`를 한 번에 |
| `v4.3.0+gbec5b06` | `+` 뒤는 빌드한 git 커밋 해시 (SemVer 빌드 메타데이터) |

> 운영 서버에서는 파일로 받아 `sha256sum -c`로 무결성을 검증한 뒤 푸는 게 일반적입니다. 파이프 방식은 검증할 원본 파일이 남지 않습니다.

### A-6. `kind get clusters`는 어딘가에 저장된 목록을 읽는 건가요?
**아니요. kind에는 데몬도, 상태 저장소도 없습니다.** kind는 노드 컨테이너를 만들 때 `io.x-k8s.kind.cluster=<이름>` 라벨을 붙여 둡니다. `get clusters`는 그 라벨이 붙은 컨테이너를 Docker에 **그때그때 조회한 결과**입니다.
```bash
docker ps -a --filter label=io.x-k8s.kind.cluster --format '{{.Names}}  {{.Label "io.x-k8s.kind.cluster"}}'
```
클러스터를 만든 뒤에는 kind가 개입하지 않습니다. 모든 작업은 kubectl과 helm이 API 서버로 합니다. kind가 다시 등장하는 건 `kind load`와 `kind delete`뿐입니다.

### A-7. kind는 Docker API로 Pod를 만드는 건가요?
**Docker로 만드는 건 "노드"까지입니다.** Pod는 노드 컨테이너 **안에서** kubelet이 containerd에 요청해서 만듭니다. Docker는 Pod의 존재를 모릅니다.
```
dockerd ─(kind)─▶ 노드 컨테이너 ─▶ kubelet ─CRI─▶ containerd ─▶ Pod
```
```bash
docker ps --format '{{.Names}}' | grep k8s-lab   # 노드 3개뿐
docker exec k8s-lab-worker crictl ps             # 노드 안의 Pod 컨테이너들
```
자세한 내용: [01 §3.6 "kind가 Docker API로 만드는 것"](01-environments.md#36-kind-클러스터-생성-control-plane-1--worker-2)

### A-8. docker build로 이미지를 만들었는데 Pod가 `ErrImagePull`이에요
A-7과 같은 이유입니다. `docker build` 결과는 **호스트 dockerd의 이미지 저장소**에 있고, Pod를 띄우는 건 **노드 안의 containerd**입니다. 둘은 저장소가 따로라서 이미지를 노드로 복사해야 합니다.
```bash
kind load docker-image k8s-lab/payment-api:$TAG --name k8s-lab
docker exec k8s-lab-worker crictl images | grep k8s-lab   # 노드에 들어갔는지 확인
```
레지스트리에서 받지 않고 노드에 미리 넣은 이미지를 쓰는 방식이라 `imagePullPolicy: IfNotPresent`가 필수입니다(`Always`면 레지스트리에서 받으려다 실패). 코드를 바꿨다면 **빌드와 load를 다시** 해야 합니다.

---

## B. 어디에 설치되고, 어디서 실행되나

### B-1. 01 §4.1~4.3의 helm 명령은 control-plane에 설치되는 건가요?
**명령은 WSL에서 입력하지만, 단계마다 결과가 남는 곳이 다릅니다.**

| 단계 | 일어나는 곳 | 결과가 남는 곳 |
|---|---|---|
| 4.1 `helm repo add/update` | **WSL 안에서만** (클러스터와 통신 없음) | `~/.config/helm`, `~/.cache/helm` (차트 저장소 즐겨찾기와 목록) |
| 4.2 Traefik 설치 | WSL에서 렌더링한 YAML을 API 서버에 제출 | Pod는 **control-plane 노드** |
| 4.3 metrics-server 설치 | 위와 같음 | Pod는 **worker 노드 중 하나** |

helm은 **클라이언트일 뿐**입니다. 클러스터 쪽에 helm 서버가 없습니다.
1. helm이 차트와 values를 합쳐 WSL에서 YAML을 만듭니다.
2. 그 YAML을 `~/.kube/config`에 적힌 API 서버로 보냅니다.
3. **어느 노드에 띄울지는 scheduler가 정합니다.**

설치 기록(release)은 해당 namespace의 Secret(`sh.helm.release.v1.<이름>.v<번호>`)으로 **클러스터에** 저장됩니다.
```bash
kubectl get pods -A -o wide | grep -E 'traefik|metrics-server'   # NODE 열 확인
kubectl get secret -n traefik -l owner=helm                      # release 기록
```

### B-2. `-n kube-system`으로 설치했으니 control-plane에 뜨는 것 아닌가요?
**아니요. namespace는 논리적인 구분일 뿐, 배치와는 관계없습니다.** 배치는 nodeSelector, nodeAffinity, taint/toleration이 정합니다. metrics-server는 배치 규칙이 없고 control-plane에는 taint가 있으므로 worker에 뜹니다.

### B-3. Pod가 어느 노드에 갈지는 무엇으로 정하나요? nodeAffinity만 있으면 전용 노드가 되나요?
**nodeAffinity만으로는 전용 노드가 되지 않습니다.** 두 규칙은 방향이 반대입니다.

| 규칙 | 누가 정하나 | 의미 | 이 프로젝트 |
|---|---|---|---|
| nodeAffinity / nodeSelector | Pod | "**나는** 저 노드로 간다" (끌어당김) | MySQL·Kafka → `tier=data` |
| taint | 노드 | "**허락받은 Pod만** 받는다" (밀어냄) | control-plane 기본 taint |
| toleration | Pod | "그 taint를 견딜 수 있다" (들어갈 **수** 있음) | Traefik |

전용 노드를 만들려면 **taint + toleration + nodeAffinity**를 함께 씁니다. Traefik이 control-plane에 배치되는 방식이 그 예입니다(`deploy/platform/traefik-values.yaml`).

> ⚠️ 지금은 data 노드에 taint가 없습니다. MySQL과 Kafka는 data 노드로만 가지만, **앱 Pod와 metrics-server도 data 노드에 배치될 수 있습니다.** 격리가 한 방향만 되어 있는 상태입니다.

자세한 내용: [03 스케줄링](03-components.md#스케줄링)

### B-4. helm으로 Traefik, Argo CD 등을 설치했는데 Docker Desktop의 컨테이너 목록은 그대로예요
**정상입니다. 모두 노드 컨테이너 "안"에 설치되고 실행되기 때문입니다.** Docker Desktop(호스트 dockerd)에는 kind가 만든 노드 컨테이너 3개만 보입니다. helm으로 무엇을 설치하든 이 개수는 늘지 않습니다.

| 관점 | 보이는 것 | 확인 명령 |
|---|---|---|
| 호스트 Docker | 노드 컨테이너 3개 | `docker ps --format '{{.Names}}' \| grep k8s-lab` |
| 노드 안의 containerd | Pod 컨테이너들 (argocd, traefik ...) | `docker exec k8s-lab-worker crictl ps` |
| 노드 안의 이미지 저장소 | 노드가 받은 이미지 (`quay.io/argoproj/argocd` 등) | `docker exec k8s-lab-worker crictl images` |
| 호스트 Docker 이미지 | **argocd 이미지 없음** | `docker images \| grep argo` → 결과 없음 |

이미지도 마찬가지입니다. **Pod가 배치된 노드의 containerd가** 레지스트리에서 직접 받습니다. 호스트 Docker는 그 과정에 관여하지 않습니다. 같은 이미지를 쓰는 Pod가 worker와 worker2에 하나씩 뜨면 **노드마다 따로** 받습니다.

> 그래서 정리도 단순합니다. `kind delete cluster`로 노드 컨테이너만 지우면, 그 안에 설치된 Pod, 이미지, PV 데이터가 모두 함께 사라집니다.

### B-5. Argo CD는 control-plane 안에 설치되나요? Jenkins와 비슷한 건가요?
**아니요. Argo CD는 `argocd` namespace에 뜬 보통의 Pod 5개이고, scheduler가 고른 worker 노드에서 실행됩니다.** control-plane에는 taint가 있고 Argo CD values에는 toleration이 없기 때문입니다. Argo CD도 우리가 쓰는 kubectl처럼 **API 서버의 클라이언트**입니다. ServiceAccount와 ClusterRole로 권한을 받아 API를 호출합니다.

| Pod | 종류 | 하는 일 |
|---|---|---|
| application-controller | StatefulSet (`-0`) | Git의 원하는 상태와 클러스터의 실제 상태를 **계속 비교**하고 차이를 apply. selfHeal 담당 |
| repo-server | Deployment | Git clone 후 Helm·Kustomize를 렌더링해 최종 YAML 생성 (apply는 안 함) |
| server | Deployment | 웹 UI와 API (`argocd.localtest.me`) |
| redis | Deployment | 렌더링 결과와 상태 캐시 |
| applicationset-controller | Deployment | 템플릿 하나로 Application 여러 개 생성 |

설치하면 CRD(`Application`, `ApplicationSet`, `AppProject`)도 함께 생깁니다. **Application 리소스를 만들기 전까지 Argo CD는 아무것도 하지 않습니다.** 05 §4에서 `root-app.yaml`을 apply하면 그때부터 동작합니다.

Jenkins와는 **방향이 반대**입니다.

| | Jenkins (push 방식 CI/CD) | Argo CD (pull 방식 GitOps) |
|---|---|---|
| 위치 | 보통 클러스터 밖 | 클러스터 안 |
| 동작 계기 | 웹훅이나 버튼으로 파이프라인 **1회 실행** | **계속 반복** (기본 3분마다 Git과 비교) |
| 배포 | `kubectl apply`를 **밀어넣음** | Git을 **끌어와서** 맞춤 |
| 클러스터 자격 증명 | Jenkins에 kubeconfig 저장 필요 | 불필요 (클러스터 안의 ServiceAccount 사용) |
| 누가 kubectl로 직접 바꾸면 | 다음 배포 전까지 남음 | selfHeal로 Git 상태로 되돌림 |
| 이미지 빌드 | ✅ | ❌ **하지 않음** |

실무에서는 둘을 함께 씁니다. **CI**(Jenkins, GitHub Actions)가 테스트, 이미지 빌드와 push, values의 이미지 태그 커밋까지 하면, **CD**(Argo CD)가 Git 변경을 감지해 클러스터에 반영합니다. 이 프로젝트에서는 CI 역할을 사람이 합니다(Lab 2-1의 build·load와 태그 수정).
```bash
kubectl get pods -n argocd -o wide        # NODE 열: worker / worker2
kubectl get crd | grep argoproj
kubectl get applications -A               # root-app을 apply하기 전에는 비어 있음
```
> Argo CD는 "CRD + 컨트롤러" 패턴, 즉 **Operator**입니다. Deployment 컨트롤러가 replicas 수를 계속 맞추듯, Argo CD는 "Git ↔ 클러스터"를 계속 맞추는 reconcile 루프입니다. reconcile CronJob([D-2](#d-2-reconcile-job은-어떻게-동작하나요))은 불일치를 **감지만** 하고, Argo CD는 **고칩니다.**

---

## C. 확장

### C-1. 확장이 필요하면 app 노드와 data 노드를 물리 서버로 나누고, data 노드도 쪼개면 되나요?
**방향은 맞지만, 두 tier가 확장되는 방식이 다릅니다.**

**공통 원리:** YAML은 하드웨어가 아니라 **라벨**(`tier=data`)을 가리킵니다. 서버를 추가하고 `kubectl label node <서버> tier=data`만 하면 되고, 매니페스트는 바뀌지 않습니다.

**app tier (stateless):** 노드만 늘리면 HPA와 topologySpread가 알아서 퍼뜨립니다. 단, balance-worker는 **Kafka 파티션 수(3)가 상한**입니다. 파티션 하나는 consumer 하나만 읽기 때문입니다.

**data tier (stateful):** **노드를 쪼개도 MySQL이나 Kafka가 늘어나지 않습니다.** 둘 다 `replicas: 1`입니다.

| | 실제로 확장하려면 |
|---|---|
| MySQL | primary + read replica 복제 (Operator), 쓰기 확장은 샤딩(Vitess). 앱도 읽기와 쓰기 연결을 나눠야 함 |
| Kafka | 브로커 증가 + 토픽 replication factor와 파티션 수 증가 (Strimzi) |

data 노드를 나누는 실제 목적은 **장애 격리와 자원 분리**입니다. MySQL 전용과 Kafka 전용 노드를 따로 두거나, podAntiAffinity로 브로커를 서로 다른 노드에 흩어 놓습니다.

> 스토리지 제약: kind의 `local-path` PV는 특정 노드 디스크에 묶여서 Pod가 그 노드를 떠날 수 없습니다. 노드 장애 때 다른 노드로 옮기려면 네트워크 스토리지(Ceph, Longhorn, EBS)가 필요합니다. 실무에서는 DB를 관리형 서비스(RDS 등)로 빼는 경우도 많고, 그때는 Helm values의 `mysql.host`만 바꾸면 됩니다.

---

## D. 데이터와 메시징

### D-1. Kustomize는 무엇인가요? Helm과 뭐가 다른가요?
**템플릿 없이 YAML을 조합하고 덮어써서 환경별 매니페스트를 만드는 도구**입니다. kubectl에 내장되어 있습니다(`kubectl apply -k`).
- **base:** 공통 원본 YAML. 그 자체로 apply할 수 있습니다.
- **overlay(dev/prod):** base 위에 **차이점만** 덮어씁니다. namespace, 라벨, 이미지 태그, patch를 지정합니다.

| | Helm | Kustomize |
|---|---|---|
| 방식 | `{{ .Values.x }}` **템플릿**에 값 주입 | 완성된 YAML 위에 **patch** |
| 이 프로젝트 | 직접 만든 앱, 서드파티 플랫폼 | 구조가 고정된 데이터 계층 (MySQL, Kafka) |

`configMapGenerator`는 ConfigMap 이름에 **내용 해시**를 붙입니다. 그래서 파일이 바뀌면 참조하는 Pod가 자동으로 재시작됩니다.
```bash
kubectl kustomize deploy/kustomize/data/overlays/prod   # 최종 YAML 미리보기 (apply 안 함)
```
자세한 내용: [05 §2 Kustomize](05-tooling.md#2-kustomize)

### D-2. reconcile Job은 어떻게 동작하나요?
**잔고와 원장이 맞는지 5분마다 점검하는 감사용 CronJob**입니다(`deploy/kustomize/data/base/jobs/balance-reconcile.yaml`).
```
accounts.balance == initial_balance + SUM(payment_ledger.delta WHERE status='COMPLETED')
```
- `reconcile.sql`은 어긋난 행만 돌려줍니다. 결과가 0행이면 정상(exit 0), 있으면 `MISMATCH`를 출력하고 exit 1로 끝나 Job이 Failed가 됩니다.
- **Job 실패 자체가 알람 신호입니다.** `kube_job_status_failed` 지표로 경보를 걸 수 있습니다.
- `concurrencyPolicy: Forbid`: 이전 실행이 아직 끝나지 않았으면 이번 회차를 건너뜁니다.
- `backoffLimit: 1`: 한 번 재시도한 뒤 실패로 확정합니다. 일시적인 DB 끊김과 실제 불일치를 구분하기 위한 설정입니다.
- prod overlay는 JSON 6902 patch로 주기만 1분으로 바꿉니다.
```bash
kubectl create job -n data-dev --from=cronjob/balance-reconcile reconcile-manual
kubectl logs -n data-dev job/reconcile-manual
```

### D-3. reconcile이 도는 순간 worker가 결제를 처리 중이면 불일치로 잡히지 않나요?
**잡히지 않습니다.** 세 가지가 함께 갖춰져 있기 때문입니다.
1. **쓰는 쪽이 한 트랜잭션입니다.** worker는 잔고 UPDATE와 원장 INSERT를 `@Transactional` 하나로 묶습니다. 다른 세션에는 두 변경이 커밋 순간에 함께 보이거나 둘 다 안 보입니다.
2. **읽는 쪽은 스냅샷을 봅니다.** InnoDB의 MVCC 덕분에 SELECT 한 문장은 시작 시점의 스냅샷만 읽습니다. 커밋되지 않은 변경은 undo log의 이전 버전으로 대신 읽고, 잠금도 잡지 않습니다.
3. **쿼리가 JOIN 한 문장입니다.** 잔고와 원장 합계를 **두 문장으로 나눠** 읽으면, 그 사이에 커밋이 끼어들어 정상인데도 경보가 납니다.

| 처리 도중에 읽는 방식 (실측) | 결과 |
|---|---|
| 기본 REPEATABLE READ, 한 문장 | ✅ OK |
| READ UNCOMMITTED | ❌ MISMATCH |
| 두 문장으로 나눠 읽음 | ❌ 값이 다름 |

> 여러 문장이 꼭 필요하면 `START TRANSACTION WITH CONSISTENT SNAPSHOT;`으로 묶으면 됩니다. 남은 예외는 dev에서 worker가 재기동하는 몇 초뿐입니다. `ddl-auto: create`가 테이블을 지우고 다시 만드는 동안에는 `Table doesn't exist`로 실패할 수 있습니다.

### D-4. Kafka consumer는 offset을 어디에 기록하나요? 재기동하면 메모리에서 사라지지 않나요?
**offset은 consumer가 아니라 Kafka 브로커의 내부 토픽 `__consumer_offsets`에 저장됩니다.** 파일로 남길 필요가 없습니다.

| | 위치 | 재기동 후 |
|---|---|---|
| position (다음에 가져올 offset) | consumer 메모리 | 사라짐 |
| **committed offset** (여기까지 처리 완료) | 브로커 `__consumer_offsets` (compact 토픽) | **유지** |

- **pull 방식:** listener container 스레드가 `poll()`로 레코드를 가져와 `@KafkaListener` 메서드에 넘깁니다.
- **커밋 시점:** 주기적 자동 커밋(`enable.auto.commit`)은 Spring이 기본으로 끕니다. 이 프로젝트는 `ack-mode: record`라 **레코드 하나를 처리할 때마다** 커밋합니다.
- **재기동하면:** 같은 `group.id`로 그룹에 참가해 파티션을 배정받고, 브로커에서 committed offset을 조회해 그 지점부터 이어서 읽습니다. 커밋 기록이 없는 새 그룹만 `auto-offset-reset`을 따릅니다(worker는 `earliest`).
- **처리는 끝났는데 커밋 전에 죽으면** 같은 메시지를 다시 받습니다(at-least-once). 그래서 worker는 `payment_id` UNIQUE로 멱등하게 처리합니다([04 장애 시나리오](04-workflows.md)).
- payment-api는 group id가 `payment-api-${POD_NAME}`이라 Pod마다 새 그룹이 됩니다. 그래서 재기동하면 `latest`(기본값)부터, 즉 재기동 이후 이벤트만 받습니다.
```bash
kubectl exec -n data-dev kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group balance-worker   # CURRENT-OFFSET, LAG
```
> dev는 worker가 재기동할 때마다 DB 테이블을 다시 만들지만(`ddl-auto: create`), **Kafka offset은 이어집니다.** 그래서 DB만 초기화되고 옛 메시지는 다시 처리되지 않습니다. 처음부터 다시 처리하려면 `--reset-offsets --to-earliest`를 실행하세요.

### D-5. JPA id를 IDENTITY로 생성하는데, SEQUENCE와 뭐가 다른가요?
| | IDENTITY | SEQUENCE |
|---|---|---|
| id를 정하는 시점 | INSERT할 때 DB가 정함 (`AUTO_INCREMENT`) | INSERT 전에 시퀀스에서 받아 옴 |
| `persist()` | **즉시 INSERT** | flush나 커밋 때 INSERT (쓰기 지연) |
| JDBC batch insert | 불가 | 가능 |
| `allocationSize` | 해당 없음 | 한 번 조회로 id N개를 미리 확보 |
| MySQL | **원래 쓰라고 만든 방식** | 네이티브 시퀀스가 없음 → Hibernate가 `*_seq` **테이블로 흉내 냄** (추가 쿼리와 행 락) |

MySQL에서는 IDENTITY가 맞습니다. SEQUENCE의 장점은 진짜 시퀀스가 있는 DB(PostgreSQL, Oracle)에서만 의미가 있고, 이 worker는 결제 하나당 한 행만 INSERT해서 batch로 얻을 것도 없습니다.
> 롤백이나 `INSERT IGNORE`로 버려진 번호는 되돌아오지 않아서 id 사이에 **빈 번호가 생깁니다.** id는 정렬 용도로만 쓰고, 개수를 세거나 정합성을 따지는 데 쓰면 안 됩니다.

### D-6. 스키마는 누가 만들고, 언제 초기화되나요?
- **balance-worker의 JPA 엔티티**(`Account`, `PaymentLedger`)가 스키마를 소유합니다. worker는 쓰기 권한이 있는 계정으로 접속합니다.
- `spring.jpa.hibernate.ddl-auto`는 Helm `balanceWorker.ddlAuto`로 정합니다.
  - dev `create`: Pod가 뜰 때마다 DROP 후 재생성합니다. **데이터가 초기화됩니다.**
  - prod `update`: replica 3개가 서로의 데이터를 지우지 않도록 테이블을 유지합니다.
- 초기 계좌는 `data.sql`이 넣습니다(`defer-datasource-initialization`으로 DDL 이후에 실행).
- payment-api는 `ddl-auto: none`입니다. `api_ro` 계정은 SELECT 권한뿐이라 DDL을 실행할 수도 없습니다.
- MySQL init 스크립트(`02-readonly-user.sh`)는 **데이터 디렉터리가 비어 있을 때만** 실행됩니다. PVC나 볼륨이 남아 있으면 다시 실행되지 않습니다. compose에서는 `docker compose down -v`, kind에서는 PVC 삭제가 필요합니다.
