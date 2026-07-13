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
- **Observability:** Spring Boot Actuator (`/actuator/health`) · correlation ID (`X-Request-ID`) trong log qua MDC
- **Test:** JUnit 5 · Mockito · Testcontainers

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

App chạy tại `http://localhost:8080`. Lần khởi động đầu tiên tự seed data mẫu (dentist, service, work schedule, appointment mẫu) + 1 tài khoản ADMIN bootstrap: `admin@vibecode.local` / `admin123` (chỉ dev — xem mục **Chạy production** bên dưới về prod).

Cấu hình đọc qua biến môi trường (DB, Redis, JWT, admin bootstrap, CORS) — xem [.env.example](.env.example). Không set gì thì dùng default dev y hệt như trên. Copy thành `.env` khi cần override; `docker compose` tự đọc file này.

> **Lưu ý cho máy Windows có dải port bị hệ thống reserve** (`netsh interface ipv4 show excludedportrange protocol=tcp`, dải này **đổi mỗi lần Docker Desktop/Hyper-V restart**): nếu `bootRun`/Docker báo "port already in use" dù không process nào đang dùng, đổi port qua tham số runtime thay vì sửa `application.yaml`, ví dụ: `./gradlew bootRun --args="--server.port=9090 --spring.data.redis.port=17000"`.

## Chạy bằng Docker (gần với production hơn)

```bash
docker compose up -d              # Postgres + Redis
./deploy.sh build                 # build image antijob:latest
cp .env.example .env              # chỉnh DB_HOST=dental-db, REDIS_HOST=dental-redis
./deploy.sh run --network antijob_default
```

Xem log: `docker logs -f antijob-app`. Chi tiết trong [Dockerfile](Dockerfile)/[deploy.sh](deploy.sh) — script không giả định nền tảng cloud cụ thể, image build ra chạy được ở bất kỳ đâu có Docker.

**Cách nhanh hơn để chạy full stack (app + Postgres + Redis) chỉ bằng docker-compose, không cần `deploy.sh`:**

```bash
docker compose --profile full up -d --build
```

Service `antijob-app` build image tại chỗ, tự nối `DB_HOST=dental-db`/`REDIS_HOST=dental-redis` qua Docker network, có `HEALTHCHECK` (`/actuator/health`) — `docker ps` sẽ hiện `healthy` khi app sẵn sàng. Mặc định KHÔNG chạy cùng `docker compose up -d` (tránh xung đột port 8080 với dev đang chạy `bootRun` song song) — chỉ bật khi gọi rõ `--profile full`. Đây là cấu hình dev-only (dùng default JWT secret/không set `ADMIN_PASSWORD`); muốn override cho môi trường giống prod thì vẫn dùng `deploy.sh` + `.env` như trên.

## Observability

- `GET /actuator/health` — public (không cần JWT), dùng cho Docker `HEALTHCHECK` + load balancer/orchestrator health probe. Chỉ trả `{"status":"UP"|"DOWN"}`, không chi tiết nội bộ (`show-details: never`).
- Mọi request được gán 1 `requestId` (lấy từ header `X-Request-ID` nếu client gửi, tự sinh UUID nếu không) — trả lại trong response header `X-Request-ID`, và xuất hiện trong mọi dòng log của request đó (`CorrelationIdFilter`, chạy trước cả Spring Security chain). Dùng để nối các dòng log rời rạc của cùng 1 request khi có nhiều request đồng thời.

## Chạy production (`SPRING_PROFILES_ACTIVE=prod`)

Set `SPRING_PROFILES_ACTIVE=prod` (kích hoạt `application-prod.yaml`) khi deploy thật. Khác biệt so với dev:

- **`JWT_SECRET` bắt buộc phải đổi** — app **fail to start** ngay lập tức nếu vẫn dùng giá trị mặc định dev (`StartupSecurityValidator`). Tạo secret mới: `openssl rand -base64 32`.
- **Không tự seed tài khoản ADMIN mặc định** — phải set `ADMIN_PASSWORD` (và tuỳ chọn `ADMIN_EMAIL`) để tạo admin đầu tiên; nếu để trống, app vẫn chạy bình thường nhưng không có admin nào được tạo (log warning, không có backdoor `admin123`).
- `spring.jpa.show-sql` tắt (không log raw SQL ra console/log tổng hợp).
- Set `CORS_ALLOWED_ORIGINS` nếu có frontend gọi API từ trình duyệt trên domain khác (mặc định rỗng = chặn hết, chỉ ảnh hưởng browser).

Ví dụ:
```bash
JWT_SECRET=$(openssl rand -base64 32) \
ADMIN_EMAIL=admin@yourclinic.com \
ADMIN_PASSWORD=<mật khẩu mạnh> \
CORS_ALLOWED_ORIGINS=https://app.yourclinic.com \
./gradlew bootRun --args="--spring.profiles.active=prod"
```

`/api/auth/login` và `/api/auth/register` có rate limit (5 request/60 giây/IP qua Redis, `RateLimitFilter`) — vượt ngưỡng trả `429`. Response lỗi 500 không còn lộ chi tiết exception nội bộ ra client (log đầy đủ ở server qua SLF4J, client chỉ nhận message chung chung).

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
| PATCH | `/api/auth/me/password` | Đã đăng nhập | Đổi mật khẩu (yêu cầu `currentPassword` đúng) |
| POST | `/api/auth/forgot-password` | Public | Tạo reset token (30 phút) — chưa gửi email thật, xem log server để lấy token khi test |
| POST | `/api/auth/reset-password` | Public | Đặt lại mật khẩu bằng token từ forgot-password |
| GET | `/api/appointments` | ADMIN / RECEPTIONIST | Danh sách lịch hẹn (phân trang) |
| GET | `/api/appointments/{id}` | Chủ sở hữu / ADMIN / dentist được giao | Xem 1 lịch hẹn |
| POST | `/api/appointments` | PATIENT | Đặt lịch (không gửi `dentistId` → hệ thống tự chọn bác sĩ) |
| PATCH | `/api/appointments/{id}/cancel` | Chủ sở hữu / ADMIN | Huỷ lịch (phải trước giờ hẹn tối thiểu N giờ, xem `ClinicSettings`) |
| PATCH | `/api/appointments/{id}/confirm` | ADMIN / RECEPTIONIST / dentist được giao | Xác nhận lịch (PENDING → CONFIRMED) |
| PATCH | `/api/appointments/{id}/complete` | ADMIN / RECEPTIONIST / dentist được giao | Đánh dấu đã khám xong (CONFIRMED → COMPLETED) |
| PATCH | `/api/appointments/{id}/no-show` | ADMIN / RECEPTIONIST / dentist được giao | Đánh dấu bệnh nhân không đến (PENDING/CONFIRMED → NO_SHOW) |
| GET | `/api/slots?dentistId=&date=` | Đã đăng nhập | Xem slot trống của 1 bác sĩ |
| GET | `/api/dentists` | Đã đăng nhập | Danh sách bác sĩ đang hoạt động |
| PATCH | `/api/dentists/{id}/active` | ADMIN | Bật/tắt hoạt động của bác sĩ |
| PATCH | `/api/dentists/{id}` | ADMIN | Sửa hồ sơ đầy đủ bác sĩ (fullName/phone/specialization/licenseNumber/bio) |
| GET | `/api/services` | Đã đăng nhập | Danh sách dịch vụ đang hoạt động |
| PATCH | `/api/services/{id}/active` | ADMIN | Bật/tắt hoạt động của dịch vụ |
| POST | `/api/services` | ADMIN | Tạo dịch vụ mới |
| GET | `/api/clinic-settings` | ADMIN | Xem cấu hình phòng khám |
| PUT | `/api/clinic-settings` | ADMIN | Cập nhật cấu hình phòng khám (giờ mở/đóng cửa, buffer, giờ nghỉ trưa...) |
| GET | `/api/dentists/{dentistId}/work-schedules` | ADMIN | Danh sách lịch làm việc của 1 bác sĩ |
| POST | `/api/dentists/{dentistId}/work-schedules` | ADMIN | Tạo lịch làm việc mới (1 ngày/tuần) |
| PUT | `/api/work-schedules/{id}` | ADMIN | Cập nhật giờ làm việc/trạng thái |
| DELETE | `/api/work-schedules/{id}` | ADMIN | Xoá lịch làm việc |
| GET | `/api/patients/me` | PATIENT | Xem hồ sơ bệnh nhân của chính mình |
| PATCH | `/api/patients/me` | PATIENT | Tự sửa hồ sơ (phone/dateOfBirth/gender/address/medicalHistory) |

## Test

```bash
./gradlew test                               # Toàn bộ test (unit + @WebMvcTest + @DataJpaTest)
./gradlew test --tests "*.SlotServiceTest"   # 1 class cụ thể
./gradlew jacocoTestReport                   # Tạo report coverage (build/reports/jacoco/test/html/index.html)
```

3 tầng test tự động:
- **Unit test service-layer** (Mockito thuần): `SlotServiceTest`, `AppointmentServiceTest`, `AuthServiceTest`, ...
- **`@WebMvcTest`** (MockMvc, mock service qua `@MockitoBean`): 1 file cho mỗi trong 7 controller — verify validation DTO → 400, `@PreAuthorize` chặn sai role → 403, happy path đúng response shape.
- **`@DataJpaTest` + Testcontainers** (Postgres thật qua Docker, không dùng H2): `AppointmentRepositoryTest`, `WorkScheduleRepositoryTest` — verify pessimistic lock (`findConflictingForUpdate`) và unique constraint chạy đúng trên dialect Postgres thật. **Cần Docker đang chạy** khi test những class này.

Jacoco gate line coverage tối thiểu 50% (`jacocoTestCoverageVerification`, chạy trong `./gradlew build`/`check`).

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
├── security/     # JWT filter, UserDetailsService, RateLimitFilter
├── service/      # Business logic (SlotService, AppointmentService, ...)
├── controller/   # REST endpoints
└── exception/    # ApiResponse + GlobalExceptionHandler
```

Root project (ngoài `src/`): `docker-compose.yml` (Postgres+Redis dev), `Dockerfile` (multi-stage build image app), `deploy.sh` (build/run image), `.env.example` (biến môi trường).

Chi tiết đầy đủ (progress tracking từng phase, domain rules, coding convention) xem `CLAUDE.md` và `../rules/`.

## CI/CD

GitHub Actions (`.github/workflows/antijob-ci.yml` ở root repo, không nằm trong thư mục này) chạy `./gradlew build` — bao gồm test, Flyway migration thật, `@DataJpaTest` qua Testcontainers (Docker có sẵn trên runner), và Jacoco coverage gate — trên mỗi push/PR đổi code trong `antijob/`, dùng Postgres + Redis service container. Report Jacoco HTML được publish làm artifact sau mỗi lần chạy. Không có bước deploy tự động (chỉ build + test); deploy image sản xuất dùng `deploy.sh` thủ công hoặc pipeline riêng của nơi host.

## Đóng góp

Xem [CONTRIBUTING.md](CONTRIBUTING.md).
