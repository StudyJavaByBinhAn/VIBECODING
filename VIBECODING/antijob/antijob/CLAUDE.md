# CLAUDE.md — Dental Clinic Booking API

> Đọc file này TRƯỚC khi code. Chi tiết xem `rules/`.

## Project

- **Stack:** Java 21 · Spring Boot 4.1.0 · Gradle · Lombok · (JPA / PostgreSQL / Redis / JWT — sẽ thêm sau)
- **Package:** `com.vibecode.antijob`
- **Mô tả:** REST API đặt lịch hẹn nha khoa — bệnh nhân đặt lịch, chọn bác sĩ, admin quản lý

## Structure

```
entity/    enums/    dto/    service/    controller/    exception/    config/
```

Core files: `SlotService.java` (tính slot trống) · `AppointmentService.java` (đặt/huỷ lịch)

---

## 4 Rules

### 1. Think Before Coding
- Nêu assumption rõ ràng. Không chắc → hỏi.
- Nhiều cách hiểu → trình bày tất cả, không chọn im lặng.
- Có cách đơn giản hơn → nói ra.

### 2. Simplicity First
- Không thêm feature ngoài yêu cầu.
- Không abstraction cho code dùng 1 lần.
- 200 dòng viết được 50 → viết lại.

### 3. Surgical Changes
- Chỉ sửa đúng chỗ cần sửa.
- Không refactor code đang chạy tốt.
- Match style hiện tại.

### 4. Verify Before Done
- `./gradlew build` → compile OK?
- `./gradlew test` → test pass?
- Không nói "xong" khi chưa verify.

---

## Quick Rules

- Entity → DTO thủ công (chưa có MapStruct). **Không** trả entity từ controller.
- `@Transactional` cho mọi write operation (khi có JPA).
- Constructor injection (`@RequiredArgsConstructor`). Không `@Autowired`.
- Pessimistic Lock khi đặt lịch → tránh race condition (khi có DB).
- Response luôn wrap `ApiResponse<T>` — cả success lẫn error.
- Không hardcode. Config trong `application.yml` hoặc `ClinicSettings`.

## Chi tiết

| File | Nội dung |
|------|----------|
| `rules/01-project-context.md` | Tech stack, structure, commands |
| `rules/02-coding-conventions.md` | Naming, entity, DTO, exception patterns |
| `rules/03-domain-rules.md` | Slot logic, race condition, business rules |
| `rules/04-dos-and-donts.md` | Checklist DO / DON'T |

---

## Entity Design (POJO — chưa có JPA)

| Entity | Trường chính |
|--------|-------------|
| `User` | id, email, password, role, createdAt |
| `Patient` | id, user, fullName, phone, dateOfBirth, gender, address, medicalHistory |
| `Dentist` | id, user, fullName, phone, specialization, licenseNumber, bio, isActive |
| `DentalService` | id, name, description, durationMinutes, price, isActive |
| `WorkSchedule` | id, dentist, dayOfWeek, startTime, endTime, isActive |
| `Appointment` | id, patient, dentist, service, appointmentDate, startTime, endTime, status, notes, createdAt, updatedAt |
| `ClinicSettings` | id, clinicName, openTime, closeTime, slotDurationMinutes, maxAdvanceBookingDays |

**Enums:** `Role` (PATIENT/DENTIST/ADMIN/RECEPTIONIST) · `AppointmentStatus` (PENDING/CONFIRMED/CANCELLED/COMPLETED/NO_SHOW) · `Gender` (MALE/FEMALE/OTHER)

---

## Progress Tracking

**Phase 1 — Đã hoàn thành (trước 2026-07-02):**
- Khởi tạo project Spring Boot Gradle (`AntijobApplication.java`).
- Thiết kế entity POJO: User, Patient, Dentist, DentalService, WorkSchedule, Appointment, ClinicSettings.
- Enums: Role, AppointmentStatus, Gender.
- DTOs: AppointmentRequest, AppointmentResponse.
- ApiResponse wrapper + GlobalExceptionHandler.
- SlotService + AppointmentService (in-memory, không DB).
- AppointmentController với các endpoint cơ bản.

**Phase 2 — Hoàn thành 2026-07-02 (DB tích hợp):**
- Thêm dependency JPA, PostgreSQL, Flyway, Lombok vào `build.gradle`.
- Chuyển toàn bộ POJO entity → JPA Entity (`@Entity`, `@Table`, `@Id`, `@Column`, `FetchType.LAZY`).
- Tạo 6 JPA Repositories: Appointment, Patient, Dentist, DentalService, WorkSchedule, ClinicSettings.
- `AppointmentRepository` có `findConflictingForUpdate` (pessimistic lock `@Lock(PESSIMISTIC_WRITE)`).
- Chuyển `AppointmentService` và `SlotService` từ in-memory → dùng JpaRepository thực.
- Cấu hình datasource PostgreSQL + Flyway trong `application.yaml`.
- Tạo `DataInitializer` seed data tự động khi app khởi động lần đầu.
- Tạo `V1__init_schema.sql` migration file (tất cả tables + indexes + seed data).
- App chạy thực tế: API trả data từ PostgreSQL 16, endpoint `/api/appointments` và `/api/slots` hoạt động.
- ⚠️ Flyway chưa tự động chạy với Spring Boot 4.1.0 (cần điều tra thêm — tạm dùng DataInitializer).

**Phase 3 — Kế hoạch tiếp theo (Spring Security + JWT):**
- [ ] Thêm dependency: `spring-boot-starter-security`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson` vào `build.gradle`.
- [ ] `SecurityConfig`: permit `/api/auth/**`, bảo vệ tất cả routes còn lại, stateless session.
- [ ] `JwtUtil`: generate token (email + role + expiry), validate, extract claims.
- [ ] `JwtAuthFilter` (`OncePerRequestFilter`): đọc `Authorization: Bearer <token>`, set SecurityContext.
- [ ] DTOs: `AuthRequest` (email, password), `AuthResponse` (token, role, expiresIn).
- [ ] `AuthController`: `POST /api/auth/register`, `POST /api/auth/login`.
- [ ] `AuthService`: register (tạo User + Patient, hash password BCrypt), login (xác thực + trả JWT).
- [ ] `UserRepository` + `UserDetailsServiceImpl`.
- [ ] `BCryptPasswordEncoder` bean trong SecurityConfig.
- [ ] Phân quyền trên controller: `@PreAuthorize("hasRole('PATIENT')")` cho book, `hasRole('ADMIN')` cho xem tất cả.
- [ ] Flyway: điều tra tại sao Spring Boot 4.1.0 không auto-load `FlywayAutoConfiguration`.
