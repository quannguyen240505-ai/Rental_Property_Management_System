# API CONTRACT — TOÀN NHÓM

**Dự án:** Rental Property Management System — SOA  
**Cập nhật:** 21/11/2026 — Auth Service và Internal JWT Introspection  
**Base URL dự kiến qua API Gateway:** `http://localhost:8080` (chưa xác nhận chạy tích hợp Gateway)  
**Auth Service đang kiểm thử trực tiếp:** `http://localhost:8081`  
**Quy ước:** API nghiệp vụ dùng `/api/...` (không dùng `/api/v1`); API nội bộ Auth dùng `/internal/auth/introspect`.

> **Định nghĩa trạng thái:** `AUTH-TESTED` = có source trên `feature/auth-service` và đã được kiểm thử Postman trong môi trường local; `W1-CODE` = ghi nhận có code từ tài liệu tuần 1, chưa xác minh tích hợp trong lần cập nhật này; `DESIGN` = API/luồng đề xuất, chưa mặc định coi là triển khai. Kết quả kiểm thử local không phải chứng nhận CI hoặc tích hợp liên service.

## 1. Phân chia service và người phụ trách

| Service / Thành phần | Phụ trách | Trách nhiệm |
|---|---|---|
| `service-registry` (Eureka) | Quân | Đăng ký, khám phá service; không có REST nghiệp vụ `/api` |
| `api-gateway` | Quân | Cổng 8080, routing, kiểm tra JWT ở các route riêng tư |
| `auth-service` | Quân | Đăng ký CUSTOMER, login, JWT, quản lý OWNER/STAFF/TENANT/CUSTOMER, thu hồi token, introspection nội bộ |
| `notification-service` | Quân | Thông báo trong website, tích hợp sự kiện RabbitMQ |
| `property-service` | Thiện | Khu trọ, phòng, giá/ảnh, tin công khai, STAFF theo khu, lịch xem phòng |
| `rental-service` | Sáng | Hồ sơ thuê, hợp đồng, cọc, gia hạn, trả phòng, quyết toán |
| `billing-service` | Quốc | Chỉ số điện/nước, hóa đơn, công nợ, thanh toán thủ công |
| `payment-service` | Quốc | Giao dịch và kết quả thanh toán mô phỏng qua sandbox/test |
| `frontend` (Angular) | Cả nhóm | Giao diện theo module, gọi API qua Gateway |

## 2. Quy ước chung

- API JSON mặc định `Content-Type: application/json`; tải ảnh dùng `multipart/form-data` nếu triển khai.
- **Actor/role:** `GUEST` là người chưa đăng nhập, **không phải JWT role**. Các enum role của Auth: `OWNER`, `STAFF`, `TENANT`, `CUSTOMER`. `OWNER` là chủ trọ/quản trị; `STAFF` được thao tác theo khu được giao; `TENANT` là người thuê có quyền đối với hợp đồng/dữ liệu của mình; `CUSTOMER` là tài khoản tự đăng ký và **chưa có quyền TENANT**.
- **Đăng ký hiện tại:** `POST /api/auth/register` là **public**, chỉ tạo `CUSTOMER`. `OWNER` chỉ tạo `STAFF` qua `POST /api/users`. Không cho tạo OWNER/TENANT trực tiếp; chuyển CUSTOMER ↔ TENANT đang bị chặn cho đến khi xác minh hợp đồng qua Rental Service. OWNER có thể chuyển CUSTOMER ↔ STAFF theo quy tắc Auth.
- **Phân quyền:** OWNER có quyền quản trị cao nhất và về nghiệp vụ bao gồm khả năng STAFF, nhưng các service vẫn phải định nghĩa policy/role checks phù hợp. STAFF chỉ thao tác `propertyId` được phân công; TENANT chỉ được xem dữ liệu có liên kết đến user của mình. Không tin `userId`, `role` tự gửi từ phía client khi xử lý nghiệp vụ.
- **Property/Viewing còn là DESIGN:** GUEST xem phòng công khai, liên hệ OWNER theo phương án ban đầu; việc bổ sung `CUSTOMER` trong Auth **chưa đồng nghĩa** đã xây API đặt lịch, giữ phòng hoặc ký hợp đồng trực tuyến. Các thay đổi nghiệp vụ này cần thống nhất với SRS và người phụ trách.
- ID là UUID; ngày `yyyy-MM-dd`; tháng hóa đơn `yyyy-MM`; ngày giờ ISO 8601, Auth sử dụng UTC `Instant` (ví dụ `2026-11-21T16:00:00Z`). Tiền là số nguyên VND, không dùng số thực để tính tiền.
- HTTP status thường gặp: `200` đọc/cập nhật; `201` tạo; `202` chấp nhận xử lý; `204` không có nội dung; `400` dữ liệu không hợp lệ; `401` không xác thực/token bị thu hồi; `403` thiếu quyền; `404` không tìm thấy/không trong phạm vi; `409` xung đột trạng thái. Nếu Auth không khả dụng để kiểm tra token, service phải **fail closed**, sử dụng lỗi hạ tầng phù hợp như `503`, không tự cho request đi qua.
- Mỗi service sở hữu database riêng. Property, Rental, Billing, Payment **không truy cập trực tiếp `auth_db`**, cũng không lấy `JWT_SECRET` của Auth. Giao tiếp bằng REST hoặc event có xác thực caller, timeout và xử lý lỗi.

### 2.1. Hợp đồng JWT hiện tại của Auth

- Thuật toán `HS256`, `iss = rental-property-auth`, thời gian hết hạn cấu hình mặc định **30 phút**.
- Claims: `iss`, `sub` (UUID user), `iat`, `exp`, `username`, `roles` (mảng có một giá trị như `["ROLE_CUSTOMER"]`), `tv` (số phiên bản token của user trong database).
- `JwtDecoder` của Auth kiểm tra chữ ký, issuer, thời gian hiệu lực, user tồn tại và `ACTIVE`, `tv` khớp `token_version` hiện tại, role khớp database. Sau logout, đổi/reset mật khẩu, khóa/mở khóa hoặc thay role, token cũ bị thu hồi bằng cách tăng `token_version`.
- Auth cấp JWT bằng `JWT_SECRET`; service khác xác minh tính hiệu lực thông qua **Internal Token Introspection** (mục 3.2), không chỉ decode payload JWT.
- `/actuator/health` là endpoint vận hành, **không thay thế** JWT verification.

## 3. Auth / User — Quân (`auth-service`)

**URL kiểm thử hiện tại:** `http://localhost:8081`. Các URL nghiệp vụ `/api/...` sẽ đi qua Gateway `http://localhost:8080` sau khi cấu hình và kiểm thử routing. Các trạng thái bên dưới dựa trên source `feature/auth-service` và kết quả Postman local của nhóm.

### 3.1. Authentication và User Management

| Method | Path | Quyền | Nội dung | Trạng thái |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Đăng ký tài khoản role **CUSTOMER** | AUTH-TESTED |
| `POST` | `/api/auth/login` | Public | Login bằng username/password, cấp JWT | AUTH-TESTED |
| `POST` | `/api/auth/logout` | JWT | Thu hồi toàn bộ JWT cũ của tài khoản bằng `token_version` | AUTH-TESTED |
| `POST` | `/api/auth/change-password` | JWT | Đổi mật khẩu, thu hồi JWT cũ | AUTH-TESTED |
| `POST` | `/api/auth/forgot-password` | Public | Trả `202`/thông báo chung, không tiết lộ email có tồn tại | CODE — SMTP NOT RUN |
| `POST` | `/api/auth/reset-password` | Public | Đặt lại mật khẩu bằng reset token một lần, thu hồi JWT cũ | CODE — SMTP NOT RUN |
| `GET` | `/api/users/me` | JWT | Lấy profile của mình | AUTH-TESTED |
| `PATCH` | `/api/users/me` | JWT | Sửa fullName/email/phone của mình | AUTH-TESTED |
| `GET` | `/api/users?role={role}&status={status}` | OWNER | Danh sách; filter `role`/`status` không bắt buộc | AUTH-TESTED |
| `GET` | `/api/users/staff` | OWNER | Danh sách STAFF | AUTH-TESTED |
| `GET` | `/api/users/{id}` | OWNER | Chi tiết user theo UUID | AUTH-TESTED |
| `POST` | `/api/users` | OWNER | **Chỉ tạo STAFF**, không tạo OWNER/TENANT/CUSTOMER | AUTH-TESTED |
| `PUT` | `/api/users/{id}` | OWNER | Sửa thông tin tài khoản không phải OWNER | AUTH-TESTED |
| `PATCH` | `/api/users/{id}/status` | OWNER | `ACTIVE`/`LOCKED`; không áp dụng OWNER | AUTH-TESTED |
| `PATCH` | `/api/users/{id}/role` | OWNER | CUSTOMER ↔ STAFF; TENANT chờ tích hợp Rental | AUTH-TESTED (TENANT bị chặn) |
| `GET` | `/api/users/{id}/role` | OWNER | Trả role hiện tại (enum JSON) | AUTH-TESTED |
| `GET` | `/api/users/{id}/role-history` | OWNER | Lịch sử thay đổi role | AUTH-TESTED |

**Register CUSTOMER** — `POST /api/auth/register`:

```json
{
  "username": "customer_demo",
  "password": "<strong-password>",
  "fullName": "Khách hàng A",
  "email": "customer@example.com",
  "phone": "0900000000"
}
```

HTTP `201` trả `UserResponse` gồm `id`, `username`, `fullName`, `email`, `phone`, `role` (`CUSTOMER`), `status` (`ACTIVE`), `createdAt`, `updatedAt`, **không trả passwordHash**.

**Login** — `POST /api/auth/login`:

```json
{"username":"owner","password":"<password>"}
```

Response HTTP `200`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-21T16:00:00Z",
  "role": "OWNER",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**OWNER tạo STAFF** — `POST /api/users`:

```json
{
  "username": "staff_demo",
  "password": "<strong-password>",
  "fullName": "Nhân viên A",
  "email": "staff@example.com",
  "phone": "0900000000",
  "role": "STAFF"
}
```

**Đổi quyền** — `PATCH /api/users/{id}/role`:

```json
{
  "role": "STAFF",
  "reason": "Phân công nhân viên",
  "contractReference": null,
  "contractStatusConfirmed": false
}
```

OWNER được phép chuyển CUSTOMER → STAFF → CUSTOMER; mỗi lần thay role sẽ tăng `token_version` và ghi `role_change_logs`. Chuyển CUSTOMER ↔ TENANT hiện trả HTTP `409`, `code=RENTAL_INTEGRATION_REQUIRED`. Trường `contractStatusConfirmed` từ client **không được coi là bằng chứng đã xác minh hợp đồng**; cấu hình `app.role-management.manual-confirmation-enabled=false` cần tiếp tục giữ trong lúc chưa tích hợp Rental.

**Mẫu lỗi nghiệp vụ Auth:**

```json
{
  "code": "CONFLICT",
  "message": "Username already exists",
  "timestamp": "2026-11-21T16:00:00Z"
}
```

Mật khẩu phải có tối thiểu 10 ký tự, gồm chữ và số; dài tối đa 72 byte UTF-8. Enum trạng thái là `ACTIVE` và `LOCKED`. Reset token có thời hạn 15 phút, lưu hash và dùng một lần. **Luồng gửi Gmail SMTP thực tế chưa được kiểm thử**, không được ghi là PASS.

### 3.2. Internal JWT Introspection (backend-to-backend)

**Trạng thái: `AUTH-TESTED`**. API có mã nguồn trên nhánh `feature/auth-service`, **không phải API công khai cho Frontend**. Các service nghiệp vụ sử dụng nó để xác nhận JWT còn hiệu lực, bao gồm `token_version`.

| Thuộc tính | Contract hiện tại |
|---|---|
| Method | `POST` |
| Path | `/internal/auth/introspect` |
| Dev URL | `http://localhost:8081/internal/auth/introspect` |
| Header bắt buộc | `Content-Type: application/json`, `X-Service-Key: <internal-service-secret>` |
| Authorization | **No Auth đối với request introspect**; JWT cần kiểm tra ở JSON body |
| JSON body | `{"token":"<raw-jwt>"}` — không có prefix `Bearer ` |
| Key đúng, token hợp lệ | HTTP `200` + `active: true` |
| Key đúng, token hết hạn/sai/đã thu hồi | HTTP `200` + `active: false` |
| Key sai/thiếu | HTTP `401`, `code: INVALID_SERVICE_KEY` |
| Body thiếu/rỗng token, key đúng | HTTP `400`, `code: VALIDATION_ERROR` |

**Request:**

```http
POST /internal/auth/introspect
Content-Type: application/json
X-Service-Key: <internal-service-secret>
```

```json
{"token":"<raw-jwt>"}
```

**Response hợp lệ — HTTP `200`:**

```json
{
  "active": true,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "customer_demo",
  "role": "CUSTOMER",
  "expiresAt": "2026-11-21T16:00:00Z"
}
```

**Token không hợp lệ/đã bị thu hồi — HTTP `200`:**

```json
{
  "active": false,
  "userId": null,
  "username": null,
  "role": null,
  "expiresAt": null
}
```

**Service Key sai hoặc thiếu — HTTP `401`:**

```json
{"code":"INVALID_SERVICE_KEY","message":"Invalid internal service credentials"}
```

> **Quan trọng:** `200 + active:false` chỉ nghĩa là Auth xử lý được request introspection, **không xác thực được JWT**. Service gọi phải trả `401` cho client nếu JWT không còn hợp lệ. HTTP `401 INVALID_SERVICE_KEY` là lỗi xác thực *backend gọi Auth*.

**Quy trình xác thực liên service:**

1. Client/Payment chuyển `Authorization: Bearer <user-jwt>` đến service nghiệp vụ khi hành động **thay mặt người dùng**.
2. Service nhận JWT gọi trực tiếp Auth nội bộ bằng `POST /internal/auth/introspect`, gửi `X-Service-Key` riêng và JWT dạng raw trong body. **Không gửi JWT đã hết hạn vào Authorization header của request introspect.**
3. Nếu `active=true`, dùng `userId` và `role` từ **response của Auth** để kiểm tra quyền nghiệp vụ và quyền sở hữu tài nguyên; không tin dữ liệu role/userId do client tự cung cấp.
4. Nếu `active=false`, từ chối với `401`; nếu token hợp lệ nhưng thiếu quyền, trả `403` (hoặc `404` để tránh lộ tài nguyên theo policy service).
5. Khi Auth timeout/không phản hồi, service **fail closed**, không cho qua xác thực; trả mã lỗi hạ tầng thích hợp, chẳng hạn `503`, đồng thời theo dõi lỗi/timeout.

**Các quy định về khóa và SOA:**

- `JWT_SECRET`: chỉ **Auth Service** giữ để ký/xác minh HS256; không phân phối cho Property/Rental/Billing/Payment.
- `AUTH_INTERNAL_SERVICE_KEY`: secret riêng tối thiểu 32 byte UTF-8, xác thực backend có quyền gọi introspection qua `X-Service-Key`; **khác `JWT_SECRET`**. Giai đoạn hiện tại dùng một shared key, chưa có danh tính/khóa riêng từng service.
- Không đưa hai khóa lên Frontend/Postman Collection public/GitHub; dùng biến môi trường và cơ chế quản lý secret. Không đưa `/internal/auth/introspect` thành route công khai trên Gateway; khi triển khai qua mạng cần network isolation + HTTPS/TLS + hạn chế thời gian chờ và luân chuyển khóa.
- Payment có thể forward Bearer JWT sang Property nếu xử lý thay mặt người dùng; Property **vẫn phải introspect**. Với lời gọi nội bộ không đại diện user hoặc callback của payment provider, dùng cơ chế xác thực service-to-service **riêng**, không mượn JWT của OWNER hoặc người dùng.
- Auth xác thực danh tính/token; **mỗi service vẫn tự kiểm tra authorization nghiệp vụ** (theo tài nguyên/khu/hợp đồng/hóa đơn của người dùng).

### 3.3. Trạng thái kiểm thử Auth

- Auth Regression: **57 request, 104 assertions PASS** tại môi trường local (ngày 21/11/2026, theo báo cáo kiểm thử của nhóm).
- Internal Introspection: **30 request, 64 assertions PASS**, chạy lại thành công hai lượt liên tiếp (ngày 21/11/2026, theo báo cáo kiểm thử của nhóm).
- Collections và environment mẫu được lưu trong thư mục `postman/` ở repository; **không commit** `JWT_SECRET`, `AUTH_INTERNAL_SERVICE_KEY`, JWT thật hoặc mật khẩu OWNER.
- **Chưa kiểm thử** Forgot/Reset Password qua Gmail SMTP thật và end-to-end Payment → Property → Auth. Chưa được xác nhận CI, kiểm thử tải hoặc an ninh môi trường triển khai.

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
3. **Kích hoạt thuê:** Rental Service tạo DRAFT → OWNER kích hoạt → xác minh phòng và tránh hợp đồng chồng → đồng bộ Property `OCCUPIED` → **thiết kế Rental xác minh hợp đồng và tích hợp Auth để chuyển CUSTOMER thành TENANT** (chưa triển khai, không cấp TENANT trực tiếp) → Notification thông báo.
4. **Hóa đơn:** Billing nhận hợp đồng ACTIVE/chỉ số/đơn giá, tạo DRAFT → phát hành ISSUED → TENANT xem trong cổng cá nhân.
5. **Thanh toán:** TENANT khởi tạo giao dịch Payment → Payment/Property/Billing introspect JWT qua Auth khi cần → Provider sandbox trả kết quả → Payment xác minh, chống callback trùng → Billing ghi nhận công nợ đúng một lần → Notification gửi thông báo. Callback provider hoặc lời gọi thuần backend dùng xác thực service-to-service riêng.
6. **Trả phòng:** TENANT gửi yêu cầu → OWNER/STAFF xử lý, chốt công nợ/cọc → Rental kết thúc ENDED → Property đưa phòng về AVAILABLE khi đủ điều kiện.

## 11. Checklist trước khi merge PR của mỗi service

- Đúng đường dẫn `/api/...` và khai báo Gateway, không có `/v1`. Riêng `/internal/auth/introspect` chỉ dùng nội bộ, **không public qua Gateway**.
- Controller/DTO/validation phù hợp hợp đồng, status code và phân quyền theo bảng.
- Không lộ `passwordHash`, `JWT_SECRET`, `AUTH_INTERNAL_SERVICE_KEY`; không cho STAFF đọc khu khác, TENANT đọc hồ sơ người khác, CUSTOMER vượt quyền TENANT.
- Có xử lý `400`, `401`, `403`, `404`, `409`; không trả lỗi 500 cho xung đột nghiệp vụ dự đoán trước. Service nhận Bearer JWT phải introspect Auth; `active:false` nghĩa là token không hợp lệ; timeout/không thể kết nối Auth thì fail closed.
- Không tự ghi DB service khác; quy tắc trạng thái, chống trùng kỳ và chống giao dịch lặp có kiểm thử.
- Thêm request/response mẫu vào Postman của module; thông báo thay đổi contract trước khi tạo PR vào `dev`.

## 12. Điểm cần thống nhất thêm (chưa được coi là đã hoàn thành)

- **Chưa triển khai:** quy tắc chuyển CUSTOMER ↔ TENANT sau khi Rental xác minh hợp đồng; caller backend nào được phép kích hoạt chuyển role và quyền OWNER/STAFF liên quan.
- Phân vùng dữ liệu STAFF theo `propertyId` và kiểm tra quyền nghiệp vụ. **Đã có trong Auth:** thu hồi JWT qua `token_version` và introspection; **chưa xác nhận** các service khác đã tích hợp xác thực.
- Cơ chế cập nhật trạng thái phòng/hợp đồng giữa Property và Rental khi một service gặp lỗi.
- Quy tắc tính giá dịch vụ, tiền cọc, hoàn cọc và đối soát công nợ giữa Rental/Billing/Payment.
- Schema sự kiện RabbitMQ, retry/DLQ/outbox; bảo vệ mạng cho introspection và quản lý/luân chuyển khóa; xác thực service-to-service cho những lời gọi không đại diện người dùng.
- Chuẩn lỗi JSON chung, phân trang, tải ảnh, logs/audit, migration database.
- Chuẩn callback sandbox và dịch vụ chịu trách nhiệm tổng hợp dashboard.

**Nguồn nghiệp vụ:** SRS nền trước đây của dự án (GUEST/OWNER/STAFF/TENANT, FR-01 → FR-22, UC-01 → UC-10) cùng mã Auth thực tế hiện bổ sung role CUSTOMER, đăng ký public và JWT introspection. **Những thay đổi ảnh hưởng các nghiệp vụ Property/Rental/Billing/Payment cần được đồng bộ SRS riêng**, không tự coi các API DESIGN là đã triển khai. Danh sách endpoint DESIGN chỉ là đề xuất tích hợp, không phải đường dẫn được SRS quy định cố định.
