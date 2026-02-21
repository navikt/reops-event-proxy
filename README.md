# Getting Started

### Prerequisites
- Java version 21

### Build the project
```
./gradlew build
./gradlew test --rerun-tasks
```

### Run locally
1. Start docker-compose under .compose `docker-compose up`, has a readme in that folder describing the services started.
2. Start application with profile `local`:  
   `./gradlew bootRun -Dspring-boot.run.profiles=local`
 - local profile does not use ssl for kafka

### Build native image locally (requires GraalVM JDK 25)
```
./gradlew nativeCompile
./build/native/nativeCompile/app
```

### Build and run with docker (native image)
```
docker build -t reops-event-proxy .
```
```
docker run --network host -e SPRING_PROFILES_ACTIVE=local reops-event-proxy
```
