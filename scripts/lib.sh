#!/usr/bin/env bash
# 공통 변수와 헬퍼. 다른 스크립트에서 source 한다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER_NAME="${CLUSTER_NAME:-k8s-lab}"
ENV_NAME="${ENV_NAME:-dev}"
DATA_NS="data-${ENV_NAME}"
APP_NS="payment-${ENV_NAME}"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

need() {
  command -v "$1" > /dev/null 2>&1 || die "'$1' 명령이 없습니다. scripts/00-setup-wsl.sh 를 먼저 실행하세요."
}

# 이미지 태그: git short sha (커밋 전이면 dev)
image_tag() {
  if [[ -n "${TAG:-}" ]]; then
    echo "${TAG}"
  elif git -C "${ROOT_DIR}" rev-parse --short HEAD > /dev/null 2>&1; then
    git -C "${ROOT_DIR}" rev-parse --short HEAD
  else
    echo "dev"
  fi
}
