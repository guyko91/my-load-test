# Multi-Platform Docker Build Guide

## 지원 플랫폼

### Spring Boot App
- ✅ **linux/amd64** (x86_64)
- ✅ **linux/arm64** (ARM64, Apple Silicon)

### Oracle Database
- ✅ **linux/amd64** (x86_64) - 네이티브 지원
- ⚠️ **linux/arm64** (Apple Silicon) - Rosetta 2 에뮬레이션 (느릴 수 있음)

### K6
- ✅ **linux/amd64** (x86_64)
- ✅ **linux/arm64** (ARM64)

## 빌드 방법

### 1. Buildx를 사용한 멀티 플랫폼 빌드

```bash
# 빌드 스크립트 실행
./build-multiplatform.sh
```

또는 직접 빌드:

```bash
# Buildx 빌더 생성
docker buildx create --name multiplatform-builder --use --bootstrap

# 멀티 플랫폼 빌드 (amd64 + arm64)
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t load-test-app:latest \
  --load \
  .
```

### 2. Docker Compose 빌드

```bash
# 현재 플랫폼용 빌드
docker-compose build

# 특정 플랫폼 지정
docker-compose build --build-arg BUILDPLATFORM=linux/amd64
```

### 3. 레지스트리에 푸시 (선택)

```bash
# Docker Hub 예시
docker tag load-test-app:latest your-username/load-test-app:latest
docker push your-username/load-test-app:latest

# 멀티 플랫폼 빌드 & 푸시
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t your-username/load-test-app:latest \
  --push \
  .
```

## 플랫폼별 실행

### x86_64 (Intel/AMD) 서버

```bash
# 네이티브 실행 (최고 성능)
docker-compose up -d
```

### ARM64 (Apple Silicon M1/M2)

```bash
# Oracle DB는 에뮬레이션 (platform: linux/amd64 지정됨)
docker-compose up -d
```

**주의**: Oracle DB는 Rosetta 2로 에뮬레이션되므로 약간 느릴 수 있습니다.

## 아키텍처 확인

```bash
# 이미지 아키텍처 확인
docker image inspect load-test-app:latest | grep Architecture

# 실행 중인 컨테이너 아키텍처 확인
docker inspect load-test-app | grep -A 5 "Platform"
```

## 성능 비교

| 플랫폼 | Spring Boot App | Oracle DB | K6 | 전체 성능 |
|--------|----------------|-----------|-----|-----------|
| **x86_64 서버** | 네이티브 | 네이티브 | 네이티브 | ⭐⭐⭐⭐⭐ |
| **Apple Silicon** | 네이티브 | 에뮬레이션 | 네이티브 | ⭐⭐⭐⭐ |

## Buildx 설치 확인

```bash
# Buildx 버전 확인
docker buildx version

# 사용 가능한 빌더 확인
docker buildx ls

# Buildx 활성화 (Docker Desktop에는 기본 포함)
docker buildx create --use
```

## 트러블슈팅

### 1. "buildx: command not found"
Docker Desktop을 설치하거나 Docker Engine에 buildx 플러그인을 설치하세요.

```bash
# Linux에서 buildx 설치
DOCKER_BUILDKIT=1 docker build --platform=local -o . \
  git://github.com/docker/buildx
mkdir -p ~/.docker/cli-plugins
mv buildx ~/.docker/cli-plugins/docker-buildx
chmod +x ~/.docker/cli-plugins/docker-buildx
```

### 2. Apple Silicon에서 Oracle DB가 느림
Oracle DB는 amd64만 지원하므로 Rosetta 2로 에뮬레이션됩니다. 이는 정상이며, 개발/테스트 환경에서는 충분합니다.

대안:
- x86 서버에서 실행
- PostgreSQL 등 ARM 네이티브 지원 DB 사용

### 3. "multiple platforms feature is currently not supported"
Docker Compose 버전이 낮을 수 있습니다. `platforms` 옵션 제거 후 수동으로 buildx 사용:

```bash
# 직접 빌드 후 docker-compose 사용
./build-multiplatform.sh
docker-compose up -d
```

## CI/CD 파이프라인 예시

### GitHub Actions

```yaml
name: Build Multi-Platform

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2

      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: true
          tags: your-username/load-test-app:latest
```

## 요약

✅ **Spring Boot App**: amd64 + arm64 네이티브 지원
⚠️ **Oracle DB**: amd64만 네이티브, arm64는 에뮬레이션
✅ **K6**: amd64 + arm64 네이티브 지원

**권장**: x86 서버에서 프로덕션 실행, Apple Silicon에서 개발