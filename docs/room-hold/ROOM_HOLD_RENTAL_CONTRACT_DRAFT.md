# DRAFT — Room Hold ↔ Rental/Payment (chờ Quân/Sáng/Quốc xác nhận)

Trạng thái: **CHƯA CHỐT**, không triển khai như API hiện hành.

- Property: nguồn trạng thái phòng và room hold (`roomId`, `holdId`, `viewingId`, `customerId`, `status`, `expiresAt`).
- Payment: nguồn biên nhận, xác minh chứng từ và đối soát tiền cọc; cung cấp tham chiếu xác minh/giải quyết tiền cọc.
- Rental: quản lý hợp đồng thuê; chỉ chuyển khoản cọc đã được Payment xác minh hợp lệ thành cọc hợp đồng đúng một lần.
- Auth: mọi JWT user phải introspect; service-to-service không giả lập OWNER.

## Luồng đã chốt với Payment (26/11/2026)

1. STAFF/OWNER tạo Hold sau viewing COMPLETED; Property không yêu cầu `receiptReference` ở thời điểm này.
2. Payment chuyển tiếp nguyên `Authorization: Bearer <user JWT>` khi gọi `GET /api/room-holds/{holdId}/deposit-eligibility`. Property xác minh JWT qua Auth Introspection và OWNER/STAFF property scope; CUSTOMER/TENANT 403.
3. Property trả đủ thông tin Hold/phòng/người thuê, `expiresAt` có offset +07:00, `eligible` boolean và `reason` null khi đủ điều kiện; không xác nhận đã thanh toán.
4. Payment tạo và đối soát biên nhận; Payment đảm bảo UNIQUE hold_id, retry cùng người/nội dung idempotent, dữ liệu khác 409.
5. Luồng OWNER_VERIFIED, confirm và xử lý tiền cọc trước approve/cancel/expire cần thống nhất giao ước xác thực riêng và kiểm thử E2E. **Chưa được đánh dấu hoàn tất.**

Xem `PROPERTY_PAYMENT_COMPATIBILITY_DRAFT.md` để biết API và mã lỗi.

## Các bước tích hợp còn chưa chốt

- Payment -> Property thông báo/tra cứu receipt đã tạo (để Property có nguồn tin cậy cho `RECEIPT_ALREADY_EXISTS`).
- Xác minh tiền đã nhận/OWNER_VERIFIED trước khi đổi trạng thái Hold sang APPROVED.
- Bảo vệ trường hợp Hold hủy hoặc hết hạn giữa lúc Payment kiểm tra và phát hành phiếu (TOCTOU).
- Rental chỉ áp dụng chứng từ cọc đã được Payment xác minh vào hợp đồng, theo idempotency key.
- Không kết nối trực tiếp database của service khác; cần retry, audit, idempotency, xử lý 409/503 rõ ràng.
