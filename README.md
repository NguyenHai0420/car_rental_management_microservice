# Car rental management microservices

Java Spring Boot microservices with a React frontend.

Quản lý đội xe qua React, Keycloak, Gateway và MySQL. [Hướng dẫn chạy local và kiểm thử](docs/vehicle-management.md).

Unified MinIO module with Spring Boot API, S3 server, lifecycle commands and bucket configuration:
[MinIO service](car_rental_microservice_project_be/services/minio-service/README.md).

Storage API contract, caller permissions and HTTP consumers:
[Storage API](car_rental_microservice_project_be/services/minio-service/API.md).

Vehicle media upload, public images, private documents and local MinIO setup:
[Hướng dẫn tích hợp MinIO cho vehicle-service](docs/minio-vehicle-media.md).
