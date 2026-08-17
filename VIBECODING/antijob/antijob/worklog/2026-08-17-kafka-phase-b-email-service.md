# Kafka Phase B — email-service tách riêng, xoá EmailService khỏi booking-service

**Ngày:** 2026-08-17
**Bối cảnh:** Tiếp Phase A (hạ tầng Kafka + booking-service publish event, đã xong 2026-08-06). Phase B: xây `email-service` như 1 Gradle project độc lập hoàn toàn (không multi-module), consume 3 topic đã có, thêm audit log `sent_emails`, rồi xoá `EmailService` khỏi booking-service.

## Đã làm
- `antijob/email-service/` (project Gradle riêng, gradlew/settings.gradle/build.gradle/Dockerfile riêng) — dependency: `spring-boot-starter-webmvc` (health), `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa`, `spring-boot-starter-mail`, `spring-kafka`, `postgresql` + Flyway.
- `config/KafkaConsumerConfig.java` — tự khai báo `ConsumerFactory`/`ConcurrentKafkaListenerContainerFactory` (Boot 4.1 không autoconfigure Kafka, y hệt phát hiện ở Phase A cho producer). Value deserializer dùng `StringDeserializer` thô — không `JacksonJsonDeserializer` theo type cụ thể, vì mỗi topic có payload khác nhau và service này cố tình không share jar `DomainEvent<T>` với producer.
- `event/` — bản sao riêng của `KafkaTopics` + 3 payload DTO (khớp JSON booking-service publish, không phải bản dùng chung).
- `listener/EmailEventListener.java` — 3 `@KafkaListener`, mỗi cái tự `readTree()` message rồi `treeToValue(node.get("data"), PayloadType.class)` để lấy đúng payload (vì `DomainEvent<T>` generic không deserialize thẳng bằng type token đơn giản được). Tự dựng lại 3 template tiếng Việt (port nguyên văn từ `AppointmentService.book()/cancel()` và `AuthService.forgotPassword()` bên booking-service).
- `entity/SentEmail.java` + `V1__create_sent_emails.sql` — audit log mỗi lần xử lý event, có cột `status` (SENT/FAILED, khác thiết kế nuốt-exception-im-lặng của `EmailService` cũ vì gửi mail giờ LÀ nghiệp vụ chính của service này).
- `docker-compose.yml` (booking-service): thêm bind-mount `docker/postgres-init/` — script tạo `email_service_db` tự động cho lần khởi tạo volume Postgres mới; volume hiện có (đã tồn tại từ trước) phải tạo DB thủ công 1 lần.
- Xoá `EmailService.java`/`EmailServiceTest.java` khỏi booking-service; xoá field + lời gọi `emailService.send(...)` khỏi `AppointmentService.book()/cancel()` và `AuthService.forgotPassword()` (giữ nguyên phần publish Kafka). Xoá `spring-boot-starter-mail` khỏi `build.gradle`, xoá `spring.mail.*`/`mail.from`/`management.health.mail.enabled` khỏi `application.yaml` (không còn ý nghĩa vì không còn mail starter). Cập nhật `.env.example` (bỏ `MAIL_*`, thêm `KAFKA_BOOTSTRAP_SERVERS`).
- Cập nhật `AppointmentServiceTest`/`AuthServiceTest`: bỏ mock `EmailService`, đổi verify sang `kafkaTemplate.send(...)`.

## Quyết định đáng nhớ
- `EmailSenderService.send()` bên email-service trả về `boolean` (khác `EmailService` cũ nuốt hẳn exception) — để `EmailEventListener` biết ghi đúng status vào `sent_emails`.
- Không dùng `JacksonJsonDeserializer` theo generic `DomainEvent<T>` — parse thủ công bằng `ObjectMapper.readTree()` + `treeToValue()` đơn giản hơn nhiều so với vật lộn với type token/`TypeReference` cho 3 payload khác nhau trên 3 topic khác nhau.
- Áp dụng luôn `management.health.mail.enabled: false` cho email-service từ đầu (bài học từ sự cố treo `/actuator/health` ở booking-service sau Phase A) — không đợi gặp lại sự cố mới sửa.

## Gotcha
- Volume Postgres `dental-db-data` đã có data từ trước (không phải lần init đầu) nên script `/docker-entrypoint-initdb.d/*.sql` mới thêm không tự chạy — phải `docker exec dental-db psql -U postgres -c "CREATE DATABASE email_service_db;"` thủ công 1 lần cho volume hiện có; script vẫn có ích cho môi trường mới/CI sau này.
- Toàn bộ 5 container (`dental-db`, `dental-redis`, `dental-kafka`, `dental-kafka-ui`, `dental-mailhog`) và cả 2 process Java đều đã dừng khi bắt đầu phiên này (máy tắt/Docker Desktop restart 6 ngày trước) — phải `docker compose up -d` + `bootRun` lại từ đầu trước khi verify được gì.

## Verify
- `./gradlew build` cả 2 project đều pass (booking-service: full suite + Jacoco + SpotBugs sạch; email-service: 5 test Mockito).
- Full stack thật: `docker compose up -d` (5 container) + `bootRun` cả 2 service + curl thật:
  - `forgot-password` (admin) → Kafka → email-service log "status=SENT" → MailHog nhận đúng mail "Đặt lại mật khẩu" → `sent_emails` có dòng đúng.
  - `book` + `cancel` (patient mới tạo) → 2 topic còn lại đều tới đúng → MailHog tổng 3 mail đúng người nhận/subject → `sent_emails` có đủ 3 dòng, đều `status=SENT`.
- Regression: `GET /api/appointments` không token vẫn 403, `/v3/api-docs` vẫn 200, `./gradlew dependencies` xác nhận booking-service không còn `spring-boot-starter-mail`.

## Còn lại / bước tiếp theo
- **Phase C**: `customer-care-service` (WebSocket/STOMP chat + MongoDB + JWT verify copy-paste + publish `chat.message-sent`).
- **Phase D**: orchestration/CI/docs đầy đủ cho cả 3 service (bao gồm thêm `email-service` vào docker-compose root như 1 service thật, hiện mới chạy tay qua `bootRun`).
- Chi tiết đầy đủ: `C:\Users\ADMIN\.claude\plans\linked-crunching-dahl.md`.
