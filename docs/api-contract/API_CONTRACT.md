# API CONTRACT — TOÀN NHÓM

**Dự án:** Rental Property Management System — SOA  
**Trạng thái:** Tài liệu thống nhất API toàn nhóm (bản dự thảo tích hợp, cập nhật 05/11/2026)  
**Base URL:** `http://localhost:8080` qua API Gateway  
**Quy ước:** Các endpoint bắt đầu bằng `/api/...`, **không sử dụng `/api/v1`**.

> **Phân biệt trạng thái triển khai:** `W1-CODE` = đã có mã nguồn trong gói tuần 1 của Quân (chưa xác nhận build/chạy tích hợp); `DESIGN` = hợp đồng API đề xuất cho các phần việc tiếp theo, **chưa có nghĩa đã được lập trình**. Mọi thay đổi phải được thống nhất giữa các service trước khi merge vào `dev`.

## 1. Phân chia service và người phụ trách

| Service / Thành phần | Phụ trách | Trách nhiệm |
|---|---|---|
| `service-registry` (Eureka) | Quân | Đăng ký, khám phá service; không có REST nghiệp vụ `/api` |
| `api-gateway` | Quân | Cổng 8080, routing, kiểm tra JWT ở các route riêng tư |
| `auth-service` | Quân | Login, người dùng, vai trò OWNER/STAFF/TENANT |
| `notification-service` | Quân | Thông báo trong website, tích hợp sự kiện RabbitMQ |
| `property-service` | Thiện | Khu trọ, phòng, giá/ảnh, tin công khai, STAFF theo khu, lịch xem phòng |
| `rental-service` | Sáng | Hồ sơ thuê, hợp đồng, cọc, gia hạn, trả phòng, quyết toán |
| `billing-service` | Quốc | Chỉ số điện/nước, hóa đơn, công nợ, thanh toán thủ công |
| `payment-service` | Quốc | Giao dịch và kết quả thanh toán mô phỏng qua sandbox/test |
| `frontend` (Angular) | Cả nhóm | Giao diện theo module, gọi API qua Gateway |

## 2. Quy ước chung

- Request/response mặc định `Content-Type: application/json`; endpoint tải ảnh dùng `multipart/form-data` nếu triển khai.
- Endpoint riêng tư dùng `Authorization: Bearer <accessToken>`.
- **Actor:** `GUEST` = chưa đăng nhập, chỉ xem phòng công khai và thông tin liên hệ; `OWNER` = duy nhất, toàn quyền; `STAFF` = tác nghiệp đúng khu được giao và lịch dẫn xem của mình; `TENANT` = chỉ xem/thao tác dữ liệu thuê của chính mình. Quyền OWNER bao gồm khả năng tác nghiệp STAFF.
- **Không có đăng ký public.** Tài khoản STAFF/TENANT do người có quyền cấp. Trong mã tuần 1, API tạo tài khoản chỉ dành cho OWNER; khả năng STAFF hợp lệ cấp TENANT chỉ mở sau khi bổ sung kiểm tra phạm vi khu/hợp đồng.
- GUEST **không đặt lịch trực tuyến, không giữ phòng, không tự ký hợp đồng**. Khách liên hệ OWNER ngoài website; OWNER ghi lịch xem và **chủ động chọn** STAFF, hoặc tự dẫn khách.
- ID trả về dùng UUID dạng chuỗi; ngày `yyyy-MM-dd`; tháng hóa đơn `yyyy-MM`; ngày giờ ISO 8601 có múi giờ (ưu tiên `2026-11-05T14:00:00+07:00`). Giá trị tiền là số nguyên VND, không dùng số thực để xử lý tiền.
- Phản hồi: `200` đọc/cập nhật, `201` tạo, `204` xóa/không có nội dung, `400` không hợp lệ, `401` chưa xác thực, `403` không có quyền, `404` không thấy tài nguyên hoặc không thuộc phạm vi, `409` xung đột dữ liệu/trạng thái. Mẫu lỗi chuẩn cần chốt chung trước tích hợp; chưa áp đặt một response wrapper mà code W1 chưa sử dụng.
- Phân quyền phải kiểm tra **trong từng service**, không chỉ dựa vào Gateway. OWNER có quyền tổng thể; STAFF chỉ thao tác `propertyId` được phân công; TENANT chỉ có dữ liệu liên kết đến `userId` lấy từ JWT (không tin `userId` client gửi trong yêu cầu `/me`).
- Một service không truy cập trực tiếp database service khác. Liên service qua REST/sự kiện, có kiểm tra nguồn gọi và xử lý lỗi/timeout.

### JWT (Auth Service)

- `sub`: UUID của tài khoản đăng nhập.
- `roles`: **mảng chuỗi**, ví dụ `["ROLE_OWNER"]`, `["ROLE_STAFF"]`, `["ROLE_TENANT"]`.
- Claim thêm: `iss`, `iat`, `exp`, `username` (theo mã tuần 1).
- JWT dùng chung issuer/secret giữa các service ở môi trường demo; thay bằng cơ chế quản lý khóa an toàn trước triển khai thật.
- `/actuator/health` là endpoint kỹ thuật, **không** thay thế API nghiệp vụ.

## 3. Auth / User — Quân (`auth-service`)

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Đăng nhập, nhận access token | W1-CODE |
| `GET` | `/api/users/me` | JWT | Hồ sơ bản thân, không trả `passwordHash` | W1-CODE |
| `GET` | `/api/users` | OWNER | Danh sách tài khoản | W1-CODE |
| `GET` | `/api/users/staff` | OWNER | Danh sách tài khoản STAFF | W1-CODE |
| `POST` | `/api/users` | OWNER | Tạo STAFF hoặc TENANT, không tạo OWNER | W1-CODE |
| `PATCH` | `/api/users/{id}/status` | OWNER | Khóa/mở khóa tài khoản | W1-CODE |

**Request mẫu**

```json
POST /api/auth/login
{"username":"owner","password":"<password>"}
```

**Response mẫu** (cấu trúc theo mã tuần 1):

```json
{"accessToken":"<jwt>","tokenType":"Bearer","expiresAt":"2026-11-05T18:00:00Z","role":"OWNER","userId":"<uuid>"}
```

Tạo tài khoản: `POST /api/users` body `{"username":"staff1","password":"<strong-password>","fullName":"Nhân viên A","email":"staff@example.com","phone":"0900000000","role":"STAFF"}`. Đổi trạng thái: `{"status":"LOCKED"}` hoặc `{"status":"ACTIVE"}`.

## 4. Property / Room / Viewing — Thiện (`property-service`)

### 4.1. Khu trọ, phòng và website công khai

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `GET` | `/api/public/rooms` | Public | Danh sách phòng `AVAILABLE` và đã công khai, hỗ trợ lọc/phân trang | DESIGN |
| `GET` | `/api/public/rooms/{id}` | Public | Chi tiết, ảnh, giá, tiện ích, liên hệ OWNER của phòng còn công khai | DESIGN |
| `GET` | `/api/properties` | OWNER/STAFF theo khu | Danh sách khu trọ trong phạm vi | DESIGN |
| `POST` | `/api/properties` | OWNER | Tạo khu trọ | DESIGN |
| `PATCH` | `/api/properties/{id}` | OWNER | Sửa thông tin khu trọ | DESIGN |
| `GET` | `/api/rooms` | OWNER/STAFF theo khu | Danh sách phòng quản trị | DESIGN |
| `POST` | `/api/rooms` | OWNER/STAFF theo khu | Tạo phòng trong khu được phép | DESIGN |
| `GET` | `/api/rooms/{id}` | OWNER/STAFF theo khu | Chi tiết phòng quản trị | DESIGN |
| `PATCH` | `/api/rooms/{id}` | OWNER/STAFF theo khu | Cập nhật thông tin/trạng thái phòng theo quy tắc | DESIGN |
| `PATCH` | `/api/rooms/{id}/publication` | OWNER | Đăng/ẩn thông tin phòng trên website | DESIGN |
| `POST` | `/api/rooms/{id}/images` | OWNER/STAFF theo khu | Tải ảnh phòng có kiểm tra dung lượng/loại tệp | DESIGN |
| `GET` | `/api/properties/{id}/pricing` | OWNER/STAFF theo khu | Xem đơn giá áp dụng | DESIGN |
| `PUT` | `/api/properties/{id}/pricing` | OWNER | Cấu hình giá thuê tham chiếu, điện, nước, phí | DESIGN |

**Quy tắc:** Chỉ phòng `AVAILABLE` và được OWNER công khai mới xuất hiện với GUEST. Phòng `OCCUPIED`, `MAINTENANCE` hoặc `INACTIVE` không được hiển thị là phòng còn trống; giá hóa đơn đã phát hành phải dùng snapshot, không truy vấn giá hiện tại để tính lại.

### 4.2. STAFF theo khu và lịch xem phòng

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `GET` | `/api/properties/{id}/staff` | OWNER | STAFF đang phụ trách khu | DESIGN |
| `PUT` | `/api/properties/{id}/staff/{staffId}` | OWNER | Giao STAFF cho khu | DESIGN |
| `DELETE` | `/api/properties/{id}/staff/{staffId}` | OWNER | Thu hồi quyền theo khu | DESIGN |
| `GET` | `/api/viewing-appointments/available-staff?propertyId={id}&startAt={time}&endAt={time}` | OWNER | Gợi ý STAFF `ACTIVE`, đúng khu, không trùng lịch | DESIGN |
| `POST` | `/api/viewing-appointments` | OWNER | OWNER ghi lịch sau khi khách liên hệ ngoài website | DESIGN |
| `GET` | `/api/viewing-appointments` | OWNER/STAFF được giao | OWNER thấy toàn bộ, STAFF chỉ thấy lịch của mình | DESIGN |
| `GET` | `/api/viewing-appointments/{id}` | OWNER/STAFF được giao | Chi tiết lịch | DESIGN |
| `PATCH` | `/api/viewing-appointments/{id}/assignment` | OWNER | Chọn/đổi STAFF hoặc OWNER tự dẫn | DESIGN |
| `PATCH` | `/api/viewing-appointments/{id}/result` | OWNER/STAFF được giao | Kết quả `COMPLETED`/`CANCELLED`, ghi chú | DESIGN |

**Mẫu tạo lịch (đề xuất):**

```json
{"roomId":"<uuid>","guestName":"Khách A","guestPhone":"0900000000","startAt":"2026-11-08T09:00:00+07:00","endAt":"2026-11-08T09:30:00+07:00","note":"Liên hệ qua điện thoại"}
```

Không có endpoint `POST /api/public/viewing-appointments` hoặc endpoint `reservations` cho GUEST trong SRS hiện tại. Lịch xem không tạo trạng thái giữ phòng.

## 5. Rental / Contract — Sáng (`rental-service`)

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `POST` | `/api/tenant-profiles` | OWNER/STAFF theo khu | Lập hồ sơ người thuê, người ở cùng | DESIGN |
| `GET` | `/api/tenant-profiles/{id}` | OWNER/STAFF theo khu | Xem hồ sơ thuê | DESIGN |
| `POST` | `/api/contracts` | OWNER/STAFF theo khu | Lập hợp đồng `DRAFT` cho phòng | DESIGN |
| `GET` | `/api/contracts` | OWNER/STAFF theo khu | Danh sách hợp đồng trong phạm vi | DESIGN |
| `GET` | `/api/contracts/{id}` | OWNER/STAFF theo khu; TENANT sở hữu | Chi tiết hợp đồng theo quyền | DESIGN |
| `GET` | `/api/contracts/me` | TENANT | Danh sách hợp đồng của tôi | DESIGN |
| `POST` | `/api/contracts/{id}/activate` | OWNER | Kiểm tra và kích hoạt `ACTIVE` | DESIGN |
| `POST` | `/api/contracts/{id}/deposit-records` | OWNER/STAFF theo khu | Ghi nhận tiền cọc/điều chỉnh hợp lệ; có lịch sử | DESIGN |
| `POST` | `/api/contracts/{id}/extension-requests` | TENANT sở hữu | Gửi yêu cầu gia hạn `PENDING` | DESIGN |
| `GET` | `/api/extension-requests` | OWNER/STAFF theo khu | Danh sách yêu cầu gia hạn | DESIGN |
| `PATCH` | `/api/extension-requests/{id}/decision` | OWNER/STAFF theo khu được phép | `APPROVED`/`REJECTED` theo quy trình | DESIGN |
| `POST` | `/api/contracts/{id}/move-out-requests` | TENANT sở hữu | Gửi yêu cầu trả phòng `REQUESTED` | DESIGN |
| `GET` | `/api/move-out-requests` | OWNER/STAFF theo khu | Danh sách yêu cầu trả phòng | DESIGN |
| `PATCH` | `/api/move-out-requests/{id}/status` | OWNER/STAFF theo khu | Xác nhận và theo dõi bàn giao/quyết toán | DESIGN |
| `POST` | `/api/contracts/{id}/settlements` | OWNER/STAFF theo khu | Quyết toán công nợ, khấu trừ/hoàn cọc | DESIGN |
| `POST` | `/api/contracts/{id}/end` | OWNER/STAFF theo khu được phép | Kết thúc hợp đồng sau quyết toán hợp lệ | DESIGN |

**Quy tắc:** Không có hai hợp đồng `ACTIVE` chồng thời gian trên một phòng. Chỉ OWNER kích hoạt hợp đồng; kích hoạt `ACTIVE` làm phòng `OCCUPIED` qua tích hợp an toàn với Property Service. Yêu cầu gia hạn/trả phòng không tự đổi hợp đồng; trả phòng chưa quyết toán không tự giải phóng phòng.

**Mẫu lập DRAFT (đề xuất):**

```json
{"roomId":"<uuid>","tenantProfileId":"<uuid>","startDate":"2026-11-15","endDate":"2027-11-15","rentAmount":2000000,"depositAmount":2000000}
```

## 6. Billing / Utilities — Quốc (`billing-service`)

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `POST` | `/api/meter-readings` | OWNER/STAFF theo khu | Ghi điện/nước theo kỳ, kèm ảnh minh chứng | DESIGN |
| `GET` | `/api/meter-readings?roomId={id}&period={yyyy-MM}` | OWNER/STAFF theo khu; TENANT có hợp đồng liên quan | Tra cứu chỉ số theo kỳ | DESIGN |
| `POST` | `/api/invoices` | OWNER/STAFF theo khu | Lập hóa đơn `DRAFT` theo kỳ, lưu giá snapshot | DESIGN |
| `GET` | `/api/invoices` | OWNER/STAFF theo khu | Danh sách hóa đơn/phân trang | DESIGN |
| `GET` | `/api/invoices/{id}` | OWNER/STAFF theo khu; TENANT sở hữu | Chi tiết hóa đơn | DESIGN |
| `GET` | `/api/invoices/me` | TENANT | Danh sách hóa đơn của tôi | DESIGN |
| `POST` | `/api/invoices/{id}/issue` | OWNER/STAFF theo khu được phép | Phát hành hóa đơn `ISSUED` | DESIGN |
| `POST` | `/api/invoices/{id}/manual-payments` | OWNER/STAFF theo khu | Ghi nhận tiền mặt/chuyển khoản đã xác nhận | DESIGN |
| `GET` | `/api/debts` | OWNER/STAFF theo khu | Tổng hợp công nợ theo quyền | DESIGN |
| `GET` | `/api/debts/me` | TENANT | Công nợ của tôi | DESIGN |

**Quy tắc:** Chỉ số mới không nhỏ hơn kỳ trước; không có hai chỉ số cùng loại/phòng/kỳ. Không phát hành trùng hóa đơn cùng hợp đồng/kỳ. Hóa đơn `DRAFT → ISSUED → PARTIALLY_PAID/PAID` (và `OVERDUE`, `CANCELLED` theo điều kiện). Không điều chỉnh số tiền hóa đơn đã phát hành dựa trên thay đổi bảng giá sau đó.

## 7. Payment — Quốc (`payment-service`)

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `POST` | `/api/payments/transactions` | TENANT sở hữu hóa đơn còn nợ | Khởi tạo thanh toán sandbox, header `Idempotency-Key` | DESIGN |
| `GET` | `/api/payments/transactions/{id}` | TENANT sở hữu; OWNER/STAFF theo khu | Tra cứu trạng thái giao dịch | DESIGN |
| `GET` | `/api/payments/me` | TENANT | Lịch sử thanh toán của tôi | DESIGN |
| `POST` | `/api/payments/provider/callback` | Payment Provider (xác thực webhook) | Nhận/cập nhật kết quả mô phỏng, không dùng JWT người dùng | DESIGN |

**Quy tắc:** `PENDING`/`FAILED`/`CANCELLED`/timeout **không giảm công nợ**. Chỉ `SUCCESS` đã xác minh mới ghi nhận vào Billing Service **đúng một lần**; không tạo thanh toán kép khi callback lặp. `Idempotency-Key`, mã giao dịch phía provider, chữ ký/xác thực callback, timeout và retry phải được thống nhất trước khi tích hợp thật. Thao tác thanh toán thủ công thuộc Billing, **không** tự gắn `SUCCESS` cho giao dịch provider.

**Mẫu tạo giao dịch (đề xuất):**

```http
POST /api/payments/transactions
Authorization: Bearer <token>
Idempotency-Key: demo-invoice-001
Content-Type: application/json

{"invoiceId":"<uuid>","amount":2200000}
```

## 8. Notification — Quân (`notification-service`)

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `GET` | `/api/notifications/me` | JWT | Thông báo của user đang đăng nhập | W1-CODE |
| `PATCH` | `/api/notifications/{id}/read` | JWT, đúng chủ thông báo | Đánh dấu thông báo đã đọc | W1-CODE |

Các sự kiện `CONTRACT_ACTIVATED`, `VIEWING_ASSIGNED`, `INVOICE_ISSUED`, `PAYMENT_SUCCEEDED`, `REQUEST_RESOLVED` chỉ là **tên sự kiện đề xuất**, chưa phải schema RabbitMQ đã chốt. Cần thống nhất `eventId`, `eventType`, `occurredAt`, `recipientUserId`, `entityId`, `payload`, retry/idempotency và phiên bản schema trước khi phát sự kiện liên service.

## 9. Dashboard / báo cáo chung

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `GET` | `/api/reports/overview` | OWNER/STAFF theo khu | Số phòng, công nợ, doanh thu, lịch xem theo phạm vi | DESIGN — chưa chốt service tổng hợp |

Service nào tổng hợp báo cáo và cách lấy snapshot từ Property/Rental/Billing/Payment cần được cả nhóm chốt trước khi triển khai. Không nên dùng API Gateway làm nơi chứa logic nghiệp vụ báo cáo.

## 10. Quy trình liên service cần kiểm thử tích hợp

1. **Hiển thị phòng:** Property Service chỉ trả phòng trống được OWNER công khai; GUEST xem và liên hệ OWNER ngoài website.
2. **Xem phòng:** OWNER ghi lịch → Property Service lọc STAFF theo khu/lịch → OWNER gán STAFF hoặc tự dẫn → STAFF báo kết quả; **không phát sinh giữ phòng**.
3. **Kích hoạt thuê:** Rental Service tạo DRAFT → OWNER kích hoạt → xác minh phòng và tránh hợp đồng chồng → đồng bộ Property `OCCUPIED` → OWNER/STAFF hợp lệ cấp tài khoản TENANT → Notification thông báo.
4. **Hóa đơn:** Billing nhận hợp đồng ACTIVE/chỉ số/đơn giá, tạo DRAFT → phát hành ISSUED → TENANT xem trong cổng cá nhân.
5. **Thanh toán:** TENANT khởi tạo giao dịch Payment → Provider sandbox trả kết quả → Payment xác minh, chống callback trùng → Billing ghi nhận công nợ đúng một lần → Notification gửi thông báo.
6. **Trả phòng:** TENANT gửi yêu cầu → OWNER/STAFF xử lý, chốt công nợ/cọc → Rental kết thúc ENDED → Property đưa phòng về AVAILABLE khi đủ điều kiện.

## 11. Checklist trước khi merge PR của mỗi service

- Đúng đường dẫn `/api/...` và khai báo Gateway, không có `/v1`.
- Controller/DTO/validation phù hợp hợp đồng, status code và phân quyền theo bảng.
- Không lộ passwordHash, không cho STAFF đọc khu khác, TENANT đọc hồ sơ người khác.
- Có xử lý `400`, `401`, `403`, `404`, `409`; không trả lỗi 500 cho xung đột nghiệp vụ dự đoán trước.
- Không tự ghi DB service khác; quy tắc trạng thái, chống trùng kỳ và chống giao dịch lặp có kiểm thử.
- Thêm request/response mẫu vào Postman của module; thông báo thay đổi contract trước khi tạo PR vào `dev`.

## 12. Điểm cần thống nhất thêm (chưa được coi là đã hoàn thành)

- Quyền STAFF có thể cấp TENANT trong giai đoạn nào và kiểm tra liên kết hợp đồng/khu thế nào.
- Phân vùng dữ liệu STAFF theo `propertyId`, kiểm tra quyền liên service và cách vô hiệu hóa JWT khi khóa tài khoản.
- Cơ chế cập nhật trạng thái phòng/hợp đồng giữa Property và Rental khi một service gặp lỗi.
- Quy tắc tính giá dịch vụ, tiền cọc, hoàn cọc và đối soát công nợ giữa Rental/Billing/Payment.
- Schema sự kiện RabbitMQ, retry/DLQ/outbox, xác thực service-to-service.
- Chuẩn lỗi JSON chung, phân trang, tải ảnh, logs/audit, migration database.
- Chuẩn callback sandbox và dịch vụ chịu trách nhiệm tổng hợp dashboard.

**Nguồn nghiệp vụ:** SRS cuối cùng của dự án (GUEST/OWNER/STAFF/TENANT, FR-01 → FR-22, UC-01 → UC-10). Danh sách các endpoint `DESIGN` là **đề xuất thiết kế API để chia việc và tích hợp**, không phải các đường dẫn được SRS quy định cố định.
