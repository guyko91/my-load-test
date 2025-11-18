# Gemini Project Log

이 문서는 AI 에이전트(Gemini)가 이 프로젝트에서 수행한 주요 작업 및 변경 사항을 기록합니다. 향후 컨텍스트를 유지하고 작업을 계속하는 데 사용됩니다.

## 최종 업데이트: 2025-11-18

### 주요 목표
이 세션의 주요 목표는 초기 k6 실행 오류를 해결하고, 사용자의 요구에 따라 부하 테스트 기능을 점진적으로 개선하고, 대시보드 UI/UX를 향상시키는 것이었습니다.

---

### 1. 초기 오류 수정 및 환경 설정

- **k6 스크립트 경로 오류 해결**:
  - `K6ControlServiceImpl.java`에서 k6 스크립트 경로를 찾는 로직을 수정했습니다. 로컬 개발 환경의 `./k6` 디렉토리를 Docker 환경의 `/host-k6`보다 우선적으로 탐색하도록 변경하여 로컬 실행 시 스크립트를 찾지 못하는 문제를 해결했습니다.

- **k6 Docker 네트워크 오류 해결**:
  - Linux 환경의 Docker 컨테이너 내에서 `host.docker.internal`을 인식하지 못하는 문제를 해결하기 위해, k6 컨테이너 실행 명령어에 `--add-host=host.docker.internal:host-gateway` 옵션을 추가했습니다.

- **JVM 메모리 제한 설정**:
  - **로컬 실행**: `build.gradle`의 `bootRun` 태스크에 `jvmArgs = ['-Xmx1g']`를 추가하여 로컬 테스트 시 JVM 최대 힙 메모리를 1GB로 설정했습니다.
  - **Docker 실행**: `entrypoint.sh`가 `JAVA_OPTS` 환경 변수를 사용하도록 수정하고, `docker-compose.yml`에서 이 변수 값을 `-Xmx1g`로 설정하여 Docker 환경에서도 동일한 메모리 제한이 적용되도록 했습니다.

- **로컬 개발용 스크립트 (`local.sh`) 생성**:
  - IntelliJ 등 IDE에서 로컬로 실행 시 OpenTelemetry(OTEL) 에이전트가 활성화되지 않는 문제를 해결하기 위해 `local.sh` 스크립트를 생성했습니다.
  - 이 스크립트는 `./gradlew build`를 실행하여 프로젝트를 빌드하고, OTEL 환경 변수를 설정한 뒤, `-javaagent` 옵션과 함께 `java -jar` 명령어로 애플리케이션을 직접 실행합니다.

### 2. 부하 테스트 기능 및 안정성 강화

- **트랜잭션 타임아웃 설정**:
  - DB 커넥션 풀 고갈 시 요청이 응답 없이 대기하는 현상을 해결하기 위해, `DatabaseService.java`의 주요 DB 접근 메소드에 `@Transactional(timeout = 10)` 어노테이션을 추가했습니다.
  - 이를 통해 10초 이상 지연되는 트랜잭션은 `TransactionTimedOutException`을 발생시켜 명확한 500 에러로 이어지도록 보장했습니다.

- **동시 k6 테스트 실행 기능 (주요 리팩토링)**:
  - **요구사항**: 평시(baseline) 트래픽과 특정 시나리오 부하를 동시에 발생시키는 기능.
  - **백엔드**: `K6ControlService`와 그 구현체를 여러 k6 프로세스를 동시에 관리할 수 있도록 전면 리팩토링했습니다. `Map`을 사용하여 "baseline"과 "scenario" 타입으로 각 테스트를 추적합니다.
  - **API**: `DashboardController`의 API를 수정하여, `testType`에 따라 다른 종류의 테스트를 시작하고 모든 테스트를 한 번에 중지할 수 있는 유연한 구조로 변경했습니다.

### 3. 대시보드 UI/UX 개선

- **동시 테스트를 위한 UI 개편**:
  - `dashboard.html`의 UI를 '🌊 Baseline Traffic'과 '🚀 Scenario Test' 카드로 분리하여 두 종류의 테스트를 독립적으로 제어할 수 있도록 재설계했습니다.
  - `dashboard.js`를 새로운 UI 구조와 백엔드 API에 맞춰 전면 재작성했습니다.

- **DB 커넥션 풀 상태 표시**:
  - **백엔드**: `spring-boot-starter-actuator` 의존성을 추가하고, `MeterRegistry`를 사용하여 HikariCP의 실시간 상태(active, idle, pending)를 조회하는 API 엔드포인트(`/api/dashboard/db/pool-status`)를 추가했습니다.
  - **프론트엔드**: 대시보드의 'DB Pool Control' 카드에 커넥션 풀의 실시간 상태를 시각적으로 표시하는 기능을 추가했습니다. 3초마다 자동으로 새로고침됩니다.

- **CSS 깨짐 문제 해결**:
  - UI 리팩토링 과정에서 발생한 CSS 깨짐 현상을 해결하기 위해, `dashboard.css` 파일의 내용을 현재 HTML 구조에 맞는 올바른 스타일로 교체했습니다.

- **DB 커넥션 풀 최소값 문제 해결**:
  - 대시보드에서 최대 풀 크기를 `minimum-idle` 값보다 작게 설정할 수 없었던 문제의 원인(HikariCP 정책)을 진단했습니다.
  - `DatabaseService`에서 요청된 크기가 최소 유휴값보다 작을 경우, 자동으로 최소값으로 조정하고 경고 로그를 남기도록 수정하여 사용자 경험을 개선했습니다.
