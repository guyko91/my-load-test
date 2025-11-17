-- Create user and grant privileges
ALTER SESSION SET CONTAINER = XEPDB1;

CREATE USER testuser IDENTIFIED BY testpass;
GRANT CONNECT, RESOURCE TO testuser;
GRANT CREATE SESSION TO testuser;
GRANT CREATE TABLE TO testuser;
GRANT CREATE SEQUENCE TO testuser;
GRANT UNLIMITED TABLESPACE TO testuser;

-- Connect as testuser and create sequence
ALTER SESSION SET CURRENT_SCHEMA = testuser;

-- Create sequence for Order table
CREATE SEQUENCE ORDER_SEQ START WITH 1 INCREMENT BY 1;

EXIT;
