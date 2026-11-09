# Fitness Microservice

Fitness activity tracking built as a Spring Boot microservice system.

| Module | Port | Responsibility | Storage |
|---|---|---|---|
| `eureka` | 8761 | Service discovery (HTTP Basic protected) | - |
| `userservice` | 8081 | Registration, login (RS256 JWT), refresh/logout, profiles | PostgreSQL |
| `activityservice` | 8082 | Activity tracking, user validation, Kafka producer | MongoDB |
| `aiservice/demo` | 8083 | Kafka consumer, recommendation generation | MongoDB |

## Architecture

```
Client -> userservice  (register / login -> access + refresh JWT)
Client -> activityservice --(JWT + internal key)--> userservice
             |
             +-- saves activity in MongoDB
             +-- publishes Activity JSON to Kafka "activity-topic"
             +-- scheduled republisher retries unpublished events

Kafka "activity-topic" -> aiservice
             +-- generates recommendation (AI when configured, rules otherwise)
             +-- stores recommendation in MongoDB
Client -> aiservice (JWT, owner or admin only)
```

## Prerequisites

- JDK 24
- Maven 3.9+
- Docker/Podman (for PostgreSQL, MongoDB and Kafka)

## Start infrastructure

```bash
docker compose up -d
```

Services use these defaults: PostgreSQL `localhost:5432/fitnessdb`, MongoDB
`localhost:27017`, Kafka `localhost:9092`. Docker Compose binds all published
ports to `127.0.0.1`; Kafka is PLAINTEXT for local development only.

## Build and test

```bash
mvn clean verify
```

Tests are unit, slice and repository tests and do not require infrastructure.

## Run

Start in this order:

```bash
mvn -pl eureka spring-boot:run
mvn -pl userservice spring-boot:run
mvn -pl activityservice spring-boot:run
mvn -pl aiservice/demo spring-boot:run
```

Or use `make run` (builds everything, starts infrastructure and all four services).
`make status`, `make logs`, `make restart` and `make stop` are also available.
The Eureka dashboard is at `http://localhost:8761` and requires
`EUREKA_USERNAME` / `EUREKA_PASSWORD`.

## Configuration

Copy `.env.example` to `.env` and replace every placeholder before running.
The `Makefile` loads `.env` automatically; otherwise use `set -a && source .env && set +a`.

Generate the RSA signing keys (the application expects Base64 DER, not PEM):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
openssl pkcs8 -topk8 -nocrypt -in jwt-private.pem -outform DER | base64 -w0 > jwt-private.b64
openssl pkey -in jwt-private.pem -pubout -outform DER | base64 -w0 > jwt-public.b64
```

Put the contents of `jwt-private.b64` in `JWT_PRIVATE_KEY` and
`jwt-public.b64` in `JWT_PUBLIC_KEY`. Keep the private key out of source control.

Important variables:

| Variable | Used by | Default |
|---|---|---|
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | userservice (private), all services (public) | none, required |
| `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_KEY_ID` | all services | `https://userservice.fitness.local`, `fitness-api`, `fitness-key-1` |
| `JWT_TTL`, `JWT_REFRESH_TTL` | userservice | `PT15M`, `P7D` |
| `INTERNAL_API_KEYS` | userservice (accepted keys, comma-separated) | none, required |
| `INTERNAL_API_KEY` | activityservice (key to send) | none, required |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | userservice | local PostgreSQL |
| `JPA_DDL_AUTO`, `FLYWAY_ENABLED` | userservice | `validate`, `true` |
| `MONGODB_URI`, `MONGODB_DATABASE` | activityservice | local MongoDB (`activityfitness`) |
| `AI_MONGODB_URI`, `AI_MONGODB_DATABASE` | aiservice | local MongoDB (`airecommendationfitness`) |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC` | activityservice, aiservice | `localhost:9092`, `activity-topic` |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` | aiservice | empty key -> rule-based advice |
| `EUREKA_USERNAME`, `EUREKA_PASSWORD`, `EUREKA_HOST` | all services | local Eureka with basic auth |
| `EUREKA_BIND_ADDRESS`, `EUREKA_SERVER_PORT` | eureka | `127.0.0.1`, `8761` |
| `CORS_ALLOWED_ORIGINS` | all services | `http://localhost:3000` |
| `SPRINGDOC_ENABLED` | userservice, activityservice, aiservice | `false` |

Internal keys can be rotated without downtime: put the new key first in
`INTERNAL_API_KEYS`, deploy, then remove the old key.

## API quick start

Register (returns `201`, never returns the password; passwords require 8-72 characters):

```bash
curl -X POST http://localhost:8081/api/users/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"secret123","firstName":"Jane","lastName":"Doe"}'
```

Login and capture both tokens:

```bash
LOGIN=$(curl -s -X POST http://localhost:8081/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"secret123"}')
TOKEN=$(echo "$LOGIN" | jq -r .accessToken)
REFRESH=$(echo "$LOGIN" | jq -r .refreshToken)
```

Access tokens expire after 15 minutes. Rotate the refresh token:

```bash
curl -X POST http://localhost:8081/api/users/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

Revoke a refresh token on logout:

```bash
curl -X POST http://localhost:8081/api/users/logout \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

Track an activity (the JWT subject must match `userId`). Send an
`Idempotency-Key` header to make retries safe:

```bash
curl -X POST http://localhost:8082/api/activities \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f0c2f8e-1a9b-4f36-9f30-2b7f3c4d5e6f' \
  -d '{"userId":"<user-id>","type":"RUNNING","duration":30,"caloriesBurned":300,"startTime":"2026-01-01T10:00:00"}'
```

Read activities and recommendations:

```bash
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8082/api/activities/user/<user-id>?page=0&size=20'
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8083/api/recommendations/user/<user-id>?page=0&size=20'
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8083/api/recommendations/activity/<activity-id>'
```

OpenAPI UI (only when `SPRINGDOC_ENABLED=true`): `http://localhost:<port>/swagger-ui.html`
Health: `http://localhost:<port>/actuator/health`

## Security notes

- Passwords are hashed with BCrypt (cost 10), require 8-72 characters and are never serialized.
- JWTs are RS256. userservice holds the private key; activityservice and
  aiservice only receive the public key. Tokens carry `iss`, `aud`, `kid`, and a
  15-minute expiry; decoders validate issuer, audience and signature.
- Refresh tokens are opaque 256-bit values stored only as SHA-256 hashes. They
  are rotated on every use, revoked on logout, and expire after 7 days.
- Login is rate-limited per IP (10/min) and per account (5 failures / 15 min,
  then HTTP 429 with `Retry-After`). Registration is limited to 5/hour per IP.
- `GET /api/users/{id}/validate` is internal and requires `X-Internal-Api-Key`;
  multiple keys are accepted for rotation and compared in constant time.
- Profile, activity and recommendation endpoints enforce owner-or-admin access.
- Swagger/OpenAPI is disabled by default and actuator exposes only `health`.
- Eureka requires HTTP Basic authentication; the server binds to loopback by default.
- Never commit `.env`, the private key or the AI API key. Rotate any credential
  that has been shared or exposed.

## Operational notes

- Activities are written to MongoDB first, then published to Kafka. If Kafka is
  unavailable the API still returns the stored activity and a scheduled
  republisher retries unpublished events (`published=false`).
- Sending the same `Idempotency-Key` for the same user returns the original
  activity instead of creating a duplicate. Keys are limited to 128 characters.
- Kafka redelivery is idempotent: a recommendation is updated in place per
  `activityId` instead of creating duplicates.
- userservice uses Flyway (`V1` create users, `V2` drop legacy name columns,
  `V3` refresh tokens) with `baseline-on-migrate=true` for existing databases;
  Hibernate runs with `ddl-auto=validate`.
- MongoDB compound indexes are created automatically for activity and
  recommendation lookups.
- For production, terminate TLS in front of every service and configure Kafka
  with SASL/SSL instead of PLAINTEXT.
