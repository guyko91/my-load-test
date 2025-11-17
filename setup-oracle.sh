#!/bin/bash

# Oracle DB 사용자 설정 스크립트

echo "=== Oracle DB User Setup ==="

# Oracle DB 컨테이너가 실행 중인지 확인
if ! docker ps | grep -q load-test-oracle; then
    echo "❌ Oracle DB container is not running"
    echo "Run: docker compose up -d oracle-db"
    exit 1
fi

echo "✅ Oracle DB container is running"
echo "Creating testuser..."

MAX_RETRIES=6
RETRY_INTERVAL=10
RETRY_COUNT=0
SUCCESS=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    echo "Attempting to connect to Oracle DB (Attempt: $((RETRY_COUNT + 1))/$MAX_RETRIES)..."

    # SQL 명령어 실행
    docker exec -i load-test-oracle sqlplus -s system/OraclePassword123@//localhost:1521/XEPDB1 <<EOF
ALTER SESSION SET CONTAINER = XEPDB1;

-- Drop user if exists
BEGIN
   EXECUTE IMMEDIATE 'DROP USER testuser CASCADE';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -1918 THEN
         NULL;
      END IF;
END;
/

-- Create user
CREATE USER testuser IDENTIFIED BY testpass DEFAULT TABLESPACE USERS;
GRANT CONNECT, RESOURCE, DBA TO testuser;
GRANT CREATE SESSION TO testuser;
GRANT CREATE TABLE TO testuser;
GRANT CREATE SEQUENCE TO testuser;
GRANT UNLIMITED TABLESPACE TO testuser;

-- Create sequence
CREATE SEQUENCE testuser.ORDER_SEQ START WITH 1 INCREMENT BY 1;

COMMIT;

-- Verify
SELECT username FROM dba_users WHERE username = 'TESTUSER';

EXIT;
EOF

    if [ $? -eq 0 ]; then
        SUCCESS=true
        break
    else
        echo "Connection failed. Retrying in $RETRY_INTERVAL seconds..."
        RETRY_COUNT=$((RETRY_COUNT + 1))
        sleep $RETRY_INTERVAL
    fi
done

if [ "$SUCCESS" = "true" ]; then
    echo "✅ testuser created successfully!"
    echo ""
    echo "Now you can start the app:"
    echo "  docker compose up -d app"
else
    echo "❌ Failed to create user after $MAX_RETRIES attempts."
    exit 1
fi
