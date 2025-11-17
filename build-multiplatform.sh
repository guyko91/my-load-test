#!/bin/bash

# Multi-platform Docker build script using buildx

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Multi-Platform Docker Build ===${NC}"

# Image name and tag
IMAGE_NAME="load-test-app"
IMAGE_TAG="latest"
FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"

# Check if buildx is available
if ! docker buildx version > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker buildx is not available${NC}"
    echo "Please install Docker Desktop or enable buildx"
    exit 1
fi

# Create and use buildx builder
echo -e "${GREEN}Creating buildx builder...${NC}"
docker buildx create --name multiplatform-builder --use --bootstrap 2>/dev/null || docker buildx use multiplatform-builder

# Build for multiple platforms
echo -e "${GREEN}Building for linux/amd64 and linux/arm64...${NC}"
docker buildx build \
    --platform linux/amd64,linux/arm64 \
    -t ${FULL_IMAGE_NAME} \
    --load \
    .

echo -e "${GREEN}✅ Multi-platform build completed!${NC}"
echo -e "${BLUE}Image: ${FULL_IMAGE_NAME}${NC}"

# Show image info
echo -e "\n${BLUE}Image details:${NC}"
docker images | grep ${IMAGE_NAME}

echo -e "\n${GREEN}To push to registry:${NC}"
echo "docker tag ${FULL_IMAGE_NAME} <registry>/${FULL_IMAGE_NAME}"
echo "docker push <registry>/${FULL_IMAGE_NAME}"