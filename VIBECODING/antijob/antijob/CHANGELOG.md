# Changelog

Định dạng dựa theo [Keep a Changelog](https://keepachangelog.com/). Project chưa gắn semantic version tag, nên đánh dấu theo Phase thay vì số version.

## [Unreleased]

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
