# DRAFT — Room Hold ↔ Rental/Payment (chờ Quân/Sáng/Quốc xác nhận)

Trạng thái: **CHƯA CHỐT**, không triển khai như API hiện hành.

- Property: nguồn trạng thái phòng và room hold (`roomId`, `holdId`, `viewingId`, `customerId`, `status`, `expiresAt`).
- Payment: nguồn biên nhận, xác minh chứng từ và đối soát tiền cọc; cung cấp tham chiếu xác minh/giải quyết tiền cọc.
- Rental: quản lý hợp đồng thuê; chỉ chuyển khoản cọc đã được Payment xác minh hợp lệ thành cọc hợp đồng đúng một lần.
- Auth: mọi JWT user phải introspect; service-to-service không giả lập OWNER.

## Luồng cần thống nhất

1. STAFF/OWNER ghi nhận biên nhận cọc qua Payment => `receiptReference` (unique), số tiền, phương thức, người ghi nhận.
2. CUSTOMER xác nhận chứng từ giao nhận (không đồng nghĩa OWNER đã nhận tiền).
3. Property tạo PENDING hold cho room AVAILABLE, liên kết `receiptReference`, không thu tiền.
4. Payment xác minh và đối soát biên nhận; OWNER chỉ xác nhận thủ công dựa trên tham chiếu của Payment (Phase 1 chưa tích hợp API), rồi Property chuyển APPROVED/RESERVED theo API có xác thực caller.
5. Đến hạn/hủy: Payment đối soát/giải quyết tiền cọc, cấp `depositResolutionReference` => Property mới giải phóng phòng.
6. Khi hợp đồng ACTIVE: Rental kiểm tra chứng từ cọc hợp lệ do Payment xác minh, chuyển cọc vào hợp đồng đúng một lần; phối hợp cập nhật Property OCCUPIED một cách idempotent.

**API proposal chỉ để họp nhóm (không có trong code Phase 1):**

- `GET /internal/payment/hold-receipts/{receiptReference}/status` => holder/room, amount, status, ownerVerified, settledAt, convertedContractId.
- `POST /internal/property/room-holds/{holdId}/confirm-from-payment` => xác nhận từ service có định danh riêng, không dùng JWT của OWNER.
- `POST /internal/property/room-holds/{holdId}/convert` => idempotencyKey, contractId, kỳ hạn hiệu lực.

Không kết nối trực tiếp database của service khác. Cần giao ước retry, idempotency, audit, error 409/503, event outbox.
