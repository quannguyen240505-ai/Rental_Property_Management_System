# Payment Service

## Phạm vi
Khung Spring Boot kết nối MySQL, chạy Flyway và cung cấp health.
Chưa có API thanh toán, xác thực JWT, callback hoặc tích hợp Billing.

## Cấu hình
- Java 25.
- MySQL 8.0.16 trở lên.
- Cổng mặc định: 8089; đổi bằng PAYMENT_PORT.
- Biến bắt buộc: MYSQL_USER, MYSQL_PASSWORD.
- Database mặc định: rental_payment_db.
- Có thể đổi kết nối bằng SPRING_DATASOURCE_URL.

## Database mới
Tạo database rỗng trong MySQL:

    CREATE DATABASE rental_payment_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

Khởi động com.rental.payment.PaymentServiceApplication.
Flyway tự áp dụng V1__create_payment_tables.sql.

Không import database/payment_schema.sql trước khi chạy
Flyway trên database mới.

## Database đã có bảng hoặc dữ liệu
Sao lưu và đối chiếu schema với V1 trước khi lập kế hoạch baseline.
Giữ baseline-on-migrate=false.
Chưa kiểm thử baseline database cũ.

## Migration
V1 tạo 7 bảng nghiệp vụ và Flyway tạo bảng lịch sử.
Không sửa migration đã áp dụng; thay đổi tiếp theo dùng V2.
Giữ clean-disabled=true.

## Build và kiểm tra
- Maven: clean package.
- Đã build và đóng gói JAR thành công.
- Chưa có automated test.
- Đã áp dụng V1 trên database MySQL rỗng.
- Khởi động lại thành công: No migration necessary.
- Kiểm tra health: GET http://localhost:8089/actuator/health.