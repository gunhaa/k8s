// 기본값. K8s에서는 Helm 차트의 ConfigMap이 이 파일을 덮어쓴다 (환경별 설정 주입 예시).
window.APP_CONFIG = {
  env: "local",
  pollIntervalMs: 1000
};
