# Tích hợp MinIO cho hồ sơ xe

Vehicle-service quản lý metadata và quyền truy cập ảnh, giấy tờ của xe. API Spring Boot trong module `infras/minio-service` xử lý lưu/xóa/cấp URL thông qua MinIO Server. Mã Java và cấu hình Docker đã nằm chung module này. MinIO giữ nội dung tệp; MySQL chỉ giữ object key cùng tên gốc, MIME, dung lượng và người tải lên. Ảnh có URL công khai, giấy tờ chỉ nhận URL GET ký tạm thời trong 10 phút.

## Ranh giới kiến trúc

```text
React → API Gateway → VehicleMediaController → VehicleMediaService
                                                   │
                                   ┌───────────────┴────────────────┐
                                   ▼                                ▼
                       VehicleImage/DocumentRepository        FileStorage port
                                   │                                │
                                   ▼                                ▼
                                 MySQL                        HttpFileStorage
                                                                    │
                                                                    ▼
                                                       MinIO service API :8087
                                                                    │
                                                                    ▼
                                                            MinioFileStorage
                                                                    │
                                                                    ▼
                                                                  MinIO
```

Phần tích hợp giữ cấu trúc service/repository/entity sẵn có. `FileStorage`, `MediaFile` và `MediaStorageSettings` thuộc tầng service, không chứa kiểu của MinIO hay multipart HTTP. Controller chuyển `MultipartFile` thành stream; `HttpFileStorage` gửi stream tới API nội bộ của file-service. SDK và credentials MinIO chỉ nằm trong module `infras/minio-service`. File-service có port thuần Java `ObjectStorage`, use case và adapter MinIO riêng; thay nhà cung cấp storage chỉ tác động adapter đó.

Ảnh và giấy tờ vẫn thuộc aggregate xe, được xử lý trong vehicle-service. Gateway giữ trách nhiệm xác thực JWT và loại bỏ header định danh do client gửi trước khi gắn định danh đã xác thực. API lưu file dùng service key riêng và giới hạn bucket/prefix theo caller; không expose API nội bộ qua gateway. Các service khác không truy cập trực tiếp bảng media của vehicle-service. Chi tiết API chung và scope customer nằm trong [API của MinIO service](../car_rental_microservice_project_be/infras/minio-service/API.md).

## Khởi chạy local

Cần Java 21, Maven 3.9+, Node.js 22.12+ cho các service nghiệp vụ/frontend và Docker Desktop với Linux engine. Module `infras/minio-service` chứa cả Java API và MinIO Server trong Compose project `car-rental-minio`. Một lệnh khởi động cả hai phần, từ thư mục backend:

```powershell
.\infras\minio-service\minio.ps1 start
.\infras\minio-service\minio.ps1 status
```

Console ở <http://localhost:9001>; S3 API ở <http://localhost:9000>. Thông tin local mặc định là `minioadmin` / `minioadmin123`. Compose tạo `vehicle-images` với quyền anonymous `GetObject` và `vehicle-documents` private. Init chạy lại được và không xóa dữ liệu. Quyền công khai không cho phép liệt kê bucket.

MinIO có lệnh start/stop/restart/down riêng và dùng volume/network external. Ví dụ `.\infras\minio-service\minio.ps1 down` chỉ xóa container thuộc project MinIO, giữ nguyên file và các container của service khác. Script tự tạo volume/network khi cần và tự chạy init bucket. Chi tiết vận hành, thêm bucket customer và dùng lại dữ liệu cũ nằm trong [README của MinIO service](../car_rental_microservice_project_be/infras/minio-service/README.md).

Image upstream trong kế hoạch không còn truy cập được. Compose ghim một bản build MinIO theo digest từ [coollabsio/minio](https://github.com/coollabsio/minio), chứa cả server và `mc`. Có thể đặt `MINIO_IMAGE` để dùng image MinIO đã kiểm duyệt trong registry của tổ chức. Integration test dùng cùng digest; biến `MINIO_TEST_IMAGE` cho phép ghi đè image test.

Backend đọc biến môi trường của tiến trình Java. File `infras/minio-service/.env` cấu hình riêng cho stack MinIO; muốn đổi cấu hình Java, đặt biến trong shell hoặc Run Configuration của IDE. Các biến mẫu cho consumer Java nằm trong `car_rental_microservice_project_be/.env.example`; mẫu cho MinIO server nằm trong `infras/minio-service/.env.example`.

MySQL cần database `vehicle_db` và các bản ghi xe đã tồn tại. Module CRUD xe còn là khung của repository gốc; tích hợp này bổ sung các API media cho xe có sẵn. Nếu dùng schema cũ, thực hiện migration ở phần dưới trước khi khởi động service.

Sau khi stack MinIO healthy, khởi chạy Eureka, vehicle-service và gateway theo thứ tự, mỗi lệnh trong một terminal riêng từ thư mục backend. API lưu file đã chạy trong Compose, không chạy thêm một ứng dụng ở cổng 8087:

```powershell
mvn -pl infras/eureka-service spring-boot:run
mvn -pl services/vehicle-service spring-boot:run
mvn -pl infras/api-gateway spring-boot:run
```

Gateway dùng Keycloak issuer `http://localhost:8180/realms/car-rental` theo cấu hình có sẵn. Backend chỉ tin `X-User-ID` và `X-User-Roles` do gateway sinh ra; cổng vehicle-service phải nằm trong mạng nội bộ khi triển khai. Vai trò mặc định là `ADMIN` hoặc `STAFF`, so khớp nguyên tên, không phân biệt hoa thường. Đặt `VEHICLE_MEDIA_MANAGEMENT_ROLES` nếu realm đang dùng tên khác.

Trong frontend:

```powershell
Copy-Item .env.example .env
npm ci
npm run dev
```

Điền ba biến `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID` trong `.env` để bật đăng nhập. Client Keycloak phải là public client, bật Standard Flow và PKCE S256, đăng ký redirect URI `http://localhost:5173/*` cùng Web Origin `http://localhost:5173`. Tên role hiển thị nút quản lý được cấu hình bằng `VITE_MEDIA_MANAGEMENT_ROLES`, đồng bộ với backend. Cấu hình dựa trên [JavaScript adapter của Keycloak](https://www.keycloak.org/securing-apps/javascript-adapter).

Frontend giữ token trong bộ nhớ, refresh trước khi gọi API và gửi Bearer token đến gateway. Người chưa đăng nhập vẫn xem ảnh công khai. Nhập UUID của xe có sẵn để mở hồ sơ; tài khoản quản lý có form upload, nút xóa và quyền mở giấy tờ. Giấy tờ được mở bằng URL mới mỗi lần bấm, không lưu URL hết hạn vào state hay DB.

## Cấu hình storage phía vehicle-service

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `FILE_SERVICE_URL` | `http://localhost:8087` | Địa chỉ API file-service |
| `FILE_SERVICE_VEHICLE_API_KEY` | `local-vehicle-file-service-key` | Khóa caller local, giống phía file-service |
| `VEHICLE_IMAGE_BUCKET` | `vehicle-images` | Bucket ảnh công khai |
| `VEHICLE_DOCUMENT_BUCKET` | `vehicle-documents` | Bucket giấy tờ private |
| `VEHICLE_PRESIGN_EXPIRY` | `10m` | Thời hạn URL, không vượt giới hạn file-service |

Hai bucket phải khác nhau. Các biến tên bucket/thời hạn cũ `MINIO_IMAGE_BUCKET`, `MINIO_DOCUMENT_BUCKET`, `MINIO_PRESIGN_EXPIRY` được chấp nhận làm fallback. Danh sách bucket cần tạo trên MinIO server được cấu hình độc lập bằng `MINIO_PUBLIC_BUCKETS=vehicle-images` và `MINIO_PRIVATE_BUCKETS=vehicle-documents`; tên bucket và scope quyền ở file-service phải khớp.

File-service có hai MinIO client độc lập: ghi/xóa qua `MINIO_ENDPOINT`, ký URL bằng `MINIO_PUBLIC_ENDPOINT` với region cố định `us-east-1`. Nếu đưa file-service vào Docker, nối vào network external `car-rental-storage` và đặt `MINIO_ENDPOINT=http://minio:9000`; public endpoint vẫn là host trình duyệt truy cập được. Vehicle-service chỉ dùng `FILE_SERVICE_URL` và không cần credentials MinIO.

## Hợp đồng API

Mọi endpoint bên dưới có tiền tố `/api/v1/vehicles/{vehicleId}` và bọc kết quả bằng `ResponseApi` gồm `code`, `message`, `data`.

| Method | Đường dẫn | Quyền | Input |
| --- | --- | --- | --- |
| POST | `/images` | ADMIN/STAFF | Multipart: `file`, `imageType`, tùy chọn `isPrimary=false`, `sortOrder=0` |
| GET | `/images` | Công khai | Danh sách theo sortOrder, createdAt, id |
| DELETE | `/images/{imageId}` | ADMIN/STAFF | ID thuộc đúng xe |
| POST | `/documents` | ADMIN/STAFF | Multipart: `file`, `docType`, tùy chọn `docNumber`, `issueDate`, `expiryDate` |
| GET | `/documents` | ADMIN/STAFF | Danh sách giấy tờ, không có URL hay object key |
| GET | `/documents/{documentId}/url` | ADMIN/STAFF | Trả `{url, expiresAt}`, header `Cache-Control: no-store` |
| DELETE | `/documents/{documentId}` | ADMIN/STAFF | ID thuộc đúng xe |

`imageType`: `EXTERIOR_FRONT`, `EXTERIOR_REAR`, `EXTERIOR_SIDE`, `INTERIOR`, `DASHBOARD`, `TRUNK`, `LICENSE_PLATE`, `OTHER`.

`docType`: `REGISTRATION`, `INSURANCE`, `INSPECTION_CERT`, `ROAD_TAX`, `WARRANTY`, `OTHER`. Ngày theo ISO `yyyy-MM-dd`; ngày hết hạn không được trước ngày cấp. File mới có `isVerified=false`; upload không cho phép tự xác minh giấy tờ.

Mỗi tệp tối đa 10 MiB, cả multipart request tối đa 12 MiB. Ảnh hỗ trợ JPEG, PNG, WebP; giấy tờ hỗ trợ thêm PDF. Backend kiểm tra MIME và chữ ký đầu file, sinh đuôi theo MIME, không dùng tên người dùng để sinh key. Tên gốc chỉ giữ làm metadata, được bỏ đường dẫn và ký tự điều khiển.

Upload thành công trả 201; danh sách, URL và xóa trả 200. Lỗi đầu vào trả 400, thiếu xác thực 401, thiếu quyền 403, xe đã xóa/không tồn tại hoặc media sai xe 404, quá dung lượng 413, lỗi MinIO 503.

Ví dụ upload qua gateway bằng token tài khoản quản lý, từ PowerShell:

```powershell
$vehicleId = 'UUID-XE-CO-SAN'
$token = 'ACCESS-TOKEN-KEYCLOAK'
curl.exe -X POST "http://localhost:8080/api/v1/vehicles/$vehicleId/images" `
  -H "Authorization: Bearer $token" `
  -F 'file=@C:/path/to/car.png' -F 'imageType=EXTERIOR_FRONT' -F 'isPrimary=true'
curl.exe "http://localhost:8080/api/v1/vehicles/$vehicleId/images"
curl.exe "http://localhost:8080/api/v1/vehicles/$vehicleId/documents"
```

Lệnh cuối phải trả 401 khi thiếu token. URL ảnh trong response mở trực tiếp không cần token. Các lệnh frontend gửi FormData không tự đặt Content-Type để Axios và browser tạo multipart boundary.

## Transaction và lỗi giữa DB với storage

Upload xác nhận xe còn hoạt động, kiểm tra file và gửi object qua file-service trước khi lưu metadata. Object key có dạng `vehicles/{vehicleId}/images/{uuid}.png` hoặc `vehicles/{vehicleId}/documents/{uuid}.pdf`. Callback xóa bù được đăng ký trước khi gửi HTTP để xử lý cả rollback DB và phản hồi upload bị mất. Callback bao phủ lỗi chỉ xảy ra ở thời điểm commit. Row lock trên xe tuần tự hóa thay đổi ảnh đại diện để hai request đồng thời không tạo hai ảnh primary.

Khi xóa, metadata phải commit thành công trước khi xóa object. Rollback giữ cả metadata lẫn file. Nếu MinIO lỗi sau khi DB đã commit, request vẫn thành công và log ghi bucket/key cần dọn lại. Xử lý này không phải transaction phân tán: khi tiến trình bị dừng đột ngột giữa upload với commit, hoặc dọn object thất bại, cần đối soát object dư. Chưa có worker retry tự động trong phạm vi kế hoạch.

## Migration schema cũ

Script một lần nằm ở `services/vehicle-service/src/main/resources/db/manual/001_vehicle_media_object_keys.sql`. Dừng vehicle-service và backup hai bảng media trước khi chạy. Các câu SELECT đầu script phải trả về rỗng; nếu có URL ngoài hệ thống, cần copy nội dung vào bucket và ánh xạ key trước. Với tên bucket tùy chỉnh, sửa phần tách URL trong script theo tên bucket cũ.

Script đổi tên `file_url` sang `object_key`, thêm MIME/người tạo và chuyển URL MinIO cũ thành key. Dữ liệu được giữ lại. MySQL DDL tự commit; script chỉ chạy một lần trên schema gốc. Database mới để Hibernate tạo schema mới, không chạy script này. Không dựa vào `ddl-auto=update` để thực hiện đổi tên cột trên DB cũ.

## Kiểm thử

Từ backend:

```powershell
mvn -pl infras/minio-service,services/vehicle-service,infras/api-gateway -am clean verify
docker compose -f infras/minio-service/docker-compose.yml config --quiet
```

Unit test kiểm tra whitelist/chữ ký file, bù object khi DB/HTTP lỗi, vòng đời commit/rollback và HTTP adapter của vehicle-service. Test persistence dùng H2 để kiểm tra metadata, primary image, rollback upload và rollback/commit xóa. Test gateway kiểm tra giấy tờ trả 401, ảnh công khai và header giả mạo bị loại bỏ. Testcontainers trong module file-service chạy cả adapter MinIO và HTTP API thật để put, GET bằng URL ký, chặn GET anonymous, scope caller và delete; tự skip khi Docker không khả dụng.

Từ frontend:

```powershell
npm test
npm run lint
npm run build
```

Test HTTP frontend gửi FormData thật tới HTTP server tạm để kiểm tra boundary, Bearer token, metadata và endpoint lấy URL giấy tờ.
