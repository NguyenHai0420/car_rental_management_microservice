# MinIO service

Module này chứa cả API Spring Boot và cấu hình MinIO Server trong cùng một thư mục, một Maven module và Compose project `car-rental-minio`. API Java dùng cổng 8087, S3 dùng cổng 9000 và console dùng cổng 9001. Vehicle/customer gọi API Java; API lưu file trên MinIO Server.

```text
services/minio-service/
├── pom.xml
├── src/                  # API Java, adapter MinIO và tests
├── Dockerfile            # Build và chạy API Java
├── docker-compose.yml    # minio-api + minio + minio-init
├── init-buckets.sh
└── minio.ps1
```

## Chạy service

Từ thư mục này, mở PowerShell và chạy:

```powershell
.\minio.ps1 start
.\minio.ps1 status
```

Script build image API Java, tạo volume/network nếu chưa có, chờ MinIO healthy, chạy init bucket rồi khởi động API và chờ health check. Lệnh `start` khởi động cả API Java lẫn MinIO Server trong Docker. Muốn đổi cấu hình, copy `.env.example` thành `.env` nếu chưa có file `.env`, sửa giá trị rồi chạy lại `start`. Lần đầu Docker tải image Maven/JRE và dependencies để build API.

Console: <http://localhost:9001>. Tài khoản local mặc định: `minioadmin` / `minioadmin123`.

API: <http://localhost:8087/actuator/health>. Các endpoint thao tác file yêu cầu service key; hợp đồng API và scope từng caller được mô tả trong [API.md](API.md).

| Lệnh | Công việc |
| --- | --- |
| `.\minio.ps1 start` | Build và khởi động API Java, MinIO Server, khởi tạo bucket |
| `.\minio.ps1 stop` | Dừng API Java và MinIO Server |
| `.\minio.ps1 restart` | Khởi động lại cả hai phần và áp dụng cấu hình |
| `.\minio.ps1 init` | Áp dụng lại danh sách bucket và đồng bộ cấu hình API |
| `.\minio.ps1 status` | Xem trạng thái container |
| `.\minio.ps1 logs` | Xem 100 dòng log gần nhất của API và S3 Server |
| `.\minio.ps1 down` | Xóa container của project MinIO, giữ dữ liệu và network |

Không cần gõ `mc alias` bằng tay; init script tự kết nối tới MinIO. Bucket init được chạy bằng container dùng một lần và tự xóa sau khi hoàn thành.

## Cấu hình

| Biến | Mặc định |
| --- | --- |
| `MINIO_ACCESS_KEY` | `minioadmin` |
| `MINIO_SECRET_KEY` | `minioadmin123` |
| `MINIO_API_PORT` | `9000` |
| `MINIO_CONSOLE_PORT` | `9001` |
| `MINIO_HTTP_PORT` | `8087` |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` |
| `MINIO_DATA_VOLUME` | `car-rental-minio-data` |
| `MINIO_NETWORK` | `car-rental-storage` |
| `MINIO_PUBLIC_BUCKETS` | `vehicle-images` |
| `MINIO_PRIVATE_BUCKETS` | `vehicle-documents` |
| `FILE_SERVICE_VEHICLE_API_KEY` | `local-vehicle-file-service-key` |
| `FILE_SERVICE_CUSTOMER_API_KEY` | `local-customer-file-service-key` |
| `MINIO_EUREKA_ENABLED` | `false` trong Compose |

Danh sách bucket phân cách bằng dấu phẩy, không có khoảng trắng. Có thể để trống một danh sách. Một bucket không được có mặt trong cả hai danh sách. Bucket public chỉ cho đọc object, không cho anonymous liệt kê bucket. Bucket private không cấp quyền anonymous.

Để thêm nơi chứa ảnh customer, cấu hình `MINIO_PRIVATE_BUCKETS=vehicle-documents,customer-images` rồi chạy `init`. Customer-service cần tự quản lý API upload và metadata của mình; tạo bucket không tự bổ sung API customer.

Volume và network được khai báo `external` và được script quản lý riêng. Vì vậy thao tác xóa Compose project không xóa bucket/file, cũng không xóa network dùng chung với các consumer.

## API Spring Boot trong cùng module

Compose đã nối `minio-api` với `minio` trên network storage, dùng `MINIO_ENDPOINT=http://minio:9000`. Credentials được truyền từ cùng file `.env`; public endpoint dùng địa chỉ browser truy cập được.

Nếu muốn debug Java bằng IDE, dừng riêng `minio-api` để nhường cổng 8087 và chạy class `crm.ms.files.MinioServiceApplication`. Tiến trình Java trên máy dùng:

```text
MINIO_ENDPOINT=http://localhost:9000
MINIO_PUBLIC_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
```

Endpoint, credentials và tên bucket phải khớp với MinIO. Khi chạy Java bằng IDE, đặt các biến trong Run Configuration hoặc environment; mẫu nằm trong `java.env.example`. Khi chạy qua Compose, script và `.env` quản lý cả API lẫn MinIO Server.

Vehicle-service gọi HTTP tới API trong module này tại `http://localhost:8087` qua port `FileStorage` và adapter `HttpFileStorage`. Biến client `FILE_SERVICE_URL` và các endpoint `/internal/v1/files/**` được giữ để tương thích. Credentials MinIO chỉ nằm ở module này; các service nghiệp vụ dùng service key riêng.

Nếu vehicle/customer chạy trong Compose project khác, gắn service đó vào network external `car-rental-storage` và đặt `FILE_SERVICE_URL=http://minio-api:8087`. `MINIO_PUBLIC_ENDPOINT` vẫn là địa chỉ trình duyệt truy cập được. API Java có thể đăng ký Eureka khi bật `MINIO_EUREKA_ENABLED`; MinIO S3 Server không đăng ký Eureka.

## Dùng lại dữ liệu từ Compose backend cũ

Compose cũ ở thư mục backend mặc định tạo volume `car_rental_microservice_project_be_minio-data`. Để dùng lại dữ liệu, đặt dòng sau trong `.env` ở thư mục này:

```text
MINIO_DATA_VOLUME=car_rental_microservice_project_be_minio-data
```

Giữ credentials đã dùng với MinIO cũ. Dừng và xóa riêng hai container MinIO cũ trước khi khởi động project mới; không chạy hai MinIO server đồng thời trên cùng volume.

Với tên container mặc định của bản Compose cũ:

```powershell
docker stop car_rental_microservice_project_be-minio-1 car_rental_microservice_project_be-minio-init-1
docker rm car_rental_microservice_project_be-minio-1 car_rental_microservice_project_be-minio-init-1
.\minio.ps1 start
```

Các lệnh trên giữ nguyên volume cũ và không thao tác container của service khác. Việc đổi `MINIO_DATA_VOLUME` chọn volume cần sử dụng, không tự sao chép dữ liệu sang volume khác.
