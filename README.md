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

## Chạy toàn bộ dự án bằng Docker

Chỉ cần Docker Desktop. Từ thư mục gốc repo:

```powershell
docker compose up -d --build
```

- Web: <http://localhost:5173> (tài khoản Keycloak: `admin` / `123456`, `owner1`, `customer1` cùng mật khẩu)
- Gateway: <http://localhost:8080>, Eureka: <http://localhost:8761>, Keycloak admin: <http://localhost:8180> (`admin` / `admin`)
- MinIO console: <http://localhost:9001> (`minioadmin` / `minioadmin`), MySQL: `localhost:3307` (`root` / `1234`)

Tạo dữ liệu thuê xe cho `user` và `admin` (hồ sơ ACTIVE, GPLX đã duyệt, 3 xe mẫu):

```powershell
.\car_rental_microservice_project_be\scripts\seed-rental-demo.ps1 -VerifyBooking
```

Cả hai tài khoản đăng nhập ứng dụng bằng mật khẩu `123456`. [Chi tiết dữ liệu mẫu và cách thuê thử](car_rental_microservice_project_be/docs/rental-demo-data.md).

Lần đầu build mất vài phút. Xem log: `docker compose logs -f <service>`. Dừng: `docker compose down` (thêm `-v` để xóa dữ liệu MySQL/MinIO). Sau khi sửa code chạy lại `docker compose up -d --build`.

Khi chạy Java từ IntelliJ và dùng MinIO trong Docker, đặt
`FILE_SERVICE_URL=http://localhost:8087`. Compose mở API lưu file ở
`127.0.0.1:8087`; cổng `9000` là S3 và `9001` là giao diện quản trị MinIO.
Các service chạy trong Docker dùng `FILE_SERVICE_URL=http://minio-service:8087`.
