#!/usr/bin/env bash
# ArgoCD에 root Application(App of Apps)을 등록한다. 이후 배포는 git push로 한다.
#   REPO_URL=https://github.com/<you>/k8s-lab.git ./scripts/50-gitops.sh
# 비공개 저장소라면 먼저 ArgoCD에 저장소 자격증명을 등록해야 한다 (docs/05-tooling.md).
source "$(dirname "$0")/lib.sh"
need kubectl

REPO_URL="${REPO_URL:-$(git -C "${ROOT_DIR}" remote get-url origin 2>/dev/null || true)}"
TARGET_REVISION="${TARGET_REVISION:-$(git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)}"
[[ -z "${REPO_URL}" ]] && die "REPO_URL 을 지정하거나 git remote origin 을 설정하세요."
[[ "${REPO_URL}" == git@* ]] && warn "SSH URL은 ArgoCD에 SSH 키 등록이 필요합니다. HTTPS URL을 권장합니다."

MONITORING_ENABLED="false"
kubectl get crd servicemonitors.monitoring.coreos.com > /dev/null 2>&1 && MONITORING_ENABLED="true"

if helm status payment-apps -n "${APP_NS}" > /dev/null 2>&1; then
  warn "수동으로 설치한 helm 릴리스(payment-apps)가 있습니다. ArgoCD와 충돌하지 않도록 제거합니다."
  helm uninstall payment-apps -n "${APP_NS}"
fi

log "root Application 등록: ${REPO_URL} @ ${TARGET_REVISION} (monitoring=${MONITORING_ENABLED})"
sed -e "s#REPO_URL#${REPO_URL}#g" \
    -e "s#TARGET_REVISION#${TARGET_REVISION}#g" \
    -e "s#MONITORING_ENABLED#${MONITORING_ENABLED}#g" \
    "${ROOT_DIR}/deploy/argocd/root-app.yaml" | kubectl apply -f -

log "Application 목록 (잠시 후 Synced/Healthy가 되어야 한다)"
sleep 5
kubectl get applications -n argocd
echo
echo "  ArgoCD UI: http://argocd.localtest.me"
echo "  watch    : kubectl get applications -n argocd -w"
