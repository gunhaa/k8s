#!/bin/bash
# payment-api 전용 read-only 계정 생성.
# 비밀번호를 SQL 파일(ConfigMap)에 적지 않고 Secret에서 주입된 환경변수로 받는다.
# docker_process_sql 은 mysql 공식 이미지 entrypoint가 제공하는 함수다 (.sh 파일은 source 되어 실행됨).
set -euo pipefail

: "${API_RO_PASSWORD:?API_RO_PASSWORD is required}"

docker_process_sql <<-EOSQL
	CREATE USER IF NOT EXISTS 'api_ro'@'%' IDENTIFIED BY '${API_RO_PASSWORD}';
	GRANT SELECT ON \`${MYSQL_DATABASE}\`.accounts TO 'api_ro'@'%';
	FLUSH PRIVILEGES;
EOSQL
