# Dental Clinic Booking API

REST API đặt lịch hẹn nha khoa — bệnh nhân đặt lịch, chọn bác sĩ (hoặc để hệ thống tự chọn), admin quản lý.

## Tech Stack

- **Core:** Java 21 · Spring Boot 4.1.0 · Gradle
- **Data:** PostgreSQL 16 · Spring Data JPA/Hibernate · Flyway
- **Cache:** Redis · Spring Cache (`@Cacheable`/`@CacheEvict`)
- **Auth:** Spring Security · JWT (jjwt)
- **Validation:** Jakarta Bean Validation
- **Mapping:** MapStruct (Entity ↔ DTO)
- **API docs:** springdoc-openapi (Swagger UI)
- **Test:** JUnit 5 · Mockito

## Yêu cầu môi trường

- JDK 21
- Docker (chạy PostgreSQL + Redis)

## Chạy project (dev — app chạy trực tiếp bằng gradlew)

```bash
# 1. Khởi động PostgreSQL + Redis (docker-compose, data giữ qua named volume)
docker compose up -d

# 2. Build + test
./gradlew build

# 3. Chạy app
./gradlew bootRun
```

App chạy tại `http://localhost:8080`. Lần khởi động đầu tiên tự seed data mẫu (dentist, service, work schedule, appointment mẫu) + 1 tài khoản ADMIN bootstrap: `admin@vibecode.local` / `admin123` — **đổi mật khẩu này trước khi lên production.**

Cấu hình đọc qua biến môi trường (DB, Redis, JWT) — xem [.env.example](.env.example). Không set gì thì dùng default dev y hệt như trên. Copy thành `.env` khi cần override; `docker compose` tự đọc file này.

> **Lưu ý cho máy Windows có dải port bị hệ thống reserve** (`netsh interface ipv4 show excludedportrange protocol=tcp`, dải này **đổi mỗi lần Docker Desktop/Hyper-V restart**): nếu `bootRun`/Docker báo "port already in use" dù không process nào đang dùng, đổi port qua tham số runtime thay vì sửa `application.yaml`, ví dụ: `./gradlew bootRun --args="--server.port=9090 --spring.data.redis.port=17000"`.

## Chạy bằng Docker (gần với production hơn)

```bash
docker compose up -d              # Postgres + Redis
./deploy.sh build                 # build image antijob:latest
cp .env.example .env              # chỉnh DB_HOST=dental-db, REDIS_HOST=dental-redis
./deploy.sh run --network antijob_default
```

Xem log: `docker logs -f antijob-app`. Chi tiết trong [Dockerfile](Dockerfile)/[deploy.sh](deploy.sh) — script không giả định nền tảng cloud cụ thể, image build ra chạy được ở bất kỳ đâu có Docker.

## API Documentation

Sau khi chạy app, Swagger UI có sẵn tại:

```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON raw: `http://localhost:8080/v3/api-docs`. Hầu hết endpoint yêu cầu JWT — dùng nút **Authorize** trên Swagger UI, dán token lấy từ `POST /api/auth/login`.

### Tổng quan endpoint

| Method | Path | Quyền | Mô tả |
|--------|------|-------|-------|
| POST | `/api/auth/register` | Public | Đăng ký tài khoản PATIENT |
| POST | `/api/auth/login` | Public | Đăng nhập, trả JWT |
| POST | `/api/auth/register-staff` | ADMIN | Tạo tài khoản DENTIST/ADMIN/RECEPTIONIST |
| GET | `/api/appointments` | ADMIN | Danh sách lịch hẹn (phân trang) |
| GET | `/api/appointments/{id}` | Chủ sở hữu / ADMIN / dentist được giao | Xem 1 lịch hẹn |
| POST | `/api/appointments` | PATIENT | Đặt lịch (không gửi `dentistId` → hệ thống tự chọn bác sĩ) |
| PATCH | `/api/appointments/{id}/cancel` | Chủ sở hữu / ADMIN | Huỷ lịch (phải trước giờ hẹn tối thiểu N giờ, xem `ClinicSettings`) |
| GET | `/api/slots?dentistId=&date=` | Đã đăng nhập | Xem slot trống của 1 bác sĩ |
| GET | `/api/dentists` | Đã đăng nhập | Danh sách bác sĩ đang hoạt động |
| PATCH | `/api/dentists/{id}/active` | ADMIN | Bật/tắt hoạt động của bác sĩ |
| GET | `/api/services` | Đã đăng nhập | Danh sách dịch vụ đang hoạt động |
| PATCH | `/api/services/{id}/active` | ADMIN | Bật/tắt hoạt động của dịch vụ |

## Test

```bash
./gradlew test                               # Toàn bộ unit test
./gradlew test --tests "*.SlotServiceTest"   # 1 class cụ thể
```

Test thủ công qua HTTP: file `.http` trong `src/test/http/` (chạy được trực tiếp bằng REST Client extension của VS Code hoặc IntelliJ HTTP Client) — `appointments.http` cho luồng chính, `features/*.http` mỗi file test 1 tính năng cụ thể.

## Cấu trúc project

```
src/main/java/com/vibecode/antijob/
├── config/       # SecurityConfig, CacheConfig, TimeConfig, OpenApiConfig, DataInitializer
├── entity/       # JPA entities
├── enums/        # Role, AppointmentStatus, Gender
├── repository/   # Spring Data JPA repositories
├── dto/          # Request/Response DTOs
├── mapper/       # MapStruct Entity ↔ DTO
├── security/     # JWT filter, UserDetailsService
├── service/      # Business logic (SlotService, AppointmentService, ...)
├── controller/   # REST endpoints
└── exception/    # ApiResponse + GlobalExceptionHandler
```

Root project (ngoài `src/`): `docker-compose.yml` (Postgres+Redis dev), `Dockerfile` (multi-stage build image app), `deploy.sh` (build/run image), `.env.example` (biến môi trường).

Chi tiết đầy đủ (progress tracking từng phase, domain rules, coding convention) xem `CLAUDE.md` và `../rules/`.

## CI/CD

GitHub Actions (`.github/workflows/antijob-ci.yml` ở root repo, không nằm trong thư mục này) chạy `./gradlew build` — bao gồm test và Flyway migration thật — trên mỗi push/PR đổi code trong `antijob/`, dùng Postgres + Redis service container. Không có bước deploy tự động (chỉ build + test); deploy image sản xuất dùng `deploy.sh` thủ công hoặc pipeline riêng của nơi host.

## Đóng góp

Xem [CONTRIBUTING.md](CONTRIBUTING.md).
