# Getting Started

### Prerequisites
- Java version 21
- Maven 3.9+

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