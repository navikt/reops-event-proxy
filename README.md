# Getting Started

### Prerequisites
- Java version 21
- Maven 3.9+

### Build the project
```
mvn clean install
```

### Run locally
1. Start docker-compose under .compose `docker-compose up`, has a readme in that folder describing the services started.
2. Start application with profile `local`:  
   `mvn spring-boot:run -Dspring-boot.run.profiles=local`