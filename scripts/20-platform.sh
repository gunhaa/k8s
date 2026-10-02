#!/usr/bin/env bash
# 클러스터 공용 플랫폼 컴포넌트를 Helm으로 설치한다.
#   MONITORING=false ./scripts/20-platform.sh   # Prometheus/Grafana 생략 (메모리 약 1.5GB 절약)
#   ARGOCD=false     ./scripts/20-platform.sh   # ArgoCD 생략
#   TRAEFIK=false METRICS_SERVER=false ./scripts/20-platform.sh   # k3s처럼 이미 내장된 경우
source "$(dirname "$0")/lib.sh"
need helm; need kubectl

MONITORING="${MONITORING:-true}"
ARGOCD="${ARGOCD:-true}"
TRAEFIK="${TRAEFIK:-true}"
METRICS_SERVER="${METRICS_SERVER:-true}"
P="${ROOT_DIR}/deploy/platform"

log "Helm 저장소 등록"
helm repo add traefik https://traefik.github.io/charts > /dev/null
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ > /dev/null
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts > /dev/null
helm repo add argo https://argoproj.github.io/argo-helm > /dev/null
helm repo update > /dev/null

if [[ "${TRAEFIK}" == "true" ]]; then
  log "Traefik (Ingress Controller)"
  helm upgrade --install traefik traefik/traefik \
    -n traefik --create-namespace -f "${P}/traefik-values.yaml" --wait
fi

if [[ "${METRICS_SERVER}" == "true" ]]; then
  log "metrics-server (kubectl top, HPA)"
  helm upgrade --install metrics-server metrics-server/metrics-server \
    -n kube-system -f "${P}/metrics-server-values.yaml" --wait
fi

if [[ "${MONITORING}" == "true" ]]; then
  log "kube-prometheus-stack (Prometheus Operator, Prometheus, Grafana, node-exporter)"
  helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
    -n monitoring --create-namespace -f "${P}/kube-prometheus-stack-values.yaml" --wait --timeout 10m
fi

if [[ "${ARGOCD}" == "true" ]]; then
  log "Argo CD"
  helm upgrade --install argocd argo/argo-cd \
    -n argocd --create-namespace -f "${P}/argocd-values.yaml" --wait --timeout 10m
fi

log "완료"
helm list -A
echo
[[ "${MONITORING}" == "true" ]] && echo "  Grafana : http://grafana.localtest.me  (admin / admin)"
if [[ "${ARGOCD}" == "true" ]]; then
  echo "  ArgoCD  : http://argocd.localtest.me  (admin / 아래 비밀번호)"
  echo "            $(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)"
fi
