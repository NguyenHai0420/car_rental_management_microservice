# Car rental management microservices

Java Spring Boot microservices with a React frontend.

Quản lý đội xe qua React, Keycloak, Gateway và MySQL. [Hướng dẫn chạy local và kiểm thử](car_rental_microservice_project_be/docs/vehicle-management.md).

Unified MinIO module with Spring Boot API, S3 server, lifecycle commands and bucket configuration:
[MinIO service](car_rental_microservice_project_be/services/minio-service/README.md).

Storage API contract, caller permissions and HTTP consumers:
[Storage API](car_rental_microservice_project_be/services/minio-service/API.md).

Vehicle media upload, public images, private documents and local MinIO setup:
[Hướng dẫn tích hợp MinIO cho vehicle-service](car_rental_microservice_project_be/docs/minio-vehicle-media.md).

Khi chạy các service đã tích hợp, cấu hình các biến môi trường trong
[`car_rental_microservice_project_be/.env.example`](car_rental_microservice_project_be/.env.example)
cho từng tiến trình. Gateway và maintenance-service phải dùng cùng `MAINTENANCE_GATEWAY_SECRET`;
Gateway và notification-service dùng cùng `NOTIFICATION_GATEWAY_SECRET`;
booking-service và notification-service dùng cùng `NOTIFICATION_INTERNAL_TOKEN`.
Booking gửi xác nhận qua `POST /api/v1/notifications/booking-confirmation`.
SePay đọc thông tin xác thực từ `SEPAY_API_KEY` và `SEPAY_API_TOKEN` trong môi trường.
