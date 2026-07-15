# Changelog

Định dạng dựa theo [Keep a Changelog](https://keepachangelog.com/). Project chưa gắn semantic version tag, nên đánh dấu theo Phase thay vì số version.

## [Unreleased]

## Phase 17 — 2026-07-15 (sửa 3 bug nghiệp vụ phát hiện qua code review)

### Fixed
- **Double-booking race** cho slot chưa từng có lịch hẹn: `findConflictingForUpdate`'s `SELECT ... FOR UPDATE` chỉ khoá được hàng đã tồn tại, nên 2 request đặt đồng thời cùng 1 slot trống có thể cùng lọt qua. `DentistRepository.findByIdForUpdate` mới khoá hẳn hàng dentist trước khi check trùng, đóng race này.
- **Buffer bị bỏ qua** khi patient chỉ định dentist trực tiếp (`book()` dùng thời gian thô thay vì cộng `buffer_minutes` như `autoAssignDentist`/`SlotService`) — giờ nhất quán cả 2 nhánh.
- **`maxAdvanceBookingDays`/`openTime`** cấu hình được qua admin nhưng chưa từng enforce trong `validateBookingWindow` — patient có thể đặt lịch trước giờ mở cửa hoặc xa vô hạn trong tương lai.

### Added
- `AppointmentServiceTest`: 5 test case mới cho 3 fix trên (openTime, maxAdvanceBookingDays, buffer trên nhánh chỉ định dentist trực tiếp), cập nhật 4 test có sẵn để stub bước lock mới.

## Phase 16 — 2026-07-14 (Micrometer/Prometheus)

### Added
- `io.micrometer:micrometer-registry-prometheus` — Actuator tự cấu hình `PrometheusMeterRegistry` khi có dependency này trên classpath.
- `GET /actuator/prometheus` (`permitAll`, cùng nhóm với `/actuator/health`) — JVM, HTTP request latency/count theo endpoint, cache hit/miss, Hikari DB pool, đều gắn label `application="antijob"`.

### Changed
- `management.endpoints.web.exposure.include` thêm `prometheus` cạnh `health`.

## Phase 15 — 2026-07-14 (JWT logout/revoke)

### Added
- `TokenRevocationService` (`security/`) — lưu mốc "notBefore" theo email trong Redis; token có `issuedAt` trước mốc này bị coi là đã thu hồi. Dùng cho cả logout lẫn đổi/đặt lại mật khẩu (khắc phục giới hạn đã ghi trong đánh giá "sẵn sàng sử dụng" bên dưới: "JWT sống hết 24h dù đổi mật khẩu/logout").
- `POST /api/auth/logout` (yêu cầu đã đăng nhập) — thu hồi mọi token hiện có của user.
- `JwtUtil.extractIssuedAt(token)`.
- `TokenRevocationServiceTest` (4 test) + test mới trong `JwtUtilTest`/`JwtAuthFilterTest`.
- Test `.http` mới: `features/auth-logout-token-revocation.http`.

### Changed
- `AuthService.changePassword`/`resetPassword` giờ thu hồi mọi token cũ ngay sau khi đổi mật khẩu thành công (trước đây token cũ vẫn dùng được tới khi hết hạn 24h dù mật khẩu đã đổi).
- `JwtAuthFilter` chặn token đã bị thu hồi trước khi set `SecurityContext`.

## 2026-07-14 — Đánh giá "sẵn sàng sử dụng"

### Verified
- Audit tĩnh: không còn `TODO`/`FIXME`/secret hardcode nào ngoài `admin123` (dev default có chủ đích).
- Smoke test end-to-end thật nối tiếp 1 luồng nghiệp vụ hoàn chỉnh (không phải test rời rạc): admin setup (clinic-settings, tạo dịch vụ, work-schedule, tạo RECEPTIONIST) → patient tự đăng ký/đặt lịch/sửa hồ sơ → phân quyền chéo đúng (PATIENT 403, RECEPTIONIST 200 trên `GET /appointments`) → RECEPTIONIST confirm/complete lịch → **full flow quên/đặt lại mật khẩu qua email thật** (lấy token từ MailHog, không dùng log token nữa) → rate limiting 429 đúng ngưỡng → Swagger UI/OpenAPI 200.
- **Không tìm thấy bug nào.** Kết luận: dự án sẵn sàng cho mục đích dev/demo/portfolio. Xem chi tiết + giới hạn đã biết trong `CLAUDE.md` mục "Đánh giá sẵn sàng sử dụng".

## Phase 14 — 2026-07-13 (notification + static analysis + CI polish)

### Added
- `spring-boot-starter-mail` + `EmailService` (wrapper `JavaMailSender`, nuốt exception + log lỗi thay vì propagate — gửi mail thất bại không được làm fail transaction đặt/huỷ lịch hay quên mật khẩu).
- Email thật gửi qua MailHog (dev, không gửi ra ngoài internet — SMTP `localhost:1025` không cần auth, xem mail tại UI `localhost:8025`):
  - `AuthService.forgotPassword`: gửi mã reset qua email thay vì log ra server như trước.
  - `AppointmentService.book`: gửi email xác nhận đặt lịch.
  - `AppointmentService.cancel`: gửi email xác nhận huỷ lịch.
- `docker-compose.yml`: service `mailhog` mới (`mailhog/mailhog:v1.0.1`), port `1025` (SMTP) + `8025` (Web UI xem mail đã gửi).
- SpotBugs (`com.github.spotbugs` plugin) — static analysis gắn vào `check`/`build`. Exclude filter (`config/spotbugs/exclude.xml`) loại `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` (false-positive chuẩn cho entity/DTO Lombok) và `CT_CONSTRUCTOR_THROW` (constructor cố tình fail-fast của `JwtUtil`/`StartupSecurityValidator` — đúng thiết kế, không phải bug).
- CI (`antijob-ci.yml`): publish thêm JUnit test report (`junit-test-report`) và SpotBugs report (`spotbugs-report`) làm artifact, cạnh Jacoco report có sẵn.
- `EmailServiceTest` (2 test) + cập nhật `AuthServiceTest`/`AppointmentServiceTest` verify email được gửi đúng người nhận ở các luồng thành công.

### Changed
- `AuthService.forgotPassword` không còn log token reset ra server (chỉ log "đã gửi email cho X", không lộ token) — thay thế hoàn toàn bằng gửi email thật.

## Phase 13 — 2026-07-13 (observability/ops)

### Added
- `spring-boot-starter-actuator`: `GET /actuator/health` (permitAll trong `SecurityConfig`, `show-details: never`) — dùng cho Docker `HEALTHCHECK` và health probe của orchestrator sau này.
- `CorrelationIdFilter` (`config/`) — gán `requestId` (lấy từ header `X-Request-ID` nếu có, tự sinh UUID nếu không) vào MDC cho mọi request, echo lại qua response header `X-Request-ID`. Chạy ở `Ordered.HIGHEST_PRECEDENCE` (trước cả Spring Security chain) để log của mọi filter/service sau đó đều có cùng requestId.
- `logback-spring.xml` — pattern log console thêm `[%X{requestId}]` để log nhiều request đồng thời phân biệt được với nhau.
- `Dockerfile`: cài `curl` ở runtime stage + `HEALTHCHECK` gọi `/actuator/health`.
- `docker-compose.yml`: thêm service `antijob-app` (profile `full`, không chạy cùng `docker compose up -d` mặc định) — build image tại chỗ, tự nối `DB_HOST=dental-db`/`REDIS_HOST=dental-redis`, có healthcheck riêng dùng `curl`.
- `CorrelationIdFilterTest` (4 test).

### Fixed
- Tìm ra lúc verify `docker compose --profile full up`: nếu truyền `JWT_SECRET: ${JWT_SECRET:-}` (hay bất kỳ biến nào tương tự) qua `environment:` mà host không set — compose vẫn set env var đó thành **chuỗi rỗng** trong container (khác "chưa set"), khiến Spring không fallback về default trong `application.yaml` nữa → `JwtUtil` crash vì key 0 bit. Fix: bỏ hẳn `JWT_SECRET`/`ADMIN_EMAIL`/`ADMIN_PASSWORD`/`CORS_ALLOWED_ORIGINS`/`SPRING_PROFILES_ACTIVE` khỏi `environment:` của service `antijob-app` — để trống hoàn toàn thì mới đúng nghĩa "chưa set", cho phép default dev trong `application.yaml` phát huy tác dụng.

## Phase 12 — 2026-07-13 (test coverage tự động)

### Added
- `JwtUtilTest`, `JwtAuthFilterTest` — test generate/validate/expire token, filter set đúng `SecurityContext` khi token hợp lệ và bỏ qua khi thiếu/sai header.
- `AppointmentRepositoryTest`, `WorkScheduleRepositoryTest` (`@DataJpaTest` + Testcontainers Postgres thật, không dùng H2) — verify `findConflictingForUpdate` (pessimistic lock), các query count, và unique constraint `(dentist_id, day_of_week)`.
- `@WebMvcTest` + MockMvc cho toàn bộ 7 controller (`AuthController`, `AppointmentController`, `DentistController`, `DentalServiceController`, `ClinicSettingsController`, `WorkScheduleController`, `PatientController`) — verify validation DTO trả 400, `@PreAuthorize` chặn đúng role trả 403, happy path trả đúng status/shape response. Service layer mock qua `@MockitoBean`, không chạm DB thật.
- `MethodSecurityTestConfig` (`@TestConfiguration @EnableMethodSecurity`, test-only) dùng chung cho mọi `@WebMvcTest` controller — slice test tự loại `SecurityConfig`/`JwtAuthFilter`/`RateLimitFilter` thật (tránh phải mock Redis/JWT) nhưng vẫn enforce đúng `@PreAuthorize`.
- Jacoco (`build.gradle`): `jacocoTestReport` (HTML + XML) chạy sau `test`, `jacocoTestCoverageVerification` gate line coverage tối thiểu 50% gắn vào `check` (baseline thực tế ~58%, không đặt 100% cứng nhắc).
- CI (`antijob-ci.yml`): publish Jacoco HTML report làm artifact sau mỗi lần build.

### Changed
- `build.gradle`: thêm `spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `spring-security-test`. Lưu ý Testcontainers 2.x (kéo theo bởi Spring Boot 4.1.0) đổi hết artifact id sang tiền tố `testcontainers-*` và `PostgreSQLContainer` chuyển sang package `org.testcontainers.postgresql`, không còn generic (`PostgreSQLContainer` thay vì `PostgreSQLContainer<?>`).

## Phase 11 — 2026-07-12

### Added
- `ClinicSettings`: `GET/PUT /api/clinic-settings` (ADMIN) — cấu hình phòng khám giờ chỉnh được qua API thay vì phải sửa migration.
- `WorkSchedule`: CRUD đầy đủ (`GET/POST /api/dentists/{id}/work-schedules`, `PUT/DELETE /api/work-schedules/{id}`, ADMIN) — quản lý giờ làm việc bác sĩ qua API.
- `GET/PATCH /api/patients/me` — patient tự xem/sửa hồ sơ (phone/dateOfBirth/gender/address/medicalHistory).
- `PATCH /api/dentists/{id}` full edit (fullName/phone/specialization/licenseNumber/bio, ADMIN) — trước đó chỉ toggle được `active`.
- `POST /api/services` tạo dịch vụ mới (ADMIN) — trước đó chỉ có seed data, không tạo được qua API.
- `PATCH /api/auth/me/password` đổi mật khẩu (yêu cầu `currentPassword` đúng).
- `POST /api/auth/forgot-password` + `POST /api/auth/reset-password` — tạo/dùng reset token (30 phút, một lần dùng). Chưa tích hợp SMTP — token log ra server-side thay vì gửi email thật.
- `DentistResponse`/`DentalServiceResponse` mở rộng thêm field `phone`/`licenseNumber`/`active` và `active` tương ứng.

### Changed
- `RateLimitFilter` áp dụng thêm cho `/api/auth/forgot-password` (chống dò email hàng loạt).

## Phase 10 — 2026-07-11

### Added
- Vòng đời appointment đầy đủ: `PATCH /api/appointments/{id}/confirm` (PENDING → CONFIRMED), `PATCH /api/appointments/{id}/complete` (CONFIRMED → COMPLETED), `PATCH /api/appointments/{id}/no-show` (PENDING/CONFIRMED → NO_SHOW) — trước đó 3 trạng thái này không có endpoint nào set được.
- RECEPTIONIST có quyền quản lý appointment gần bằng ADMIN: xem danh sách (`GET /api/appointments`), xem chi tiết, confirm/complete/no-show mọi lịch hẹn — không đụng tới user management/register-staff.
- Dentist được giao lịch hẹn tự confirm/complete/no-show được lịch của chính mình (không cần ADMIN/RECEPTIONIST).
- Test `.http` mới: `features/appointment-lifecycle.http`.

## Phase 9 — 2026-07-11

### Added
- Spring profile `prod` (`application-prod.yaml`) — tắt `show-sql`.
- Fail-fast startup check: app không start được nếu `JWT_SECRET` vẫn là giá trị default dev khi chạy `profile=prod` (`StartupSecurityValidator`).
- `ADMIN_EMAIL`/`ADMIN_PASSWORD` env var cho seed admin — prod không tự seed admin mặc định nếu thiếu `ADMIN_PASSWORD` (không còn backdoor `admin123` tự động trên prod).
- CORS config qua `CORS_ALLOWED_ORIGINS` (mặc định rỗng = an toàn).
- Rate limiting cho `/api/auth/login`, `/api/auth/register` (5 request/60s/IP qua Redis, `RateLimitFilter`) → 429 khi vượt ngưỡng.

### Fixed
- `GlobalExceptionHandler` không còn trả `ex.getMessage()` trực tiếp trong response 500 (rò rỉ chi tiết nội bộ) — log đầy đủ server-side, trả message chung chung cho client.

## Phase 8 — 2026-07-09

### Added
- `docker-compose.yml` (Postgres + Redis, named volume — data không mất khi xoá container).
- `Dockerfile` (multi-stage build) + `deploy.sh` (build/run image, không gắn cứng nền tảng cloud).
- `.env.example` + env-var hoá toàn bộ `application.yaml` (DB, Redis, JWT secret) — trước đó hardcode 100%, kể cả JWT secret.
- GitHub Actions CI (`.github/workflows/antijob-ci.yml`, ở root repo) — chạy build+test với Postgres/Redis service container thật trên mỗi push/PR, migration Flyway được verify trong CI thay vì chỉ local.

### Changed
- README: hướng dẫn chạy bằng `docker compose` + hướng dẫn chạy full Docker (`deploy.sh`).

## Phase 7 — 2026-07-08

### Added
- Validation tầng DTO (`jakarta.validation` + `@Valid` trên controller), handler `MethodArgumentNotValidException` → 400.
- Pagination cho `GET /api/appointments` (`PageResponse<T>`, `Pageable`).
- README, CHANGELOG, CONTRIBUTING, Swagger UI (`springdoc-openapi`).

### Changed
- `GET /api/appointments` đổi response shape từ `List<AppointmentResponse>` sang `PageResponse<AppointmentResponse>` (breaking change).
- Dọn `rules/01-project-context.md`, `rules/02-coding-conventions.md` cho khớp code thật (MapStruct, Redis cache, exception vocabulary).

## Phase 6 + MapStruct migration — 2026-07-07

### Added
- Migrate toàn bộ Entity↔DTO mapping thủ công sang MapStruct (`mapper/` package).
- Business rule: buffer time, giờ nghỉ trưa, giờ đóng cửa, loại slot quá khứ trong `SlotService`.
- Business rule: huỷ lịch phải trước N giờ (`cancel_before_hours`), giới hạn số lịch PENDING/patient (`max_pending_appointments`), enforce `dentist.isActive`, auto-assign dentist khi không chỉ định.
- Migration `V2__clinic_settings_business_rules.sql`.
- Unit test đầu tiên của project: `SlotServiceTest`, `AppointmentServiceTest` (Mockito, `Clock` mockable).

## Phase 5 — 2026-07-07

### Added
- Ownership authorization cho `GET /api/appointments/{id}` và `PATCH /api/appointments/{id}/cancel`.
- Endpoint `PATCH /api/dentists/{id}/active`, `PATCH /api/services/{id}/active` (ADMIN).

### Changed
- Thay `GenericJackson2JsonRedisSerializer` (deprecated) → `GenericJacksonJsonRedisSerializer`.

## Phase 4 — 2026-07-04

### Added
- Redis cache cho slots/dentists/services (`@Cacheable`/`@CacheEvict`).
- `POST /api/auth/register-staff` (ADMIN tạo tài khoản DENTIST/ADMIN/RECEPTIONIST).
- `GET /api/dentists`, `GET /api/services`.

### Fixed
- Đặt lịch lấy `patientId` từ JWT thay vì nhận trực tiếp từ request body (đóng lỗ hổng giả mạo).
- Root cause Flyway không tự chạy trên Spring Boot 4 (thiếu module `spring-boot-flyway`).

## Phase 3 — 2026-07-02

### Added
- Spring Security + JWT: `POST /api/auth/register`, `POST /api/auth/login`, phân quyền theo role.

## Phase 2 — 2026-07-02

### Added
- Tích hợp PostgreSQL + JPA, Flyway migration `V1__init_schema.sql`.
- Pessimistic lock chống double-booking.

## Phase 1 — 2026-06-29

### Added
- Khởi tạo project Spring Boot Gradle, entity/DTO/service/controller cơ bản (in-memory, chưa có DB).
