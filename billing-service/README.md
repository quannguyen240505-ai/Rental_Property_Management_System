# Billing Service

## Cấu hình
- Java 25, MySQL 8.0.16 trở lên.
- Cổng mặc định: 8084.
- Biến môi trường bắt buộc: MYSQL_USER, MYSQL_PASSWORD,
  JWT_SECRET, JWT_ISSUER.
- JWT_SECRET tối thiểu 32 byte.
- Có thể dùng SPRING_DATASOURCE_URL để chọn database.
- Khi chạy độc lập, đặt EUREKA_CLIENT_ENABLED=false.

## Database mới
1. Tạo database rỗng:

   CREATE DATABASE rental_billing_db
   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

2. Cấu hình kết nối rồi khởi động BillingServiceApplication.
3. Flyway tự chạy V1__create_billing_tables.sql.
4. Hibernate kiểm tra schema với ddl-auto=validate.

Không import database/billing_schema.sql hoặc
database/review_fixes_migration.sql trước khi chạy Flyway
trên database mới.

Khởi động lại: Flyway kiểm tra migration đã áp dụng,
không chạy lại V1.

## Database đã có bảng hoặc dữ liệu
Không chạy quy trình database mới trực tiếp trên database cũ.

Trước khi đưa database cũ vào Flyway:
1. Sao lưu database.
2. Đối chiếu cấu trúc, index và constraint với V1.
3. Xử lý các khác biệt trước khi đánh dấu baseline.
4. Chỉ baseline phiên bản 1 khi đã xác nhận schema tương ứng V1.

Giữ baseline-on-migrate=false trong cấu hình chung.
Không tự tạo hoặc sửa bảng flyway_schema_history.
Quy trình baseline database cũ chưa được kiểm thử trong thay đổi này.

Có thể dùng database rỗng riêng qua SPRING_DATASOURCE_URL
để kiểm thử mà không tác động database cũ.

## Quy tắc migration
- Không sửa V1 sau khi đã áp dụng.
- Thay đổi tiếp theo dùng V2, V3... theo thứ tự.
- Giữ clean-disabled=true để chặn lệnh Flyway clean.
- Hibernate chỉ validate, không tự tạo hoặc sửa bảng.

## Kiểm tra
- GET /actuator/health: mong đợi status UP.
- Maven clean test: 22 test đã đạt.
- Đã kiểm thử V1 trên database MySQL rỗng.
- Đã kiểm thử khởi động lại: No migration necessary.

Migration tạo bảng payment_applications để lưu các khoản
thanh toán áp dụng vào hóa đơn. Điều này chưa đồng nghĩa
API thanh toán hoặc Payment Service đã được triển khai.
