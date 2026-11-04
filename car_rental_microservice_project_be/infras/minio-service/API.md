# API lưu file trong MinIO service

API Java trong module `infras/minio-service` quản lý thao tác lưu, xóa và cấp URL cho file trên MinIO. Vehicle-service gọi API này qua HTTP và giữ metadata, quyền người dùng cùng nghiệp vụ xe trong database của mình. API lưu file không cần database và không expose qua gateway cho trình duyệt. Mã Java, Dockerfile và Compose đã nằm chung một module; tên `FileService` trong các lớp ứng dụng chỉ mô tả chức năng quản lý file.

```text
React → Gateway → vehicle-service → HTTP file-service → MinIO
                         │
                         └→ MySQL vehicle_db

customer-service → HTTP file-service → MinIO
```

## Khởi chạy

Cách chạy toàn bộ module qua Docker, từ thư mục backend:

```powershell
.\infras\minio-service\minio.ps1 start
```

Script tự build và khởi động API Java cùng MinIO Server. Chi tiết vận hành nằm trong [README](README.md). Chỉ khi debug Java bằng IDE mới cần Java 21/Maven trên máy. Khi đó dừng container `minio-api` trước để nhường cổng 8087 và chạy:

```powershell
mvn -pl infras/minio-service spring-boot:run
```

Có thể chạy class `crm.ms.files.MinioServiceApplication` bằng IDE. API dùng `http://127.0.0.1:8087`, health endpoint là `/actuator/health`. Khi debug riêng, đặt `EUREKA_CLIENT_ENABLED=false` để không cần Eureka. File `java.env.example` chứa cấu hình cho tiến trình Java; Spring Boot không tự nạp file này. Compose dùng `.env` chung để cấu hình cả API và MinIO Server.

Để dùng luồng ảnh/giấy tờ xe, chạy tiếp Eureka, vehicle-service, gateway, MySQL và Keycloak theo hướng dẫn tích hợp media. Không cần chạy thêm Java API ngoài Docker. Frontend gọi API xe; service key không gửi xuống trình duyệt.

## Cấu trúc

- `application/port/ObjectStorage`: port thuần Java cho object storage.
- `application/FileService`: kiểm tra scope bucket/prefix, file type, dung lượng, thời hạn URL và gọi port.
- `infrastructure/storage/Minio*`: SDK, cấu hình và adapter MinIO; chỉ file-service sở hữu credentials MinIO.
- `infrastructure/config`: cấu hình quyền cho từng caller và giới hạn storage.
- `interfaces/http`: HTTP controller, xác thực service và ánh xạ lỗi.

Trong vehicle-service, `FileStorage` vẫn là port thuộc tầng ứng dụng; `HttpFileStorage` triển khai port bằng HTTP. Vehicle-service không phụ thuộc MinIO SDK hay JAR của file-service.

## API nội bộ

Mọi request cần hai header:

```text
X-Service-Name: vehicle-service
X-Service-Key: <FILE_SERVICE_VEHICLE_API_KEY>
```

| Method | Endpoint | Input | Response |
| --- | --- | --- | --- |
| PUT | `/internal/v1/files/object` | Query `bucket`, `key`; body là bytes; `Content-Type`, `Content-Length` | 204 |
| DELETE | `/internal/v1/files/object` | Query `bucket`, `key` | 204, xóa idempotent |
| GET | `/internal/v1/files/public-url` | Query `bucket`, `key` | `{url}` |
| GET | `/internal/v1/files/download-url` | Query `bucket`, `key`, `expirySeconds` | `{url, expiresAt}` |

Upload dùng raw body để stream file giữa các service, không đóng gói multipart thêm một lần. Client cung cấp object key mới và duy nhất; ví dụ `vehicles/{vehicleId}/images/{uuid}.png`. Nhờ biết key trước khi gửi, service nghiệp vụ có thể xóa bù nếu transaction DB thất bại hoặc phản hồi upload bị mất. Không dùng lại key của file có sẵn cho một lần upload mới.

Ảnh hỗ trợ JPEG, PNG, WebP; tài liệu hỗ trợ thêm PDF. File-service kiểm tra chữ ký đầu file cùng MIME. Mặc định mỗi file tối đa 10 MiB; URL ký tối đa 10 phút. `Content-Length` là bắt buộc. Object key không được chứa segment rỗng, `.` hoặc `..`, backslash hay ký tự điều khiển.

Lỗi trả `{code, message}`: 400 input không hợp lệ, 401 thiếu/sai service key, 403 bucket/key ngoài scope hoặc xin public URL cho bucket private, 411 thiếu Content-Length, 413 quá dung lượng, 503 MinIO lỗi. Response chứa URL dùng `Cache-Control: no-store`.

## Phân quyền giữa các service

| Caller | Buckets mặc định | Prefix bắt buộc | Khóa cấu hình |
| --- | --- | --- | --- |
| `vehicle-service` | `vehicle-images`, `vehicle-documents` | `vehicles/` | `FILE_SERVICE_VEHICLE_API_KEY` |
| `customer-service` | `customer-images` | `customers/` | `FILE_SERVICE_CUSTOMER_API_KEY` |

Hai khóa mặc định trong YAML là giá trị cho local development; khi triển khai cần cấp khóa riêng qua environment cho từng caller. Khóa của vehicle-service phải giống nhau ở tiến trình file-service và vehicle-service. `X-User-Roles` hoặc JWT người dùng không thay thế được service key. File-service mặc định bind loopback; khi đưa vào Docker, đặt `FILE_SERVICE_BIND_ADDRESS=0.0.0.0` trên mạng nội bộ và dùng TLS nếu kết nối qua mạng không tin cậy.

Các client/bucket/prefix mới được khai báo trong `storage.clients` của application.yml. Bucket public được khai báo bằng `FILE_SERVICE_PUBLIC_BUCKETS`; cấu hình này phải khớp policy trên MinIO. File-service không tự mở quyền public hoặc tạo bucket khi nhận upload.

Để thêm ảnh customer, tạo bucket qua `infras/minio-service` bằng `MINIO_PRIVATE_BUCKETS=vehicle-documents,customer-images`, chạy `minio.ps1 init`, rồi triển khai HTTP caller và metadata trong customer-service. Scope `customer-service` đã được chuẩn bị, nhưng module này không tự thêm API hay giao diện customer.

## Cấu hình kết nối

File-service → MinIO:

```text
MINIO_ENDPOINT=http://localhost:9000
MINIO_PUBLIC_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
```

Vehicle-service → file-service:

```text
FILE_SERVICE_URL=http://localhost:8087
FILE_SERVICE_VEHICLE_API_KEY=local-vehicle-file-service-key
VEHICLE_IMAGE_BUCKET=vehicle-images
VEHICLE_DOCUMENT_BUCKET=vehicle-documents
VEHICLE_PRESIGN_EXPIRY=10m
```

Vehicle-service không cần access key/secret key MinIO. Nếu đổi tên bucket, đồng bộ tên ở vehicle-service, scope của file-service và bucket init. Các biến `MINIO_IMAGE_BUCKET`, `MINIO_DOCUMENT_BUCKET`, `MINIO_PRESIGN_EXPIRY` cũ vẫn được chấp nhận làm fallback ở phía vehicle.

Nếu chạy file-service trong Docker, nối vào network `car-rental-storage` của MinIO và dùng `MINIO_ENDPOINT=http://minio:9000`. Public endpoint dùng host mà browser truy cập được. Các service nghiệp vụ trong Docker dùng địa chỉ nội bộ của file-service cho `FILE_SERVICE_URL`; discovery Eureka và transport HTTP là hai cấu hình độc lập.

## Xử lý lỗi và kiểm thử

Metadata vẫn được commit trong service nghiệp vụ. Vehicle-service đăng ký xóa bù trước khi gửi upload; rollback DB hoặc upload lỗi sẽ cố xóa object theo key mới. Khi xóa media, vehicle-service commit xóa metadata trước rồi gọi xóa object. Nếu service/storage đang gián đoạn, lỗi dọn file được log để đối soát. Đây là xử lý bù best effort; lỗi tiến trình hoặc timeout khi thao tác chưa hoàn tất vẫn cần đối soát object dư.

Vehicle-service đặt timeout kết nối 3 giây, timeout request 60 giây, không follow redirect hoặc tự retry một upload stream đã được dùng. Lỗi kết nối, credentials nội bộ hoặc server phía file-service được trả ra API xe dưới dạng 503; lỗi 400/413 giữ đúng mã tương ứng.

Từ thư mục backend:

```powershell
mvn -pl infras/minio-service,services/vehicle-service,infras/api-gateway -am clean verify
```

Test gồm HTTP client của vehicle-service, rollback metadata/object, quyền bucket/prefix của caller, raw upload và URL response. Testcontainers chạy MinIO riêng để kiểm tra SDK cùng HTTP API thật. Testcontainers 1.21.4 được dùng cho khả năng tương thích Docker Engine mới theo [release notes](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4).
