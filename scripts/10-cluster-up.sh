#!/usr/bin/env bash
# kind 멀티노드 클러스터 생성
source "$(dirname "$0")/lib.sh"
need kind; need kubectl

if kind get clusters | grep -qx "${CLUSTER_NAME}"; then
  warn "클러스터 '${CLUSTER_NAME}' 가 이미 있습니다. 다시 만들려면 scripts/99-teardown.sh 실행"
else
  log "kind 클러스터 생성: ${CLUSTER_NAME}"
  kind create cluster --config "${ROOT_DIR}/deploy/kind/kind-config.yaml" --wait 120s
fi

kubectl config use-context "kind-${CLUSTER_NAME}" > /dev/null

log "노드 상태"
kubectl get nodes -L tier -o wide

log "kube-system 의 DaemonSet (노드마다 1개씩 뜨는 Pod)"
kubectl get daemonsets -n kube-system
