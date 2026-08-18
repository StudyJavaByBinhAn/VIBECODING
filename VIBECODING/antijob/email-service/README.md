# email-service

Consumer đứng sau `booking-service` (xem `../antijob/`) — không có REST API nghiệp vụ, chỉ tiêu thụ 3 Kafka topic và gửi email thật.

## Vai trò trong hệ thống

`booking-service` publish event lên Kafka khi đặt lịch/huỷ lịch/quên mật khẩu — nó **không** tự gửi email. `email-service` consume 3 topic đó, tự dựng template tiếng Việt, gửi qua MailHog (dev) hoặc SMTP thật, và ghi audit log vào bảng `sent_emails` (Postgres riêng, database `email_service_db`).

| Topic | Trigger | Subject |
|---|---|---|
| `appointment.booked` | Đặt lịch thành công | Xác nhận đặt lịch hẹn |
| `appointment.cancelled` | Huỷ lịch | Huỷ lịch hẹn |
| `auth.password-reset-requested` | Quên mật khẩu | Đặt lại mật khẩu |

Chi tiết đầy đủ (sequence diagram, quyết định thiết kế): `../antijob/docs/system-flows.md` mục 4, và `../antijob/CLAUDE.md` Phase 18 Phase B.

## Chạy dev

Cần hạ tầng chung của `antijob/antijob/` đã chạy trước (Postgres, Kafka, MailHog — `docker compose up -d` ở đó):

```bash
./gradlew bootRun
```

Mặc định port `8090`, kết nối `localhost:9094` (Kafka), `localhost:1025` (MailHog), database `email_service_db` trên cùng Postgres instance với booking-service (`localhost:5432`).

## Kiểm tra nhanh

```bash
curl http://localhost:8090/actuator/health

# Xem audit log đã ghi
docker exec dental-db psql -U postgres -d email_service_db \
  -c "SELECT recipient, subject, event_type, status, sent_at FROM sent_emails ORDER BY id DESC LIMIT 10;"
```

Không có endpoint REST nào để "hỏi" service này đang làm gì — cách duy nhất để biết nó hoạt động là xem log hoặc query `sent_emails`.

## Test

```bash
./gradlew test
```

5 test Mockito thuần (`EmailSenderServiceTest`, `EmailEventListenerTest`) — không cần Postgres/Kafka thật để chạy test.

## Chạy full trong Docker

```bash
cd ../antijob
docker compose --profile full up -d --build
```
