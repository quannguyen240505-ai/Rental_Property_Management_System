# DRAFT — Room Hold ↔ Rental/Payment (chờ Quân/Sáng/Quốc xác nhận)

Trạng thái: **CHƯA CHỐT**, không triển khai như API hiện hành.

- Property: nguồn trạng thái phòng và room hold (`roomId`, `holdId`, `viewingId`, `customerId`, `status`, `expiresAt`).
- Rental: nguồn biên nhận và các khoản cọc/đối soát, chuyển tiền giữ phòng sang cọc hợp đồng đúng một lần.
- Payment: chỉ gửi kết quả đã kiểm tra từ provider sandbox; không được tự nói đã trả tiền.
- Auth: mọi JWT user phải introspect; service-to-service không giả lập OWNER.

## Luồng cần thống nhất

1. STAFF/OWNER tạo biên nhận cọc trong Rental => `receiptReference` (unique), số tiền, phương thức, người ghi nhận.
2. CUSTOMER xác nhận chứng từ giao nhận (không đồng nghĩa OWNER đã nhận tiền).
3. Property tạo PENDING hold cho room AVAILABLE, liên kết `receiptReference`, không thu tiền.
4. OWNER kiểm tra/đối soát biên nhận trong Rental => confirmation proof thuộc sở hữu Rental, rồi Property chuyển APPROVED/RESERVED theo API có xác thực caller.
5. Đến hạn/hủy: Rental xử lý khoản cọc, cấp `depositResolutionReference` => Property mới giải phóng phòng.
6. Khi hợp đồng ACTIVE: Rental xác nhận giữ chỗ hợp lệ, chuyển cọc đúng một lần, cập nhật Property OCCUPIED một cách idempotent.

**API proposal chỉ để họp nhóm (không có trong code Phase 1):**

- `GET /internal/rental/hold-deposits/{receiptReference}/status` => holder/room, amount, status, ownerVerified, settledAt, convertedContractId.
- `POST /internal/property/room-holds/{holdId}/confirm-from-rental` => xác nhận từ service có định danh riêng, không dùng JWT của OWNER.
- `POST /internal/property/room-holds/{holdId}/convert` => idempotencyKey, contractId, kỳ hạn hiệu lực.

Không kết nối trực tiếp database của service khác. Cần giao ước retry, idempotency, audit, error 409/503, event outbox.
