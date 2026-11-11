# Quản lý đội xe

Mở [trang quản lý xe](http://localhost:5173/vehicles) và đăng nhập bằng tài khoản Keycloak có vai trò `ADMIN` hoặc `STAFF`. Trên DB trống, mở [Danh mục xe](http://localhost:5173/vehicles/catalog), tạo ít nhất một loại và một mẫu, sau đó dùng **Thêm xe**. Trang chi tiết có nút **Mở ảnh và giấy tờ** cho xe đang xem. [Trang tra cứu ảnh công khai](http://localhost:5173/) nhận biển số đầy đủ của xe.

## Chạy local

1. Dùng Java 21, Maven 3.9+, Node.js 22.12+ và MySQL 8. Tạo database `vehicle_db` bằng `CREATE DATABASE IF NOT EXISTS vehicle_db CHARACTER SET utf8mb4;`. Đặt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho tiến trình vehicle-service theo kết nối MySQL của bạn. Hibernate tạo bảng trên DB trống. Các biến mẫu nằm trong `car_rental_microservice_project_be/.env.example`.
2. Khởi động Keycloak ở cổng 8180 và tạo realm `car-rental`. Tạo client OpenID Connect `car-rental-web` với **Client authentication** tắt, **Standard flow** bật, PKCE `S256`, **Valid redirect URIs** là `http://localhost:5173/*` và **Web origins** là `http://localhost:5173`. Thêm `http://localhost:5173` vào **Valid post logout redirect URIs**. Tạo realm role `ADMIN` hoặc `STAFF` và gán cho tài khoản quản lý. Nếu dùng realm khác, đặt `KEYCLOAK_ISSUER_URI` và `KEYCLOAK_JWK_SET_URI` cho Gateway theo realm đó. [Tài liệu Keycloak JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter).
3. Từ `car_rental_microservice_project_be`, chạy MinIO nếu cần nghiệm thu media. Docker Desktop cần chạy Linux engine. [Cấu hình MinIO và bucket](minio-vehicle-media.md).

   ```powershell
   .\services\minio-service\minio.ps1 start
   ```

4. Từ thư mục backend, chạy ba lệnh sau trong ba terminal riêng. Eureka dùng cổng 8761, Gateway dùng cổng 8080. Với vehicle-service chạy cùng máy với Gateway, có thể đặt `SERVER_ADDRESS=127.0.0.1` và `EUREKA_INSTANCE_HOSTNAME=127.0.0.1` cho tiến trình vehicle-service để giới hạn truy cập cổng 8081.

   ```powershell
   mvn -pl infras/eureka-service spring-boot:run
   mvn -pl services/vehicle-service spring-boot:run
   mvn -pl infras/api-gateway spring-boot:run
   ```

5. Từ `car_rental_microservice_project_fe`, đặt các giá trị sau trong `.env`, rồi chạy `npm ci` và `npm run dev`.

   ```dotenv
   VITE_API_BASE_URL=http://localhost:8080
   VITE_KEYCLOAK_URL=http://localhost:8180
   VITE_KEYCLOAK_REALM=car-rental
   VITE_KEYCLOAK_CLIENT_ID=car-rental-web
   ```

Gateway kiểm tra JWT và thay header định danh do client gửi bằng subject và role từ token. Vehicle-service kiểm tra role và lưu subject làm người thực hiện. Giữ cổng vehicle-service trong mạng nội bộ sau Gateway vì service tin các header này. Media tiếp tục dùng `VEHICLE_MEDIA_MANAGEMENT_ROLES` ở backend và `VITE_MEDIA_MANAGEMENT_ROLES` ở frontend, mặc định là `ADMIN,STAFF`. GET ảnh vẫn công khai. Giấy tờ cần quyền media và được tải qua URL ký tạm thời.

## API quản trị

Các đường dẫn dưới đây có tiền tố `/api/v1/vehicles`. Dùng Bearer token Keycloak qua Gateway. Kết quả dùng `ResponseApi` với `code`, `message` và `data`.

| Method | Đường dẫn | Dữ liệu |
| --- | --- | --- |
| POST | `/` | `categoryId`, `modelId`, `vin`, `licensePlate`, `mileageKm` và thông tin tùy chọn. Trả 201. |
| GET | `/` | `q` tìm chuỗi con trong VIN hoặc biển số, `status`, `categoryId`, `modelId`, `page` từ 0 và `size` từ 1 đến 100. Mặc định `size=20`. |
| GET | `/{id}` | Chi tiết xe. |
| PUT | `/{id}` | Cùng các trường thông tin như POST. |
| DELETE | `/{id}` | Xóa mềm, trả 200. |
| PATCH | `/{id}/status` | `status` và `reason` tùy chọn, tối đa 500 ký tự. |
| GET | `/{id}/status-history` | Các lần đổi trạng thái, mới nhất trước. |
| GET, POST | `/categories` | Liệt kê hoặc tạo loại xe với `name` bắt buộc và `description` tùy chọn. |
| PUT, DELETE | `/categories/{id}` | Sửa loại xe, hoặc xóa khi không còn mẫu xe nào thuộc loại đó (nếu còn: 409). |
| GET, POST | `/models` | Liệt kê hoặc tạo mẫu xe với `brand`, `modelName`, `manufactureYear`, `fuelType`, `seatCount`, `transmission` và `engineCapacityCc` tùy chọn. |
| PUT, DELETE | `/models/{id}` | Sửa mẫu xe (đổi loại xe thì loại của các xe thuộc mẫu đó đổi theo), hoặc xóa khi chưa có xe nào dùng, kể cả xe đã xóa mềm (nếu có: 409). |

Gọi endpoint gốc bằng `/api/v1/vehicles` không có dấu gạch chéo cuối. Danh sách trả `data.content` và `data.page` gồm `number`, `size`, `totalElements`, `totalPages`.

## Quy tắc nghiệp vụ

- VIN và biển số được cắt khoảng trắng hai đầu và viết hoa. VIN có 17 chữ cái hoặc chữ số, trừ I, O, Q. Biển số tối đa 20 ký tự, có thể dùng dấu chấm, gạch nối hoặc khoảng trắng giữa các nhóm. Số km bắt buộc, không âm và có tối đa một chữ số thập phân. Giá thuê snapshot tùy chọn, không âm và có tối đa hai chữ số thập phân. Loại và mẫu xe phải tồn tại.
- VIN và biển số duy nhất ở MySQL, kể cả với xe đã xóa mềm. Trùng khóa, bước chuyển sai và xóa xe trong trạng thái bị chặn trả `409`. Dữ liệu sai định dạng trả `400`. Thiếu xác thực trả `401` và thiếu quyền trả `403`.
- Xe mới ở trạng thái `AVAILABLE`. Chỉ PATCH trạng thái ghi lịch sử, gồm trạng thái cũ, trạng thái mới, lý do nếu có, subject của người thực hiện và thời điểm. Gửi lại trạng thái đang có trả `200` và không sinh bản ghi mới. Bộ lọc `AVAILABLE` dùng trạng thái hiện tại của xe.
- Chỉ xóa mềm xe `AVAILABLE` hoặc `DECOMMISSIONED`. Xe bị ẩn khỏi danh sách. Chi tiết, lịch sử và API media theo UUID của xe đã xóa trả `404`. MySQL giữ xe và các bản ghi liên quan, MinIO giữ file.
- Loại xe chỉ có tên, mô tả và trạng thái hoạt động; không có trường phân hạng ở loại xe hoặc mẫu xe. Với DB cũ, áp dụng [migration xóa cột phân hạng](../services/vehicle-service/src/main/resources/db/manual/003_drop_vehicle_category_size_class.sql) theo hướng dẫn dưới đây. Với DB dùng schema media cũ, làm theo [migration media hiện có](minio-vehicle-media.md#migration-schema-cũ).

| Trạng thái hiện tại | Có thể chuyển sang |
| --- | --- |
| `AVAILABLE` | `RESERVED`, `IN_MAINTENANCE`, `DECOMMISSIONED` |
| `RESERVED` | `RENTED`, `AVAILABLE` |
| `RENTED` | `AVAILABLE` |
| `IN_MAINTENANCE` | `AVAILABLE` |
| `DECOMMISSIONED` | Trạng thái cuối |

## Cập nhật database cũ

1. Dừng vehicle-service và sao lưu bảng `vehicle_categories`.
2. Chọn database `vehicle_db`, chạy một lần script `services/vehicle-service/src/main/resources/db/manual/003_drop_vehicle_category_size_class.sql`. Script xóa cột phân hạng, giữ nguyên ID loại xe và các liên kết mẫu xe/xe. Hibernate `ddl-auto=update` không tự xóa cột cũ; cần chạy migration trước khi khởi động bản mới hoặc chạy seed.
3. Build và khởi động lại vehicle-service, booking-service; cập nhật frontend để dùng API đã bỏ phân hạng. Cơ chế tính giá theo `categoryId` giữ nguyên.

Database mới không chạy migration này: Hibernate tạo bảng theo entity mới. Script seed chỉ dùng cho database phát triển vì sẽ xóa dữ liệu đội xe hiện có.

## Kiểm thử

Từ backend, chạy `mvn -pl services/vehicle-service,infras/api-gateway -am test`. Từ frontend, chạy `npm test`, `npm run lint` và `npm run build`.

Để nghiệm thu trên trình duyệt, tạo danh mục và xe bằng tài khoản quản lý, tìm bằng biển số và VIN, lọc theo trạng thái, loại, mẫu rồi sửa xe. Chuyển `AVAILABLE → RESERVED → RENTED → AVAILABLE` và kiểm tra ba dòng lịch sử. Thử gửi lại cùng trạng thái và gửi bước chuyển sai qua API. Tải lên, mở, xóa ảnh và giấy tờ từ chi tiết xe. Sau khi xác nhận xóa mềm, tìm lại xe và mở URL chi tiết cũ. Dùng tài khoản không có vai trò quản lý để kiểm tra phản hồi `403` từ Gateway và vehicle-service.

Kết quả gồm 56 test vehicle-service, 16 test api-gateway và 7 test frontend đạt. Lint và production build frontend đạt.

Kịch bản Chrome chạy từ database MySQL 8.4 trống qua React, Keycloak, Gateway và Eureka. Kịch bản đã kiểm tra tạo danh mục, thêm, tìm, xem, sửa xe, phân trang, lịch sử trạng thái, xóa mềm và quyền truy cập. Hai POST đồng thời trùng VIN và hai POST đồng thời trùng biển số đều cho kết quả một `201` và một `409`. Ảnh được tải lên, đọc công khai và xóa. Giấy tờ được tải lên, tải xuống bằng URL ký và xóa. Đối chiếu MySQL và MinIO xác nhận xóa mềm giữ lại lịch sử, metadata và các tệp đã gắn vào xe.

Kịch bản tra cứu trên Chrome với MySQL local xác nhận nhập biển số mở ảnh của đúng xe. Xe đã xóa mềm trả thông báo không tìm thấy. Yêu cầu xem giấy tờ chưa xác thực trả `401`.
