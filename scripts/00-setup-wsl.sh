#!/usr/bin/env bash
# WSL2 Ubuntu에 kubectl, kind, helm, k9s 를 설치한다. (Ubuntu 서버에서도 그대로 동작)
# Docker는 Docker Desktop의 WSL Integration을 켜서 사용한다 → docs/01-environments.md
source "$(dirname "$0")/lib.sh"

KIND_VERSION="${KIND_VERSION:-v0.33.0}"
HELM_VERSION="${HELM_VERSION:-v4.3.0}"
ARCH="$(dpkg --print-architecture)"   # amd64 | arm64
BIN_DIR="/usr/local/bin"

log "필수 패키지 설치"
sudo apt-get update -qq
sudo apt-get install -y -qq curl ca-certificates tar git jq > /dev/null

log "Docker 연결 확인"
if ! docker info > /dev/null 2>&1; then
  die "docker에 연결할 수 없습니다. Docker Desktop > Settings > Resources > WSL Integration 에서 이 배포판을 켜세요."
fi

if ! command -v kubectl > /dev/null; then
  log "kubectl 설치 (최신 stable)"
  KUBECTL_VERSION="$(curl -fsSL https://dl.k8s.io/release/stable.txt)"
  curl -fsSLo /tmp/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${ARCH}/kubectl"
  sudo install -m 0755 /tmp/kubectl "${BIN_DIR}/kubectl"
fi

if ! command -v kind > /dev/null; then
  log "kind ${KIND_VERSION} 설치"
  curl -fsSLo /tmp/kind "https://kind.sigs.k8s.io/dl/${KIND_VERSION}/kind-linux-${ARCH}"
  sudo install -m 0755 /tmp/kind "${BIN_DIR}/kind"
fi

if ! command -v helm > /dev/null; then
  log "helm ${HELM_VERSION} 설치"
  curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-${ARCH}.tar.gz" | tar -xz -C /tmp
  sudo install -m 0755 "/tmp/linux-${ARCH}/helm" "${BIN_DIR}/helm"
fi

if ! command -v k9s > /dev/null; then
  log "k9s 설치 (터미널 UI, 선택)"
  curl -fsSL "https://github.com/derailed/k9s/releases/latest/download/k9s_Linux_${ARCH}.tar.gz" | tar -xz -C /tmp k9s
  sudo install -m 0755 /tmp/k9s "${BIN_DIR}/k9s"
fi

log "kubectl 자동완성/별칭 설정 (~/.bashrc)"
if ! grep -q 'k8s-lab aliases' ~/.bashrc; then
  cat >> ~/.bashrc <<'EOF'
# k8s-lab aliases
source <(kubectl completion bash)
alias k=kubectl
complete -o default -F __start_kubectl k
EOF
fi

log "설치 완료"
kubectl version --client
kind version
helm version --short
