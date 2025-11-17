# 🚀 Quick Start Guide

이 가이드는 `docker-compose`를 사용하여 전체 애플리케이션 스택(Oracle DB, DB Setup, App)을 한 번에 시작하는 방법을 안내합니다.

## 📌 필수 요구사항

- ✅ Docker Desktop (Mac, Windows) 또는 Docker Engine (Linux)
- ✅ `aws-opentelemetry-agent.jar` 파일이 프로젝트 루트 디렉토리에 있어야 합니다.
- ✅ 최소 8GB RAM (권장: 16GB)

## 1단계: 애플리케이션 시작

프로젝트 루트 디렉토리에서 아래의 단일 명령어를 실행하세요.

```bash
# 모든 서비스를 빌드하고 백그라운드에서 시작합니다.
docker-compose up --build -d
```

이 명령어는 다음 작업을 자동으로 수행합니다.
1.  `oracle-db` 컨테이너를 시작하고 Health Check가 통과할 때까지 기다립니다.
2.  `oracle-setup` 컨테이너가 실행되어 데이터베이스 사용자(`testuser`)를 생성합니다.
3.  데이터베이스 설정이 완료되면 `app` 컨테이너가 OpenTelemetry 에이전트와 함께 시작됩니다.

> **참고:** `docker-compose` v1을 사용하는 경우, `docker-compose build && docker-compose up -d` 명령어로 실행해야 할 수 있습니다.

## 2단계: 대시보드 접속

애플리케이션이 시작된 후, 브라우저에서 아래 주소로 접속하세요.

```
http://localhost:28080/
```

### 대시보드에서 할 수 있는 것:

1.  **K6 Load Test 카드**
    - 시나리오 선택, RPS, Duration, VUs 설정 후 "Start K6 Test" 버튼으로 테스트 시작
2.  **Quick Tests (단시간)**
    - 사전 정의된 시나리오를 원클릭으로 테스트
3.  **Long Running Scenarios (장시간)**
    - 실제 운영 환경과 유사한 장시간 부하 패턴 테스트
4.  **상태 모니터링**
    - K6 실행 상태, DB 주문 건수 등 실시간 확인

## 3단계: 부하 테스트 실행

대시보드에서 원하는 테스트 시나리오의 "Start" 버튼을 클릭하세요. `app` 서비스가 동적으로 `k6` 컨테이너를 생성하고 실행하여 부하 테스트를 수행합니다. **더 이상 `docker compose run k6`와 같은 수동 명령어는 사용하지 않습니다.**

## 4단계: 수동 API 테스트 (선택)

`curl` 등을 사용하여 개별 API의 동작을 테스트할 수 있습니다.

```bash
# CPU 부하 생성
curl -X POST http://localhost:28080/api/workload/cpu \
  -H "Content-Type: application/json" \
  -d '{"durationMs": 2000, "cpuPercent": 70}'

# DB 조회
curl http://localhost:28080/api/workload/db/query?limit=10

# DB 상태 확인
curl http://localhost:28080/api/workload/db/status
```

## 5단계: 종료

모든 서비스를 중지하려면 아래 명령어를 실행하세요.

```bash
# 모든 컨테이너를 중지하고 네트워크를 제거합니다.
docker-compose down

# DB 데이터까지 완전히 삭제하려면 -v 옵션을 추가합니다.
docker-compose down -v
```

---

## 트러블슈팅

### ⚠️ `docker-compose up` 실행 시 오류 발생

- **`aws-opentelemetry-agent.jar` 파일 누락:** `Dockerfile`에서 이 파일을 복사하도록 설정되어 있습니다. 프로젝트 루트에 파일이 있는지 확인하세요.
- **포트 충돌 (28080 already in use):** 로컬 머신에서 28080 포트를 사용하는 다른 프로세스가 있는지 확인하고 종료하세요.
- **`unknown flag: --build`:** `docker-compose` v1을 사용 중일 수 있습니다. `docker-compose build && docker-compose up -d`로 실행해 보세요.

### ⚠️ Oracle DB 관련 오류

`oracle-setup` 서비스 로그에 `ORA-`로 시작하는 오류가 표시되는 경우, DB가 완전히 준비되지 않았을 수 있습니다. `docker-compose down -v`로 모든 것을 초기화한 후 다시 시도해 보세요. `setup-oracle.sh` 스크립트에 재시도 로직이 포함되어 있어 대부분의 경우 자동으로 해결됩니다.

## 주요 파일

- **`docker-compose.yml`**: 전체 서비스(oracle-db, oracle-setup, app)를 정의하고 조율합니다.
- **`Dockerfile`**: `app` 서비스의 Docker 이미지를 빌드하고, OpenTelemetry 에이전트를 포함시킵니다.
- **`setup-oracle.sh`**: `oracle-setup` 서비스에 의해 실행되어 DB 사용자를 설정합니다.
- **`K6ControlServiceImpl.java`**: 대시보드의 요청에 따라 `docker run` 명령어로 `k6` 컨테이너를 동적으로 생성하고 실행하는 핵심 로직을 담고 있습니다.
- **`k6/*.js`**: `k6`가 실행할 부하 테스트 시나리오 스크립트입니다.

**Happy Load Testing! 🚀**