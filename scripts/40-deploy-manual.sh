#!/usr/bin/env bash
# GitOps 이전 단계: kubectl + helm 으로 직접 배포한다. (각 단계를 눈으로 확인하며 학습)
# ArgoCD로 관리 중인 환경에서는 쓰지 마세요 (selfHeal이 수동 변경을 되돌린다).
source "$(dirname "$0")/lib.sh"
need kubectl; need helm

TAG="$(image_tag)"
MONITORING_ENABLED="false"
kubectl get crd servicemonitors.monitoring.coreos.com > /dev/null 2>&1 && MONITORING_ENABLED="true"

log "1) 데이터 계층 (Kustomize): ${DATA_NS}"
kubectl apply -k "${ROOT_DIR}/deploy/kustomize/data/overlays/${ENV_NAME}"

log "   StatefulSet 준비 대기"
kubectl rollout status statefulset/mysql -n "${DATA_NS}" --timeout=300s
kubectl rollout status statefulset/kafka -n "${DATA_NS}" --timeout=300s
kubectl wait --for=condition=complete job/kafka-topic-init -n "${DATA_NS}" --timeout=300s

log "2) 앱 네임스페이스 생성 + 라벨 (NetworkPolicy가 이 라벨로 접근을 허용한다)"
kubectl create namespace "${APP_NS}" --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace "${APP_NS}" k8slab.dev/role=app --overwrite

log "3) 애플리케이션 (Helm): ${APP_NS}, tag=${TAG}"
helm upgrade --install payment-apps "${ROOT_DIR}/deploy/helm/payment-apps" \
  -n "${APP_NS}" \
  -f "${ROOT_DIR}/deploy/helm/payment-apps/values-${ENV_NAME}.yaml" \
  --set global.imageTag="${TAG}" \
  --set serviceMonitor.enabled="${MONITORING_ENABLED}" \
  --wait --timeout 10m

if [[ "${MONITORING_ENABLED}" == "true" ]]; then
  log "4) Grafana 대시보드"
  kubectl apply -k "${ROOT_DIR}/deploy/monitoring"
fi

log "완료"
kubectl get pods -n "${DATA_NS}" -o wide
kubectl get pods -n "${APP_NS}" -o wide
echo
echo "  브라우저: http://payment.localtest.me"
