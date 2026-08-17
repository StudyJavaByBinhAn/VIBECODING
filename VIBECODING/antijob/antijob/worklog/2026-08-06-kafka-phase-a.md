# Kafka Phase A — hạ tầng + booking-service publish event

**Ngày:** 2026-08-06
**Bối cảnh:** Roadmap monolith (Phase 1-17) đã xong hoàn toàn, không còn bug/limitation nào. User muốn bước tiếp theo là học/thực hành microservices + Kafka làm portfolio — tách thành 3 service thật (Booking / Email / Customer-care chat). Kế hoạch đầy đủ đã duyệt qua Plan Mode, lưu tại `C:\Users\ADMIN\.claude\plans\linked-crunching-dahl.md`. Đây là Phase A trong 4 phase (A→D).

## Đã làm
- Thêm Kafka (KRaft mode, không Zookeeper) + Kafka UI vào `docker-compose.yml`.
- `config/KafkaProducerConfig.java` mới — tự khai báo `ProducerFactory`/`KafkaTemplate<String, Object>`.
- `event/` package mới: `DomainEvent<T>` (envelope chung), `KafkaTopics`, 3 payload DTO.
- `AppointmentService.book()`/`cancel()`, `AuthService.forgotPassword()` publish 3 event Kafka **song song** với gửi email hiện có (additive, chưa xoá `EmailService`).

## Quyết định đáng nhớ
- Payload event chứa field có cấu trúc (không phải text tiếng Việt dựng sẵn) — email-service (Phase B) tự sở hữu template của nó.
- Key Kafka = `appointmentId`/`email` tương ứng, đảm bảo ordering đúng theo entity.

## Gotcha / vấn đề gặp phải
1. **Spring Boot 4.1.0 không còn autoconfigure Kafka** — không có `KafkaProperties`/`KafkaAutoConfiguration` ở đâu cả (khác Boot 2.x/3.x). Phải tự viết config, đọc `spring.kafka.bootstrap-servers` qua `@Value`.
2. **`JsonSerializer` đã deprecated forRemoval** từ spring-kafka 4.0 — dùng `JacksonJsonSerializer` (Jackson 3) thay thế.
3. **Kafka cần 2 listener** khi vừa có container gọi nhau (`kafka:9092`) vừa có `bootRun` chạy trên host (`localhost:9094`) — thiếu listener host sẽ bị `UnknownHostException` dù bootstrap ban đầu vẫn OK.
4. **Cluster 1-node cần set `replication.factor=1`** cho `__consumer_offsets`/transaction log — mặc định là 3, không bao giờ tạo được với 1 broker. Hậu quả: producer gửi thành công thật (offset tăng đúng, xác nhận bằng `kafka-get-offsets.sh`) nhưng **mọi consumer group đều thấy 0 message** vì group coordinator không khởi tạo được. Rất dễ nhầm là lỗi producer.
5. Test: `book()` giờ gọi `saved.getId().toString()` làm Kafka key — mock `appointmentRepository.save()` cũ chỉ echo lại entity chưa từng có `id`, gây NPE hàng loạt. Fix: stub tự gán id nếu chưa có.

## Verify
- `./gradlew build`: 143 test pass, Jacoco + SpotBugs sạch.
- `docker compose up -d` + `bootRun` + curl book/cancel/forgot-password thật → `kafka-console-consumer` xác nhận cả 3 topic nhận đúng JSON.
- MailHog vẫn nhận đủ 3 email như cũ — regression-free.
- Health/swagger/403-không-token không đổi.

## Còn lại / bước tiếp theo
- **Phase B**: xây `email-service` (Gradle project riêng), consume 3 topic, thêm `sent_emails` audit log, rồi mới xoá `EmailService` khỏi booking-service.
- **Phase C**: `customer-care-service` (WebSocket/STOMP chat + MongoDB + JWT verify copy-paste + publish `chat.message-sent`).
- **Phase D**: orchestration/CI/docs đầy đủ cho cả 3 service.
- Chi tiết đầy đủ: `C:\Users\ADMIN\.claude\plans\linked-crunching-dahl.md`.
