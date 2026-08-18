# customer-care-service

Chat real-time (WebSocket/STOMP) giữa patient và staff — service thứ 3 và cuối cùng trong bộ tách microservice từ `booking-service` (xem `../antijob/`).

## Vai trò trong hệ thống

Không phát hành JWT, không có Postgres — chỉ **verify** token do `booking-service` ký (JWT đã mang sẵn claim `role` nên không cần tra DB nào). Lưu hội thoại/tin nhắn vào MongoDB, publish `chat.message-sent` lên Kafka (hiện chưa có consumer nào tiêu thụ).

**Model:** 1 patient : N staff — mỗi patient có đúng 1 conversation; ADMIN/RECEPTIONIST/DENTIST bất kỳ đều xem/trả lời được.

Chi tiết đầy đủ (sequence diagram, quyết định thiết kế): `../antijob/docs/system-flows.md` mục 6, và `../antijob/CLAUDE.md` Phase 18 Phase C.

## Chạy dev

Cần hạ tầng chung của `antijob/antijob/` đã chạy trước (Redis, Kafka, MongoDB — `docker compose up -d` ở đó):

```bash
./gradlew bootRun
```

Mặc định port `8082`. `JWT_SECRET` phải trùng với booking-service (mặc định dev đã hardcode giống nhau ở cả 2 bên).

## API

| Method | Path | Ai gọi được |
|---|---|---|
| `GET` | `/api/conversations` | Staff: tất cả; Patient: của mình |
| `GET` | `/api/conversations/{id}/messages` | Staff: mọi conversation; Patient: chỉ của mình |
| STOMP `CONNECT` | `/ws` (SockJS) | Header `Authorization: Bearer <jwt>` |
| STOMP `SEND` | `/app/chat.send` | Patient: không cần `conversationId`; Staff: bắt buộc |
| STOMP `SUBSCRIBE` | `/topic/conversations/{id}` | Ai cũng subscribe được đích đã biết id (không tự check quyền ở tầng subscribe — quyền thật nằm ở REST history) |

## Test

```bash
./gradlew test
```

9 test Mockito thuần (`JwtVerifierTest`, `JwtAuthenticationResolverTest`, `ChatServiceTest`) — không cần MongoDB/Redis/Kafka thật để chạy.

## Chạy full trong Docker

```bash
cd ../antijob
docker compose --profile full up -d --build
```
