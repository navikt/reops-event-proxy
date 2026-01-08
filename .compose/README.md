# Docker compose

## Description
This directory contains a `docker-compose.yaml` file that sets up a multi-container Docker application.
The application includes the following services:
- **kafka-ui**: A user interface for managing and monitoring Apache Kafka clusters.
- **kafka-broker and kafka-controller using kraft**: Apache Kafka brokers for message streaming.
- **Umami**: An open-source web analytics application.
- **Umami-db**: Postgres database for Umami.
- **Prometheus**: A monitoring and alerting toolkit.
- **Grafana**: An open-source platform for monitoring and observability.

## Prerequisites
- Docker and Docker Compose must be installed on your machine.
- Ensure that the necessary ports are available and not blocked by other applications.

Give Colima some resources (if using Colima):
```bash
colima start --cpu 4 --memory 8
``` 

## Usage
1. Navigate to the directory containing the `docker-compose.yml` file.
2. Run the following command to start the services:
   ```bash
   docker-compose up -d
   ```
3. To stop the services, run:
   ```bash
   docker-compose down --remove-orphans
   ```

## Links
| Application |          Url           |
|:------------|:----------------------:|
| kafka-ui    | http://localhost:18950 |
| Umami       | http://localhost:3000  |
| Umami-db    |     localhost:5432     |
| Prometheus  | http://localhost:9090  |
| Grafana     | http://localhost:3001  |