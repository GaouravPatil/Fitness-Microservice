SHELL := /bin/bash
.DEFAULT_GOAL := run

# Load local overrides if present.
ifneq (,$(wildcard .env))
include .env
export
endif

MVN ?= mvn
JAVA ?= java
JAVA_OPTS ?=

EUREKA_JAR   := eureka/target/eureka-0.0.1-SNAPSHOT.jar
USER_JAR     := userservice/target/userservice-0.0.1-SNAPSHOT.jar
ACTIVITY_JAR := activityservice/target/activityservice-0.0.1-SNAPSHOT.jar
AI_JAR       := aiservice/demo/target/aiservice-0.0.1-SNAPSHOT.jar
ALL_JARS     := $(EUREKA_JAR) $(USER_JAR) $(ACTIVITY_JAR) $(AI_JAR)

RUN_DIR := .run
LOG_DIR := logs
APP_PORTS := 8083 8082 8081 8761

DOCKER_OK := $(shell docker info >/dev/null 2>&1 && echo yes || echo no)
PODMAN_OK := $(shell command -v podman >/dev/null 2>&1 && echo yes || echo no)
PODMAN_AUTHFILE ?=
PODMAN_AUTH_ARG := $(if $(PODMAN_AUTHFILE),--authfile $(PODMAN_AUTHFILE),)

.PHONY: run start stop stop-apps infra infra-down build test clean logs status restart help

## run: build everything, start infrastructure and all services
run:
	@$(MAKE) --no-print-directory stop-apps
	@$(MAKE) --no-print-directory build
	@$(MAKE) --no-print-directory infra
	@$(MAKE) --no-print-directory start

## start: start the four Spring Boot services (jars must exist)
start:
	@set -e; \
	for jar in $(ALL_JARS); do \
		if [ ! -f "$$jar" ]; then echo "Missing $$jar. Run 'make build' first."; exit 1; fi; \
	done; \
	mkdir -p $(RUN_DIR) $(LOG_DIR); \
	start_service() { \
		name=$$1; jar=$$2; port=$$3; \
		echo "Starting $$name..."; \
		nohup $(JAVA) $(JAVA_OPTS) -jar "$$jar" > "$(LOG_DIR)/$$name.log" 2>&1 & \
		echo $$! > "$(RUN_DIR)/$$name.pid"; \
		for i in $$(seq 1 90); do \
			if curl -sf "http://localhost:$$port/actuator/health" >/dev/null 2>&1; then \
				echo "  $$name is UP on port $$port"; return 0; \
			fi; \
			if ! kill -0 "$$(cat $(RUN_DIR)/$$name.pid)" 2>/dev/null; then \
				echo "  $$name crashed - see $(LOG_DIR)/$$name.log"; return 1; \
			fi; \
			sleep 2; \
		done; \
		echo "  $$name did not become healthy - see $(LOG_DIR)/$$name.log"; return 1; \
	}; \
	start_service eureka $(EUREKA_JAR) 8761; \
	start_service userservice $(USER_JAR) 8081; \
	start_service activityservice $(ACTIVITY_JAR) 8082; \
	start_service aiservice $(AI_JAR) 8083; \
	echo ""; \
	echo "All services are running:"; \
	echo "  Eureka          http://localhost:8761"; \
	echo "  Users           http://localhost:8081/swagger-ui.html"; \
	echo "  Activities      http://localhost:8082/swagger-ui.html"; \
	echo "  Recommendations http://localhost:8083/swagger-ui.html"; \
	echo ""; \
	echo "Logs: make logs  |  Status: make status  |  Stop: make stop"

## stop-apps: stop only the four Spring Boot services
stop-apps:
	@for name in aiservice activityservice userservice eureka; do \
		pidfile="$(RUN_DIR)/$$name.pid"; \
		if [ -f "$$pidfile" ]; then \
			pid=$$(cat "$$pidfile"); \
			if kill -0 "$$pid" 2>/dev/null; then kill "$$pid"; echo "Stopped $$name ($$pid)"; fi; \
			rm -f "$$pidfile"; \
		fi; \
	done
	@for port in $(APP_PORTS); do \
		if fuser -s "$$port/tcp" 2>/dev/null; then \
			echo "Stopping process(es) on port $$port"; \
			fuser -k "$$port/tcp" >/dev/null 2>&1 || true; \
		fi; \
	done
	@sleep 1

## infra: start PostgreSQL, MongoDB and Kafka
infra:
ifeq ($(DOCKER_OK),yes)
	@echo "Starting infrastructure with Docker Compose..."
	@docker compose up -d
else ifeq ($(PODMAN_OK),yes)
	@set -e; \
	echo "Docker not accessible; using Podman for missing infrastructure."; \
	if ! (echo >/dev/tcp/127.0.0.1/5432) 2>/dev/null; then \
		if podman container exists fitness-postgres; then \
			podman start fitness-postgres >/dev/null; \
		else \
			podman $(PODMAN_AUTH_ARG) run -d --name fitness-postgres \
				-e POSTGRES_DB=fitnessdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
				-p 127.0.0.1:5432:5432 docker.io/postgres:16-alpine >/dev/null; \
		fi; \
	fi; \
	if ! (echo >/dev/tcp/127.0.0.1/27017) 2>/dev/null; then \
		if podman container exists fitness-mongodb; then \
			podman start fitness-mongodb >/dev/null; \
		else \
			podman $(PODMAN_AUTH_ARG) run -d --name fitness-mongodb \
				-p 127.0.0.1:27017:27017 docker.io/mongo:7 >/dev/null; \
		fi; \
	fi; \
	if ! (echo >/dev/tcp/127.0.0.1/9092) 2>/dev/null; then \
		if podman container exists fitness-kafka; then \
			podman start fitness-kafka >/dev/null; \
		else \
			podman $(PODMAN_AUTH_ARG) run -d --name fitness-kafka -p 127.0.0.1:9092:9092 \
				-e KAFKA_NODE_ID=1 \
				-e KAFKA_PROCESS_ROLES=broker,controller \
				-e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
				-e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
				-e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
				-e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
				-e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
				-e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
				-e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
				-e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
				-e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
				-e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
				-e KAFKA_NUM_PARTITIONS=3 \
				docker.io/apache/kafka:3.9.1 >/dev/null; \
		fi; \
	fi; \
	echo "Waiting for PostgreSQL, MongoDB and Kafka..."; \
	for entry in postgres:5432 mongodb:27017 kafka:9092; do \
		name=$${entry%%:*}; port=$${entry##*:}; \
		for i in $$(seq 1 60); do \
			if (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then echo "  $$name is ready on port $$port"; break; fi; \
			if [ "$$i" -eq 60 ]; then echo "  $$name did not become ready on port $$port"; exit 1; fi; \
			sleep 1; \
		done; \
	done
else
	@echo "No container runtime available. Install Docker or Podman."
	@exit 1
endif

## infra-down: stop infrastructure started by Docker Compose / Podman
infra-down:
ifeq ($(DOCKER_OK),yes)
	@docker compose down
else ifeq ($(PODMAN_OK),yes)
	@for c in fitness-kafka fitness-mongodb fitness-postgres; do \
		if podman container exists "$$c"; then podman stop "$$c" >/dev/null 2>&1 || true; fi; \
	done
endif

## build: compile and package all modules
build:
	@echo "Building all modules..."
	@$(MVN) -q -DskipTests package

## test: run all tests
test:
	@$(MVN) test

## stop: stop services and infrastructure
stop:
	@$(MAKE) --no-print-directory stop-apps
	@$(MAKE) --no-print-directory infra-down

## restart: restart only the Spring Boot services
restart:
	@$(MAKE) --no-print-directory stop-apps
	@$(MAKE) --no-print-directory start

## logs: follow all service logs
logs:
	@files=$$(ls $(LOG_DIR)/*.log 2>/dev/null || true); \
	if [ -z "$$files" ]; then echo "No logs yet. Run 'make run' first."; else tail -n 50 -f $$files; fi

## status: show service and infrastructure status
status:
	@echo "Services:"
	@for entry in eureka:8761 userservice:8081 activityservice:8082 aiservice:8083; do \
		name=$${entry%%:*}; port=$${entry##*:}; \
		if curl -sf "http://localhost:$$port/actuator/health" >/dev/null 2>&1; then \
			echo "  $$name ($$port): UP"; \
		elif (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then \
			echo "  $$name ($$port): LISTENING"; \
		else \
			echo "  $$name ($$port): DOWN"; \
		fi; \
	done
	@echo "Infrastructure:"
	@for entry in postgres:5432 mongodb:27017 kafka:9092; do \
		name=$${entry%%:*}; port=$${entry##*:}; \
		if (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then \
			echo "  $$name ($$port): UP"; \
		else \
			echo "  $$name ($$port): DOWN"; \
		fi; \
	done

## clean: stop everything and remove build/runtime artifacts
clean:
	@$(MAKE) --no-print-directory stop
	@$(MVN) clean
	@rm -rf $(RUN_DIR) $(LOG_DIR)
	@echo "Clean complete."

## help: show available targets
help:
	@echo "Fitness Microservice commands:"
	@echo "  make run       Build and start infrastructure + all services (default)"
	@echo "  make stop      Stop all services and infrastructure"
	@echo "  make restart   Restart only the Spring Boot services"
	@echo "  make build     Build all modules"
	@echo "  make test      Run all tests"
	@echo "  make status    Show service and infrastructure status"
	@echo "  make logs      Follow service logs"
	@echo "  make clean     Stop everything and remove build artifacts"
