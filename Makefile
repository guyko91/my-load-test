.PHONY: help up down logs ps restart build clean rebuild status

# Auto-detect Docker GID
ifeq ($(shell uname),Darwin)
	DOCKER_GID := $(shell stat -f "%g" /var/run/docker.sock 2>/dev/null || echo 999)
else
	DOCKER_GID := $(shell stat -c "%g" /var/run/docker.sock 2>/dev/null || echo 999)
endif

export DOCKER_GID

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ''
	@echo 'Auto-detected DOCKER_GID: $(DOCKER_GID)'

up: ## Start all services (build + detached)
	@echo "🔧 Using DOCKER_GID=$(DOCKER_GID)"
	docker compose up -d --build --remove-orphans

down: ## Stop all services
	docker compose down

down-v: ## Stop all services and remove volumes
	docker compose down -v

logs: ## Show logs (follow mode)
	docker compose logs -f

logs-app: ## Show app logs only
	docker compose logs -f app

ps: ## Show running containers
	docker compose ps

restart: ## Restart all services
	docker compose restart

restart-app: ## Restart app service only
	docker compose restart app

build: ## Build images
	@echo "🔧 Using DOCKER_GID=$(DOCKER_GID)"
	docker compose build

rebuild: down build up ## Rebuild and restart everything

clean: ## Clean up everything (containers, volumes, images)
	docker compose down -v --rmi all

status: ## Show status and health
	@echo "=== Container Status ==="
	@docker compose ps
	@echo ""
	@echo "=== Docker GID ==="
	@echo "DOCKER_GID=$(DOCKER_GID)"
	@echo ""
	@echo "=== Application Health ==="
	@curl -s http://localhost:28080/actuator/health 2>/dev/null || echo "App not running"

shell-app: ## Open shell in app container
	docker compose exec app /bin/bash

shell-oracle: ## Open Oracle SQL*Plus
	docker compose exec oracle-db sqlplus testuser/testpass@//localhost:1521/XEPDB1

init: ## First time setup
	@echo "🚀 Initial setup..."
	@echo "DOCKER_GID=$(DOCKER_GID)" > .env
	@echo "✅ Created .env file with DOCKER_GID=$(DOCKER_GID)"
	@$(MAKE) up
	@echo "✅ All services started!"
	@echo "🌐 Dashboard: http://localhost:28080/dashboard"
