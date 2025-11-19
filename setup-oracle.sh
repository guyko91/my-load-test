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

-- Create types for data generation
CREATE OR REPLACE TYPE testuser.t_varchar_array_15 AS VARRAY(15) OF VARCHAR2(255);
/
CREATE OR REPLACE TYPE testuser.t_varchar_array_5 AS VARRAY(5) OF VARCHAR2(255);
/

-- Create Order table
CREATE TABLE testuser.ORDERS (
    ID NUMBER(19,0) NOT NULL,
    ORDER_NUMBER VARCHAR2(50) NOT NULL,
    CUSTOMER_NAME VARCHAR2(100) NOT NULL,
    PRODUCT_NAME VARCHAR2(200) NOT NULL,
    QUANTITY NUMBER(10,0) NOT NULL,
    UNIT_PRICE NUMBER(10,2) NOT NULL,
    TOTAL_PRICE NUMBER(12,2) NOT NULL,
    ORDER_DATE TIMESTAMP NOT NULL,
    STATUS VARCHAR2(20) NOT NULL,
    SHIPPING_ADDRESS VARCHAR2(500),
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (ID),
    UNIQUE (ORDER_NUMBER)
);
CREATE INDEX testuser.idx_order_status ON testuser.ORDERS (STATUS);
CREATE INDEX testuser.idx_order_date ON testuser.ORDERS (ORDER_DATE);

COMMIT;

-- Procedure to generate dummy data
CREATE OR REPLACE PROCEDURE testuser.GENERATE_DUMMY_DATA
AS
    v_count NUMBER;
    v_customer_names testuser.t_varchar_array_15;
    v_product_names testuser.t_varchar_array_15;
    v_statuses testuser.t_varchar_array_5;
    v_addresses testuser.t_varchar_array_5;
BEGIN
    -- Check if table is empty
    SELECT COUNT(*) INTO v_count FROM testuser.ORDERS;
    IF v_count > 0 THEN
        RETURN;
    END IF;

    -- Initialize arrays
    v_customer_names := testuser.t_varchar_array_15('김철수', '이영희', '박민수', '최지원', '정현우', '강서연', '윤태영', '임수진', '한지훈', '오민지', '서준호', '권나영', '송재현', '안혜진', '장동민');
    v_product_names := testuser.t_varchar_array_15('노트북', '스마트폰', '태블릿', '모니터', '키보드', '마우스', '헤드셋', '웹캠', 'SSD', 'RAM', '그래픽카드', '파워서플라이', '케이스', '쿨러', '메인보드');
    v_statuses := testuser.t_varchar_array_5('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED');
    v_addresses := testuser.t_varchar_array_5('서울특별시 강남구 테헤란로 123', '서울특별시 송파구 올림픽로 456', '경기도 성남시 분당구 정자일로 789', '서울특별시 마포구 월드컵북로 321', '인천광역시 연수구 센트럴로 654');

    FOR i IN 1..100000 LOOP
        DECLARE
            v_quantity NUMBER;
            v_unit_price NUMBER(10,2);
        BEGIN
            v_quantity := TRUNC(DBMS_RANDOM.VALUE(1, 11));
            v_unit_price := TRUNC(DBMS_RANDOM.VALUE(10000, 1000000), 2);

            INSERT INTO testuser.ORDERS (
                ID, ORDER_NUMBER, CUSTOMER_NAME, PRODUCT_NAME, QUANTITY,
                UNIT_PRICE, TOTAL_PRICE, ORDER_DATE, STATUS, SHIPPING_ADDRESS
            ) VALUES (
                testuser.ORDER_SEQ.NEXTVAL,
                'ORD-' || DBMS_RANDOM.STRING('X', 8),
                v_customer_names(TRUNC(DBMS_RANDOM.VALUE(1, v_customer_names.COUNT + 1))),
                v_product_names(TRUNC(DBMS_RANDOM.VALUE(1, v_product_names.COUNT + 1))),
                v_quantity,
                v_unit_price,
                v_quantity * v_unit_price,
                SYSTIMESTAMP - (DBMS_RANDOM.VALUE(0, 365) * INTERVAL '1' DAY),
                v_statuses(TRUNC(DBMS_RANDOM.VALUE(1, v_statuses.COUNT + 1))),
                v_addresses(TRUNC(DBMS_RANDOM.VALUE(1, v_addresses.COUNT + 1)))
            );
        END;

        IF MOD(i, 1000) = 0 THEN
            COMMIT;
        END IF;
    END LOOP;
    COMMIT;
END;
/

-- Execute the procedure
BEGIN
    testuser.GENERATE_DUMMY_DATA;
END;
/

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
