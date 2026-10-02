#!/bin/bash
# payment-api 전용 read-only 계정 생성.
# 비밀번호를 SQL 파일(ConfigMap)에 적지 않고 Secret에서 주입된 환경변수로 받는다.
#
# mysql entrypoint는 .sh 파일에 실행 권한이 있으면 subprocess로 실행하고, 없으면 source 한다.
# K8s ConfigMap(0644)에서는 source 되지만, Windows bind mount(docker compose)는 실행 권한이 붙어
# subprocess로 실행되어 entrypoint 함수(docker_process_sql)를 쓸 수 없다.
# → 두 경우 모두 동작하도록 mysql 클라이언트로 init 임시 서버(소켓 전용)에 직접 접속한다.
#
# 테이블은 여기서 만들지 않는다 — balance-worker(JPA, ddl-auto)가 기동 시 생성한다.
# MySQL은 존재하지 않는 테이블에 SELECT만 GRANT할 수 없으므로 DB 단위로 SELECT를 준다.
set -euo pipefail

: "${API_RO_PASSWORD:?API_RO_PASSWORD is required}"

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<-EOSQL
	CREATE USER IF NOT EXISTS 'api_ro'@'%' IDENTIFIED BY '${API_RO_PASSWORD}';
	GRANT SELECT ON \`${MYSQL_DATABASE}\`.* TO 'api_ro'@'%';
	FLUSH PRIVILEGES;
EOSQL
