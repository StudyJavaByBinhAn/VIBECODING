# Fix: /actuator/health treo vô thời hạn do MailHealthIndicator

**Ngày:** 2026-08-06
**Bối cảnh:** Khởi động lại toàn bộ hệ thống (docker compose + bootRun) sau khi xong Phase A Kafka, để verify hệ thống chạy tốt trước khi bạn đọc code.

## Đã làm
- Phát hiện `GET /actuator/health` treo vô thời hạn (không timeout, không lỗi).
- Dùng `jcmd <pid> Thread.print` lấy thread dump — thấy mọi thread `http-nio-8080-exec-*` đang block ở `SMTPTransport.openServer` (Actuator `MailHealthIndicator`, tự bật vì có `spring-boot-starter-mail`, mở kết nối SMTP thật mỗi lần gọi health check).
- MailHog container đang chạy nhưng không phản hồi banner SMTP tại thời điểm đó (xác nhận bằng test TCP thủ công, `curl telnet://127.0.0.1:1025` cũng timeout dù connect được) — Docker networking flakiness nhất thời, không phải lỗi code.
- Fix: thêm `management.health.mail.enabled: false` vào `application.yaml` — tắt hẳn health check SMTP, không để relay chậm quyết định trạng thái app.

## Quyết định đáng nhớ
- Cùng triết lý với `EmailService` (gửi mail = side-effect, không được ảnh hưởng nghiệp vụ chính) — áp dụng luôn cho health check.

## Verify
- Restart app với config mới → `/actuator/health` trả về `200 UP` trong 0.55s (trước đó treo >30s không phản hồi).
- Regression: `/api/appointments` không token vẫn 403, `/v3/api-docs` vẫn 200.

## Còn lại / bước tiếp theo
- Không có — fix nhỏ, đã verify xong. Tiếp tục theo plan Phase B (email-service) khi bạn sẵn sàng.
