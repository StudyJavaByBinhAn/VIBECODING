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

**Đã hoàn thành:**
- Khởi tạo project Spring Boot Gradle (`AntijobApplication.java`).
- Thiết kế entity POJO: User, Patient, Dentist, DentalService, WorkSchedule, Appointment, ClinicSettings.
- Enums: Role, AppointmentStatus, Gender.
- DTOs: AppointmentRequest, AppointmentResponse.
- ApiResponse wrapper + GlobalExceptionHandler.
- SlotService + AppointmentService (in-memory, không DB).
- AppointmentController với các endpoint cơ bản.

**Tiếp theo (Phase 3 — khi có DB):**
- Thêm dependency: JPA, PostgreSQL, Redis, JWT vào `build.gradle`.
- Chuyển POJO → JPA Entity (thêm annotations).
- Thay in-memory store bằng JpaRepository.
- Thêm Spring Security + JWT filter.
- Pessimistic Lock cho `bookAppointment`.
