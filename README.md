# Fitness Microservice

Fitness activity tracking built as a Spring Boot microservice system.

| Module | Port | Responsibility | Storage |
|---|---|---|---|
| `eureka` | 8761 | Service discovery | - |
| `userservice` | 8081 | Registration, login (JWT), profiles | PostgreSQL |
| `activityservice` | 8082 | Activity tracking, user validation, Kafka producer | MongoDB |
| `aiservice/demo` | 8083 | Kafka consumer, recommendation generation | MongoDB |

## Architecture

```
Client -> userservice  (register / login -> JWT)
Client -> activityservice --(JWT + internal key)--> userservice
             |
             +-- saves activity in MongoDB
             +-- publishes Activity JSON to Kafka "activity-topic"

Kafka "activity-topic" -> aiservice
             +-- generates recommendation (OpenAI when configured, rules otherwise)
             +-- stores recommendation in MongoDB
Client -> aiservice (JWT, owner or admin only)
```

## Prerequisites

- JDK 24
- Maven 3.9+
- Docker (for PostgreSQL, MongoDB and Kafka)

## Start infrastructure

```bash
docker compose up -d
```

Services use these defaults: PostgreSQL `localhost:5432/fitnessdb`, MongoDB
`localhost:27017`, Kafka `localhost:9092`.

## Build and test

```bash
mvn clean verify
```

Tests are unit, slice and repository tests and do not require infrastructure.
Test counts: userservice 25, activityservice 15, aiservice 18, eureka 1.

## Run

Start in this order:

```bash
mvn -pl eureka spring-boot:run
mvn -pl userservice spring-boot:run
mvn -pl activityservice spring-boot:run
mvn -pl aiservice/demo spring-boot:run
```

## Configuration

Copy `.env.example`, change the security values, then export before running:

```bash
cp .env.example .env
set -a && source .env && set +a
```

Important variables:

| Variable | Used by | Default |
|---|---|---|
| `JWT_SECRET` | all services | development-only Base64 key |
| `INTERNAL_API_KEY` | userservice, activityservice | development-only key |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | userservice | local PostgreSQL |
| `MONGODB_URI`, `MONGODB_DATABASE` | activityservice, aiservice | local MongoDB |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC` | activityservice, aiservice | `localhost:9092`, `activity-topic` |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` | aiservice | empty key -> rule-based advice |
| `EUREKA_URL` | userservice, activityservice, aiservice | `http://localhost:8761/eureka/` |

## API quick start

Register (returns `201`, never returns the password):

```bash
curl -X POST http://localhost:8081/api/users/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"secret123","firstName":"Jane","lastName":"Doe"}'
```

Login and capture the token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"secret123"}' | jq -r .accessToken)
```

Track an activity (the JWT subject must match `userId`):

```bash
curl -X POST http://localhost:8082/api/activities \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"<user-id>","type":"RUNNING","duration":30,"caloriesBurned":300,"startTime":"2026-01-01T10:00:00"}'
```

Read activities and recommendations:

```bash
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8082/api/activities/user/<user-id>?page=0&size=20'
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8083/api/recommendations/user/<user-id>?page=0&size=20'
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8083/api/recommendations/activity/<activity-id>'
```

OpenAPI UI: `http://localhost:<port>/swagger-ui.html`
Health: `http://localhost:<port>/actuator/health`

## Security notes

- Passwords are hashed with BCrypt and never serialized in responses.
- JWTs are HS256, signed with `JWT_SECRET`; userservice issues them and the
  other services validate them with the same secret.
- `GET /api/users/{id}/validate` is internal and requires `X-Internal-Api-Key`.
- Profile, activity and recommendation endpoints enforce owner-or-admin access.
- Change the development defaults for `JWT_SECRET` and `INTERNAL_API_KEY`
  before deploying.

## Operational notes

- Activity publishing waits for Kafka acknowledgement (5s timeout). If Kafka
  is unavailable the request fails with `503`; the activity was already stored,
  so a client retry can create a duplicate activity.
- Kafka redelivery is idempotent: a recommendation is updated in place per
  `activityId` instead of creating duplicates.
- `ddl-auto=update` is for local development. Use Flyway/Liquibase with
  `ddl-auto=validate` in production.
