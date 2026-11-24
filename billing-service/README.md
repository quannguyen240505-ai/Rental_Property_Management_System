# Billing Service

Quản lý chỉ số điện nước, tạo hóa đơn nháp và phát hành hóa đơn.

## Cấu hình chạy

- Java 25, Maven và MySQL 8.0.16 trở lên.
- Flyway tạo bảng trên database mới trước khi Hibernate kiểm tra schema với ddl-auto=validate.
- Cổng mặc định: 8084. Bộ kiểm thử local dùng BILLING_PORT=8087.

Các biến môi trường:

| Biến | Ý nghĩa |
|---|---|
| MYSQL_USER | Tài khoản MySQL |
| MYSQL_PASSWORD | Mật khẩu MySQL |
| SPRING_DATASOURCE_URL | Ghi đè JDBC URL nếu cần |
| BILLING_PORT | Cổng Billing |
| AUTH_BASE_URL | Địa chỉ Auth, mặc định http://localhost:8081 |
| AUTH_INTERNAL_SERVICE_KEY | Key nội bộ dùng chung với Auth |
| EUREKA_CLIENT_ENABLED | Đặt false khi chạy độc lập |

AUTH_INTERNAL_SERVICE_KEY phải khớp chính xác với Auth.
Dùng key ít nhất 32 UTF-8 bytes, không chứa khoảng trắng, tab hoặc xuống dòng.
Không lưu key thật, mật khẩu hoặc access token vào Git.

Billing không cần JWT_SECRET hoặc JWT_ISSUER.

Từ thư mục gốc repository, chạy:

    mvn -f billing-service/pom.xml spring-boot:run

Trong STS, có thể chọn Maven Build với base directory là billing-service
và Goals là spring-boot:run.

## Xác thực và phân quyền

Billing gọi POST /internal/auth/introspect tại Auth Service.
Request gửi X-Service-Key và JSON chứa token JWT gốc.

Billing dùng userId và role được Auth xác nhận.
Role OWNER được ánh xạ thành ROLE_OWNER.
Billing không tự xác minh chữ ký JWT hoặc truy cập database của Auth.
Mỗi request có Bearer token được kiểm tra lại qua Auth.

- 401: thiếu token, token sai, hết hạn hoặc bị thu hồi.
- 403: token hợp lệ nhưng không đủ quyền nghiệp vụ.
- 503 AUTH_SERVICE_UNAVAILABLE: không thể xác minh qua Auth, gồm lỗi kết nối,
  timeout, service key sai, lỗi HTTP hoặc response không hợp lệ.
- GET /actuator/health được truy cập công khai.

Khi xác thực thất bại, request bị chặn trước xử lý nghiệp vụ.

## Giới hạn hiện tại

Các API hóa đơn và chỉ số điện nước hiện chỉ dành cho OWNER.
Chưa hỗ trợ quyền STAFF theo khu hoặc CUSTOMER/TENANT theo dữ liệu sở hữu.

propertyId, roomId, contractId, tenantUserId và unitPrice trong request
chưa được đối chiếu với Property/Rental hoặc bảng giá.
Validation dữ liệu đầu vào không thay thế việc kiểm tra quyền và dữ liệu liên service.
Cần bổ sung tích hợp trước khi mở rộng quyền sử dụng.

## Kiểm thử

Từ thư mục gốc repository:

    mvn -f billing-service/pom.xml clean test

Các tình huống cần kiểm tra:
- OWNER hợp lệ truy cập được API.
- Token đã logout bị từ chối với 401.
- STAFF/CUSTOMER bị từ chối với 403 ở chức năng chỉ dành cho OWNER.
- Auth không khả dụng trả 503 và không thực hiện ghi dữ liệu nghiệp vụ.

## Database và Flyway

### Database mới

1. Tạo database MySQL rỗng, ví dụ:

       CREATE DATABASE rental_billing_db
       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

2. Cấu hình MYSQL_USER, MYSQL_PASSWORD và SPRING_DATASOURCE_URL nếu cần.
3. Khởi động Billing Service.
4. Flyway chạy V1__create_billing_tables.sql để tạo bảng.
5. Hibernate kiểm tra cấu trúc bằng ddl-auto=validate.
6. Khởi động lại: Flyway xác nhận schema đã cập nhật, không chạy lại V1.

Không import database/billing_schema.sql hoặc review_fixes_migration.sql
trước khi chạy Flyway trên database mới.

### Database đã có dữ liệu

- Sao lưu và đối chiếu schema với V1 trước khi lập kế hoạch baseline.
- Giữ baseline-on-migrate=false trong cấu hình chung.
- Chỉ baseline phiên bản 1 sau khi xác nhận schema tương ứng V1.
- Quy trình baseline database cũ chưa được kiểm thử trong thay đổi này.
- Không tự sửa bảng flyway_schema_history.

### Quy tắc migration

- Không sửa V1 sau khi đã áp dụng.
- Thay đổi tiếp theo dùng V2, V3... theo thứ tự.
- Giữ clean-disabled=true và validate-on-migrate=true.
- Dùng database rỗng riêng để kiểm thử migration.


## Kiểm thử đồng thời trên MySQL

Ghi chỉ số được tuần tự hóa theo (room_id, utility_type) bằng khóa InnoDB
trong cùng transaction, kể cả khi chưa có chỉ số đầu tiên.
Transaction ghi dùng READ_COMMITTED để đọc lại dữ liệu đã commit sau khi chờ khóa.
Khóa được giải phóng khi commit hoặc rollback; điện và nước có khóa riêng.
V2 tạo utility_meter_write_locks. Giữ nguyên V1 đã áp dụng.

### Chạy integration test

1. Tạo database MySQL 8.0.16+ rỗng riêng, tên bắt đầu bằng rental_billing_it_, ví dụ:

       CREATE DATABASE rental_billing_it_20261123
       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

2. Đặt biến môi trường trong cấu hình Maven kiểm thử:

   - BILLING_IT_DB_URL: jdbc:mysql://localhost:3306/rental_billing_it_20261123?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
   - MYSQL_USER: tài khoản MySQL có quyền tạo/sửa bảng trong database test.
   - MYSQL_PASSWORD: mật khẩu MySQL, chỉ đặt ở môi trường local.

3. Tại module billing-service, chạy Goals:

       clean verify -Pmysql-it

   Hoặc từ thư mục gốc repository:

       mvn -f billing-service/pom.xml clean verify -Pmysql-it

Profile chạy 33 test hiện có qua Surefire và 8 integration test qua Failsafe.
Chỉ ghi nhận PASS sau khi cả hai nhóm đạt và Maven trả BUILD SUCCESS.
Chạy clean verify mà không có -Pmysql-it chưa xác nhận nhóm MySQL.

### Phạm vi 8 integration test

- Hai kỳ khác nhau: chờ transaction trước commit, từ chối chỉ số đầu kỳ cũ.
- Hai kỳ liên tục hợp lệ: đọc chỉ số vừa commit và lưu thành công.
- Công tơ chưa có dữ liệu vẫn được tuần tự hóa.
- Trùng kỳ trả 409 sau khi transaction trước hoàn tất.
- Rollback giải phóng khóa và không giữ lại bản ghi đầu tiên.
- Nước không bị khóa bởi transaction ghi điện cùng phòng.
- Hai yêu cầu tạo hóa đơn trùng: một thành công, một 409, chỉ lưu một hóa đơn.
- Lỗi CHECK thật trên MySQL khi lưu dòng hóa đơn rollback toàn bộ hóa đơn.

Test dùng database local riêng có tên rental_billing_it_...; guard kiểm tra trước
khi Flyway khởi chạy. Dữ liệu fixture dùng UUID riêng và được dọn theo fixture.
Test rollback tạo rồi xóa một CHECK tạm trong database test.
Auth HTTP được kiểm tra riêng trong BillingAuthSecurityTest; nhóm MySQL gọi
service với Authentication OWNER của fixture, không gọi Auth hoặc sửa auth_db.
