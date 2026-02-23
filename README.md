# reops-event-proxy
Receives insight events over HTTP and publishes them to Kafka.

## Prerequisites
- Java 25

## Build
`./gradlew build`

### Run locally
1. Start docker-compose under .compose ` cd .compose && docker compose up`, has a readme in that folder describing the services started.
2. Start the application with the local profile:  
   `./gradlew bootRun -Dspring-boot.run.profiles=local`
 - local profile does not use ssl for kafka

### Native image (optional, requires GraalVM JDK 25)
`./gradlew nativeCompile`

Or via Docker:

`docker build -t reops-event-proxy .`

`docker run --network host -e SPRING_PROFILES_ACTIVE=local reops-event-proxy`
