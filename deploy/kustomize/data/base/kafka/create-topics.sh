#!/bin/bash
# Kafka 토픽 생성 스크립트. K8s Job(kafka-topic-init)과 docker compose에서 같이 쓴다.
# --if-not-exists 이므로 여러 번 실행해도 안전하다 (멱등).
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
PARTITIONS="${PARTITIONS:-3}"
REPLICATION="${REPLICATION_FACTOR:-1}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

echo "waiting for kafka at ${BOOTSTRAP} ..."
until "${KAFKA_BIN}/kafka-broker-api-versions.sh" --bootstrap-server "${BOOTSTRAP}" > /dev/null 2>&1; do
  sleep 3
done

for topic in payment.requested payment.result payment.requested.dlt; do
  "${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION}"
done

"${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --describe
echo "topics ready"
