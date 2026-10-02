{{/*
공통 라벨. component 이름을 인자로 받는다.
  {{ include "payment-apps.labels" (dict "root" . "name" "payment-api") }}
*/}}
{{- define "payment-apps.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: payment-platform
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/version: {{ .root.Values.global.imageTag | quote }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
k8slab.dev/env: {{ .root.Values.global.env }}
{{- end }}

{{/* selector 라벨: 변하지 않는 값만 넣는다 (Deployment selector는 수정 불가) */}}
{{- define "payment-apps.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{/* 이미지 경로: registry + repository + tag */}}
{{- define "payment-apps.image" -}}
{{- printf "%s%s:%s" .root.Values.global.imageRegistry .image .root.Values.global.imageTag -}}
{{- end }}

{{/* 의존 서비스 포트가 열릴 때까지 기다리는 initContainer */}}
{{- define "payment-apps.waitFor" -}}
- name: wait-for-{{ .label }}
  image: {{ .root.Values.waitImage }}
  command: ["sh", "-c", "until nc -z -w 2 {{ .host }} {{ .port }}; do echo waiting for {{ .label }}; sleep 2; done"]
  resources:
    requests: { cpu: 5m, memory: 8Mi }
    limits: { memory: 16Mi }
  securityContext:
    runAsNonRoot: true
    runAsUser: 65534
    allowPrivilegeEscalation: false
{{- end }}

{{/* Spring Boot 컨테이너 공통 securityContext */}}
{{- define "payment-apps.containerSecurity" -}}
securityContext:
  runAsNonRoot: true
  runAsUser: 1001
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
{{- end }}

{{/* Spring Boot Actuator 기반 probe 3종 */}}
{{- define "payment-apps.springProbes" -}}
# startupProbe가 성공할 때까지 liveness/readiness는 동작하지 않는다 → 느린 JVM 기동 보호
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 5
  failureThreshold: 30
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 10
  failureThreshold: 3
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: http }
  periodSeconds: 5
  failureThreshold: 3
{{- end }}
