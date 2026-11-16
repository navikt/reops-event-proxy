# reops-event-proxy
Receives insight events over HTTP and publishes them to Kafka.

## Redaction opt-outs
The optional `X-Opt-Out-Filters` HTTP header accepts the comma-separated tokens
`uuid` and `name`, for example `uuid,name`. The proxy forwards recognized tokens
as a JSON array in the `x-opt-out-filters` Kafka header. The `name` token disables
the downstream `PROXY-NAME` rule for the event, not just its `name` field.
Other redaction rules still apply; assess whether retaining names is appropriate.

## Prerequisites
- Java 25

## Build
`./gradlew build`

### Run locally
Start only Kafka and its browser UI (no database or observability stack needed):

```bash
docker compose -f .compose/docker-compose.yaml up -d controller-1 broker-1 kafka-ui
./gradlew bootRun --args='--spring.profiles.active=local --management.tracing.enabled=false'
```

The local profile uses HTTP port `8082`, Kafka at `localhost:9094` without SSL,
and the output topic `raw`. Kafka UI runs at http://localhost:18950.

See [local testing with Bruno and Kafka UI](docs/local-testing.md) for requests,
expected Kafka headers, CLI inspection, and an optional redaction comparison.

### Native image (optional, requires GraalVM JDK 25)
`./gradlew nativeCompile`

Or via Docker:

`docker build -t reops-event-proxy .`

`docker run --network host -e SPRING_PROFILES_ACTIVE=local reops-event-proxy`
