# 🚀 Quick Start Guide (로컬 ARM 맥북)

## 필수 요구사항

- ✅ Docker Desktop for Mac (Apple Silicon 지원)
- ✅ 최소 8GB RAM (권장: 16GB)
- ✅ 10GB 이상 디스크 여유 공간

## 1단계: Docker Desktop 확인

```bash
# Docker 버전 확인
docker --version

# Docker Compose 확인
docker compose version

# Buildx 확인 (멀티 플랫폼 빌드용)
docker buildx version
```

## 2단계: 프로젝트 클론 (이미 완료)

```bash
cd /Users/2509-n0097/Workspace/projects/my/test/load-test-toy
```

## 3단계: Oracle DB 컨테이너 권한 설정

Oracle DB는 x86 전용이므로 Rosetta 2 에뮬레이션이 필요합니다.

```bash
# Docker Desktop 설정에서 확인
# Settings > General > "Use Rosetta for x86/amd64 emulation on Apple Silicon" 체크
```

## 4단계: 환경 시작 (단계별)

### 4-1. Oracle DB 시작

```bash
# Oracle DB만 먼저 시작
docker compose up -d oracle-db

# 로그 확인 (2-3분 대기)
docker compose logs -f oracle-db
```

**"DATABASE IS READY TO USE"** 메시지가 나올 때까지 기다리세요!
메시지가 나오면 `Ctrl+C`로 로그를 중지하세요.

### 4-2. Oracle 사용자 설정

```bash
# Oracle 사용자 및 권한 설정 스크립트 실행
./setup-oracle.sh
```

성공 메시지:
```
✅ testuser created successfully!
```

### 4-3. Spring Boot App 시작

```bash
# Spring Boot App 시작
docker compose up -d app

# 로그 확인
docker compose logs -f app
```

**"Started LoadTestToyApplication"** 메시지 확인 후 `Ctrl+C`로 중지

### 4-4. K6 시작 (선택)

```bash
# K6 부하 테스트 시작
docker compose up -d k6
```

## 5단계: 상태 확인

```bash
# 모든 컨테이너 상태 확인
docker compose ps

# 예상 출력:
# NAME                STATUS              PORTS
# load-test-oracle    Up (healthy)        1521/tcp, 5500/tcp
# load-test-app       Up (healthy)        28080/tcp
# load-test-k6        Up                  -
```

## 6단계: 대시보드 접속

브라우저에서 아래 주소로 접속:

```
http://localhost:28080/
```

### 대시보드에서 할 수 있는 것:

1. **K6 테스트 시작/중지**
   - 시나리오 선택 (CPU, DB, Realistic, Mixed, High Burst)
   - RPS, Duration, VUs 설정
   - "Start K6 Test" 버튼 클릭

2. **Quick Actions**
   - Quick CPU Test (20 RPS, 2분)
   - Quick DB Test (15 RPS, 3분)
   - Realistic Load (10 RPS, 5분)
   - High Burst (30 RPS, 3분)

3. **상태 모니터링**
   - K6 실행 상태
   - DB 주문 건수
   - 워크로드 실행 상태

## 7단계: 수동 API 테스트 (선택)

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

## 8단계: K6 테스트 재실행

K6는 시나리오 완료 후 자동 종료됩니다. 재실행하려면:

```bash
# 방법 1: 대시보드에서 "Start K6 Test" 버튼 클릭

# 방법 2: 커맨드로 K6 재시작
docker compose restart k6

# 방법 3: 기본 시나리오 실행
docker compose run --rm k6 run /scripts/scenarios.js
```

## 9단계: 종료

```bash
# 모든 컨테이너 종료
docker compose down

# 볼륨까지 삭제 (DB 데이터 초기화)
docker compose down -v
```

## 트러블슈팅

### ⚠️ ORA-01017: invalid username/password 에러

**증상**: Spring Boot App이 시작되지 않고 `ORA-01017` 에러 발생

**원인**: Oracle DB의 `testuser` 계정이 생성되지 않음

**해결**:
```bash
# 1. Oracle DB가 실행 중인지 확인
docker compose ps oracle-db

# 2. setup-oracle.sh 스크립트 실행
./setup-oracle.sh

# 3. App 재시작
docker compose restart app

# 4. 로그 확인
docker compose logs app | grep "Started LoadTestToyApplication"
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

### Spring Boot App이 DB 연결 실패

```bash
# 1. Oracle DB가 완전히 시작되었는지 확인 (2-3분)
docker compose logs oracle-db | grep "DATABASE IS READY TO USE"

# 2. setup-oracle.sh 스크립트 실행 (중요!)
./setup-oracle.sh

# 3. App 재시작
docker compose restart app

# 4. 로그 확인
docker compose logs -f app
```

### K6가 시작되지 않음

```bash
# K6 로그 확인
docker compose logs k6

# K6 수동 실행
docker compose run --rm k6 run /scripts/scenarios.js --verbose
```

### 포트 충돌 (28080 already in use)

```bash
# 28080 포트 사용 중인 프로세스 확인
lsof -i :28080

# 포트 변경 (docker-compose.yml 수정)
# ports: "28080:28080" → "28081:28080"
```

## 성능 최적화 (Apple Silicon)

### Docker Desktop 리소스 설정

1. **Settings > Resources**
   - CPU: 최소 4 cores (권장: 6-8 cores)
   - Memory: 최소 6GB (권장: 8-12GB)
   - Disk: 60GB

2. **Settings > General**
   - ✅ "Use Rosetta for x86/amd64 emulation on Apple Silicon" 활성화
   - ✅ "Use VirtioFS" 활성화 (파일 시스템 성능 향상)

### Oracle DB 에뮬레이션 성능

Oracle DB는 Rosetta 2로 에뮬레이션되므로:
- ⚠️ 약 10-20% 성능 저하 예상
- ✅ 개발/테스트에는 충분
- 💡 프로덕션 환경은 x86 서버 권장

## 주요 파일 위치

```
프로젝트 루트/
├── docker-compose.yml       # Docker Compose 설정
├── Dockerfile               # Spring Boot App 이미지
├── k6/
│   ├── scenarios.js         # K6 기본 시나리오
│   └── dynamic.js           # K6 동적 시나리오 (대시보드용)
├── docker/
│   └── oracle-init.sql      # Oracle DB 초기화 스크립트
└── src/
    └── main/resources/
        └── application.properties  # Spring Boot 설정
```

## 다음 단계

1. **메트릭 수집 확인**
   - OpenTelemetry Java Agent 연동
   - Prometheus 메트릭 노출

2. **알림 설정**
   - Prometheus + Alertmanager 추가
   - 트래픽 급증 알림 규칙 설정

3. **시나리오 커스터마이징**
   - `k6/dynamic.js` 수정
   - 실제 트래픽 패턴 반영

## 도움말

- **전체 가이드**: `README.md`
- **멀티 플랫폼 빌드**: `DOCKER-BUILD.md`
- **K6 시나리오**: `SCHEDULE_GUIDE.md`

## 문제 발생 시

### 완전 초기화 및 재시작

```bash
# 1. 모든 컨테이너 중지 및 볼륨 삭제
docker compose down -v

# 2. Oracle DB 시작 (2-3분 대기)
docker compose up -d oracle-db
docker compose logs -f oracle-db
# "DATABASE IS READY TO USE" 확인 후 Ctrl+C

# 3. Oracle 사용자 설정
./setup-oracle.sh

# 4. App 시작
docker compose up -d app
docker compose logs -f app
# "Started LoadTestToyApplication" 확인 후 Ctrl+C

# 5. 대시보드 접속
# http://localhost:28080/
```

## 빠른 참조

### 한 번에 모든 것 시작하기 (처음 실행 시)

```bash
# 1. Oracle DB 시작 및 대기
docker compose up -d oracle-db && \
sleep 180 && \
docker compose logs oracle-db | grep "DATABASE IS READY"

# 2. 사용자 설정 및 App 시작
./setup-oracle.sh && \
docker compose up -d app

# 3. 대시보드 접속
open http://localhost:28080/
```

### 재시작 시 (이미 한 번 실행한 경우)

```bash
# Oracle 사용자가 이미 생성되어 있다면
docker compose up -d
```

**Happy Load Testing! 🚀**