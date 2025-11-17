# 🚀 Quick Start Guide

## 📌 로컬 개발 환경 (추천: IntelliJ + Docker)

App은 IntelliJ에서 직접 실행하고, Oracle DB와 K6만 Docker로 구동하는 방식입니다.
**장점**: 코드 수정 시 이미지 재빌드 불필요, 빠른 개발 속도

**K6 Base URL**: `http://host.docker.internal:28080` (기본값 - application.properties)

### 필수 요구사항

- ✅ Docker Desktop for Mac (Apple Silicon 지원)
- ✅ Java 21 (IntelliJ)
- ✅ 최소 8GB RAM (권장: 16GB)

## 1단계: Oracle DB 시작

```bash
# Oracle DB만 먼저 시작
docker compose up -d oracle-db

# 로그 확인 (2-3분 대기)
docker compose logs -f oracle-db
```

**"DATABASE IS READY TO USE"** 메시지가 나올 때까지 기다리세요 (약 2-3분)!
메시지가 나오면 `Ctrl+C`로 로그를 중지하세요.

## 2단계: Oracle 사용자 설정

```bash
# Oracle 사용자 및 권한 설정 스크립트 실행
./setup-oracle.sh
```

성공 메시지:
```
✅ testuser created successfully!
```

## 3단계: IntelliJ에서 Spring Boot 실행

1. IntelliJ에서 프로젝트 열기
2. `src/main/java/com/dw/idstrust/loadtesttoy/LoadTestToyApplication.java` 우클릭
3. "Run 'LoadTestToyApplication'" 클릭

콘솔 로그에서 확인:
```
Generated 1000 dummy orders for testing
Started LoadTestToyApplication in X seconds
```

## 4단계: 대시보드 접속

브라우저에서 아래 주소로 접속:

```
http://localhost:28080/
```

### 대시보드에서 할 수 있는 것:

1. **K6 Load Test 카드**
   - 시나리오 선택: realistic, cpu, db, mixed, high_burst
   - RPS, Duration, VUs 설정
   - "Start K6 Test" 버튼 클릭

2. **Quick Tests (단시간)**
   - 🔥 Quick CPU Test: 20 RPS, 2분, CPU 70%
   - 💾 Quick DB Test: 15 RPS, 3분, DB 조회
   - ⚙️ Realistic Load: 10 RPS, 5분, 혼합
   - ⚡ High Burst: 30 RPS, 3분, 고부하

3. **Long Running Scenarios (장시간)**
   - 📅 Daily Pattern: 8시간 - 일반 하루 패턴
   - 📈 Gradual Increase: 4시간 - 점진적 부하 증가
   - ⚡ Spike Pattern: 3시간 - 급격한 트래픽 스파이크
   - 🛒 Black Friday: 6시간 - 대규모 이벤트 패턴
   - 🌙 Night Batch: 2시간 - 야간 배치 작업
   - 💪 Stress Test: 2시간 - 최대 부하 스트레스

4. **상태 모니터링**
   - K6 실행 상태 (실시간)
   - DB 주문 건수
   - 워크로드 실행 상태

## 5단계: K6 테스트 실행

대시보드에서 "Quick CPU Test" 버튼을 클릭하면 자동으로 K6 컨테이너가 실행됩니다.

또는 수동으로 실행:

```bash
# Quick test (5분)
docker compose run --rm \
  -e SCENARIO=realistic \
  -e RPS=10 \
  -e DURATION=5m \
  -e VUS=20 \
  -e BASE_URL=http://host.docker.internal:28080 \
  k6 run /scripts/dynamic.js

# Long scenario (8시간)
docker compose run --rm \
  -e SCENARIO=daily_pattern \
  -e BASE_URL=http://host.docker.internal:28080 \
  k6 run /scripts/long-scenarios.js
```

## 6단계: 수동 API 테스트 (선택)

```bash
# CPU 부하 생성
curl -X POST http://localhost:28080/api/workload/cpu \
  -H "Content-Type: application/json" \
  -d '{"durationMs": 2000, "cpuPercent": 70}'

# DB 조회
curl http://localhost:28080/api/workload/db/query?limit=10

# 현실적인 워크로드 (DB + CPU)
curl -X POST http://localhost:28080/api/workload/realistic \
  -H "Content-Type: application/json" \
  -d '{"durationMs": 1000, "cpuPercent": 50}'

# DB 상태 확인
curl http://localhost:28080/api/workload/db/status
```

## 7단계: 종료

```bash
# IntelliJ에서 Spring Boot 중지 (Stop 버튼)

# Oracle DB 컨테이너 종료
docker compose down

# 볼륨까지 삭제 (DB 데이터 초기화)
docker compose down -v
```

---

## 🐳 전체 Docker Compose 구동 방식 (x86 서버용)

App도 Docker로 구동하려면:

### 설정 방법

1. `docker-compose.yml`에서 app 주석 해제
2. `docker-compose.yml`의 app 환경변수에 `SPRING_PROFILES_ACTIVE=real` 추가
3. 멀티플랫폼 이미지 빌드:

```bash
./build-multiplatform.sh
docker compose up -d
```

**Real 프로파일 설정** (`application-real.properties`):
- DB URL: `oracle-db:1521` (Docker 네트워크 내부)
- K6 Base URL: `http://app:28080` (Docker 네트워크 내부)

---

## 트러블슈팅

### ⚠️ ORA-01017: invalid username/password 에러

**증상**: IntelliJ에서 Spring Boot 실행 시 `ORA-01017` 에러 발생

**원인**: Oracle DB의 `testuser` 계정이 생성되지 않음

**해결**:
```bash
# 1. Oracle DB가 실행 중인지 확인
docker compose ps oracle-db

# 2. setup-oracle.sh 스크립트 실행
./setup-oracle.sh

# 3. IntelliJ에서 Spring Boot 재시작
```

### Oracle DB가 시작되지 않음

```bash
# Oracle DB 로그 상세 확인
docker compose logs oracle-db

# Oracle DB 재시작
docker compose restart oracle-db

# 강제 재생성
docker compose down -v
docker compose up -d oracle-db
```

### Spring Boot가 DB 연결 실패

```bash
# 1. Oracle DB가 완전히 시작되었는지 확인 (2-3분)
docker compose logs oracle-db | grep "DATABASE IS READY TO USE"

# 2. setup-oracle.sh 스크립트 실행 (중요!)
./setup-oracle.sh

# 3. IntelliJ에서 Spring Boot 재시작
```

### K6 테스트 오류 (대시보드에서 시작 안됨)

```bash
# K6 수동 실행 테스트
docker compose run --rm \
  -e SCENARIO=realistic \
  -e RPS=10 \
  -e DURATION=1m \
  -e VUS=5 \
  -e BASE_URL=http://host.docker.internal:28080 \
  k6 run /scripts/dynamic.js --verbose
```

### 포트 충돌 (28080 already in use)

```bash
# 28080 포트 사용 중인 프로세스 확인
lsof -i :28080

# 해당 프로세스 종료 또는 IntelliJ Run Configuration에서 포트 변경
```

## 완전 초기화 및 재시작

```bash
# 1. IntelliJ에서 Spring Boot 중지

# 2. 모든 컨테이너 중지 및 볼륨 삭제
docker compose down -v

# 3. Oracle DB 시작 (2-3분 대기)
docker compose up -d oracle-db
sleep 180

# 4. Oracle 사용자 설정
./setup-oracle.sh

# 5. IntelliJ에서 Spring Boot 실행

# 6. 대시보드 접속
open http://localhost:28080/
```

## 주요 파일

### 설정 파일
- `src/main/resources/application.properties` - 기본 설정 (Local 환경)
  - `k6.base-url=http://host.docker.internal:28080`
- `src/main/resources/application-real.properties` - Real 프로파일 설정
  - `k6.base-url=http://app:28080`

### 서비스 구현
- `K6ControlService` (interface) - K6 제어 인터페이스
- `K6ControlServiceImpl` - 통합 구현체
  - Docker CLI 경로 자동 감지
  - `docker compose run`으로 K6 실행
  - `@Value("${k6.base-url}")` 주입으로 환경별 URL 설정

### Docker 설정
- `docker-compose.yml` - Docker 설정 (app 주석처리됨)
- `k6/dynamic.js` - 단시간 시나리오
- `k6/long-scenarios.js` - 장시간 시나리오

**Happy Load Testing! 🚀**