# Kafka Phase C — customer-care-service (chat real-time)

**Ngày:** 2026-08-18
**Bối cảnh:** Tiếp Phase A (hạ tầng Kafka) và Phase B (email-service) — Phase C: `customer-care-service`, service thứ 3 và cuối cùng, chat real-time giữa patient và staff qua WebSocket/STOMP, lưu MongoDB, publish `chat.message-sent` lên Kafka.

## Đã làm
- `antijob/customer-care-service/` — project Gradle độc lập thứ 3, package `com.vibecode.customercare`, port `8082`.
- `security/JwtVerifier` + `TokenRevocationCheck` — copy-paste trimmed từ booking-service (chỉ phần verify, không `generateToken`). Vì role đã có sẵn trong claim JWT, **không cần** `UserDetailsService`/DB lookup nào cả — service này không có Postgres.
- `security/JwtAuthenticationResolver` — gộp verify + check thu hồi + build `Authentication`, dùng chung cho cả `JwtAuthFilter` (REST) lẫn `StompAuthChannelInterceptor` (WebSocket CONNECT).
- `config/StompAuthChannelInterceptor` — auth qua CONNECT header `Authorization: Bearer ...` (không phải query param, đúng quyết định đã chốt), reject bằng `AccessDeniedException` nếu token invalid/revoked.
- `document/Conversation` + `Message` (MongoDB) — model 1 patient : N staff, mỗi patient đúng 1 conversation (unique theo `patientEmail`), auto-tạo ở tin nhắn đầu tiên.
- `ws/ChatStompController` (`@MessageMapping("/chat.send")`) — 1 đích duy nhất cho cả patient lẫn staff: patient luôn resolve theo email của mình (không cần biết conversationId trước), staff bắt buộc truyền `conversationId`.
- `controller/ConversationController` — `GET /api/conversations` (staff: tất cả; patient: của mình), `GET /api/conversations/{id}/messages` (phân trang, staff luôn xem được, patient chỉ xem được conversation của mình).
- `ChatService.sendMessage()` — lưu Mongo, broadcast qua `SimpMessagingTemplate` tới `/topic/conversations/{id}`, publish Kafka `chat.message-sent`.
- `docker-compose.yml` (booking-service): thêm service `care-mongo` (mongo:7, port 27017).

## Quyết định đáng nhớ
- **Không có bảng Postgres nào cho service này** — ban đầu tưởng cần "copy" `UserDetailsServiceImpl` như booking-service, nhưng JWT đã mang sẵn claim `role`, nên build `Authentication` thẳng từ token, không cần tra DB nào cả. Đơn giản hơn nhiều so với dự kiến ban đầu trong plan.
- **1 STOMP destination duy nhất `/app/chat.send`** thay vì `/app/conversations/{id}/send` như plan gốc — vì patient chưa hề có `conversationId` cho tới khi gửi tin đầu tiên (chicken-egg nếu nhét id vào destination). Server tự "find-or-create" theo email người gửi.
- **Không thêm listener thứ 4 cho email-service** (dù plan gốc có nhắc tới "notify-offline qua email" như 1 kịch bản verify) — vì presence/trạng thái online đã bị loại khỏi phạm vi Phase C từ đầu (ghi rõ trong plan: "Presence/online-status: cố tình bỏ qua"). Không có cách biết ai đang offline thì không có cơ sở để quyết định khi nào gửi mail thông báo — publish `chat.message-sent` vẫn có (để dùng sau nếu cần), nhưng chưa có consumer nào tiêu thụ nó. Ghi nhận đây là 1 điểm lệch có chủ đích khỏi plan gốc, không phải thiếu sót.

## Verify
- `./gradlew build` (customer-care-service): 9 test Mockito pass (JwtVerifier 5, JwtAuthenticationResolver 4, ChatService 7).
- **Full stack thật** — `docker compose up -d` (6 container, thêm `care-mongo`) + `bootRun` cả 3 service + client STOMP tự viết bằng Node (raw WebSocket, không cần thư viện ngoài):
  1. Patient connect + gửi tin đầu tiên → conversation tự tạo (xác nhận qua `GET /api/conversations`).
  2. Staff (ADMIN) connect, subscribe đúng conversation, nhận tin thứ 2 của patient **real-time**.
  3. Staff reply → patient (đang subscribe) nhận **real-time**.
  4. `GET /api/conversations/{id}/messages` trả đúng 3 tin, đúng thứ tự mới nhất trước.
  5. **Token đã logout (revoked)** → CONNECT bị từ chối ngay, nhận `ERROR` frame, không set được `SecurityContext`.
- Regression: `GET /api/appointments` booking-service không token vẫn 403, Swagger vẫn 200 — không có gì bị ảnh hưởng bởi 3 service chạy song song.

## Còn lại / bước tiếp theo
- **Phase D**: orchestration đầy đủ (docker-compose root gộp cả 3 service dưới `--profile full`, 2 CI workflow riêng cho email-service/customer-care-service, docs từng service).
- Chi tiết đầy đủ: `C:\Users\ADMIN\.claude\plans\linked-crunching-dahl.md`.
