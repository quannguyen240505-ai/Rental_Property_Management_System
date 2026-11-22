# Billing Service

Quản lý chỉ số điện nước, tạo hóa đơn nháp và phát hành hóa đơn.

## Cấu hình chạy

- Java 25, Maven và MySQL 8.
- Schema phải có sẵn các bảng vì Hibernate dùng ddl-auto=validate.
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
