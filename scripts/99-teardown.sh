#!/usr/bin/env bash
# 클러스터 전체 삭제. kind 노드 컨테이너와 그 안의 PV 데이터가 모두 사라진다.
source "$(dirname "$0")/lib.sh"
need kind

log "kind 클러스터 삭제: ${CLUSTER_NAME}"
kind delete cluster --name "${CLUSTER_NAME}"
