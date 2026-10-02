#!/usr/bin/env bash
# 앱 이미지를 빌드하고 kind 노드에 직접 적재한다 (레지스트리 없이).
#   TAG=v1 ./scripts/30-build-load.sh          # 태그 지정
#   ./scripts/30-build-load.sh payment-api     # 특정 앱만
source "$(dirname "$0")/lib.sh"
need docker; need kind

TAG="$(image_tag)"
APPS=("$@")
[[ ${#APPS[@]} -eq 0 ]] && APPS=(frontend payment-api balance-worker)

for app in "${APPS[@]}"; do
  image="k8s-lab/${app}:${TAG}"
  log "build ${image}"
  docker build -t "${image}" "${ROOT_DIR}/apps/${app}"

  # kind 노드(컨테이너) 안의 containerd 이미지 저장소로 복사한다.
  # 그래서 imagePullPolicy: IfNotPresent 이면 레지스트리 없이 바로 쓸 수 있다.
  log "kind load ${image}"
  kind load docker-image "${image}" --name "${CLUSTER_NAME}"
done

log "완료. 이미지 태그: ${TAG}"
echo "  수동 배포 : TAG=${TAG} ./scripts/40-deploy-manual.sh"
echo "  GitOps    : deploy/helm/payment-apps/values-${ENV_NAME}.yaml 의 global.imageTag 를 ${TAG} 로 바꿔 커밋/푸시"
