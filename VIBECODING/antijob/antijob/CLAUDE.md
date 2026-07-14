# CLAUDE.md — Dental Clinic Booking API

> Đọc file này TRƯỚC khi code. Chi tiết xem `rules/`.

## Project

- **Stack:** Java 21 · Spring Boot 4.1.0 · Gradle · Lombok · JPA · PostgreSQL · Flyway · Redis · Spring Security · JWT · MapStruct
- **Package:** `com.vibecode.antijob`
- **Mô tả:** REST API đặt lịch hẹn nha khoa — bệnh nhân đặt lịch, chọn bác sĩ, admin quản lý

## Structure

```
entity/    enums/    dto/    mapper/    service/    controller/    exception/    config/
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
| `ClinicSettings` | id, clinicName, openTime, closeTime, slotDurationMinutes, maxAdvanceBookingDays, bufferMinutes, breakStart, breakEnd, cancelBeforeHours, maxPendingAppointments |

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

**Phase 3 — Hoàn thành 2026-07-02 (Spring Security + JWT):**
- [x] Thêm dependency: `spring-boot-starter-security`, `jjwt-api/impl/jackson:0.12.6` vào `build.gradle`.
- [x] `SecurityConfig`: permit `/api/auth/**`, bảo vệ tất cả routes còn lại, stateless session, `@EnableMethodSecurity`.
- [x] `JwtUtil`: generate token (email + role + expiry), validate, extract claims (jjwt 0.12 API).
- [x] `JwtAuthFilter` (`OncePerRequestFilter`): đọc `Authorization: Bearer <token>`, set SecurityContext.
- [x] DTOs: `RegisterRequest` (email, password, fullName, phone), `LoginRequest` (email, password), `AuthResponse` (token, role, expiresIn).
- [x] `AuthController`: `POST /api/auth/register`, `POST /api/auth/login`.
- [x] `AuthService`: register (tạo User role=PATIENT + Patient liên kết, hash password BCrypt), login (xác thực + trả JWT).
- [x] `UserRepository` + `UserDetailsServiceImpl`.
- [x] `BCryptPasswordEncoder` bean trong SecurityConfig.
- [x] Phân quyền trên controller: `@PreAuthorize("hasRole('PATIENT')")` cho book, `hasRole('ADMIN')` cho xem tất cả (`GET /api/appointments`).
- [x] `GlobalExceptionHandler`: thêm handler `AccessDeniedException` → 403 (trước đó bị handler `RuntimeException` bắt nhầm → 500).
- Đã test thực tế qua curl: register/login trả JWT hợp lệ, PATIENT bị 403 ở endpoint ADMIN-only, book thành công.

**Phase 4 — Hoàn thành 2026-07-04:**
- [x] Endpoint book lấy `patientId` từ JWT principal (`Authentication.getName()` → `PatientRepository.findByUserEmail`) thay vì nhận trực tiếp từ request body. `AppointmentRequest.patientId` đã bị xoá khỏi DTO.
- [x] Đăng ký cho DENTIST/ADMIN/RECEPTIONIST: `POST /api/auth/register-staff` (chỉ ADMIN gọi được, `@PreAuthorize("hasRole('ADMIN')")`), DTO `RegisterStaffRequest`. `/api/auth/register` công khai vẫn chỉ tạo PATIENT (đúng chủ đích, không cho public tự tạo ADMIN).
  - `DataInitializer` seed sẵn 1 tài khoản ADMIN bootstrap: `admin@vibecode.local` / `admin123` — **đổi mật khẩu này trước khi lên prod.**
- [x] **Flyway root cause tìm ra:** Spring Boot 4 tách `FlywayAutoConfiguration` ra module riêng `org.springframework.boot:spring-boot-flyway` — chỉ có `flyway-core`/`flyway-database-postgresql` trên classpath (như trước) KHÔNG đủ để autoconfig kích hoạt. Đã thêm dependency thiếu + `baseline-on-migrate: true`, `baseline-version: 1` (vì DB đã có bảng tạo thủ công trước đó, không có `flyway_schema_history`). Verify: `flyway_schema_history` giờ có 1 row BASELINE.
- [x] Redis cache cho slots/dentists/services:
  - `spring-boot-starter-data-redis` + `spring-boot-starter-cache`, Redis chạy Docker (`dental-redis`, port 6379).
  - `CacheConfig`: `RedisCacheManagerBuilderCustomizer` (package đã đổi sang `org.springframework.boot.cache.autoconfigure` trong Boot 4), TTL riêng: `slots`=5 phút, `dentists-active`/`services-active`=1 giờ.
  - `GET /api/dentists`, `GET /api/services` (endpoint mới, không tồn tại trước đó — cần thiết để cache "dentists/services" có ý nghĩa) — cache `@Cacheable`.
  - `SlotService.getAvailableSlots` → `@Cacheable("slots")`; `AppointmentService.book/cancel` → `@CacheEvict("slots", allEntries=true)`.
  - **Bug tìm & fix:** `GenericJackson2JsonRedisSerializer` mặc định dùng `ObjectMapper` không có `JavaTimeModule` → lỗi 500 khi serialize `List<LocalTime>`. Phải tự tạo `ObjectMapper` có `JavaTimeModule` + `activateDefaultTyping`. Ngoài ra Spring Boot 4 đổi JSON mặc định sang Jackson 3 (`tools.jackson`), nhưng `GenericJackson2JsonRedisSerializer` (spring-data-redis) vẫn dùng Jackson 2 cổ điển (`com.fasterxml.jackson`) — phải thêm `jackson-databind`/`jackson-datatype-jsr310` 2.x làm dependency riêng.
- Đã verify thực tế qua curl: slots cache hit/evict đúng, dentists/services cache đúng, register-staff 403 khi không phải ADMIN, book dùng đúng patient từ JWT.

**Phase 5 — Đang làm (bắt đầu 2026-07-06):**
- [x] Ownership authorization cho `GET /appointments/{id}` và `PATCH /appointments/{id}/cancel`:
  - `AppointmentService.assertCanAccess()`: ADMIN luôn được phép; PATIENT chỉ được phép trên lịch hẹn của chính mình (so khớp `Authentication.getName()` với `appointment.getPatient().getUser().getEmail()`); DENTIST được **xem** (không được huỷ) lịch hẹn được giao cho mình.
  - Không đủ quyền → ném `AccessDeniedException` → `GlobalExceptionHandler` trả 403 (tái dùng handler có sẵn từ Phase 3).
  - `findById`/`cancel` ở `AppointmentService` và `AppointmentController` đổi signature nhận thêm `Authentication`.
  - Test case: `src/test/http/features/appointment-ownership.http` (folder mới `features/` — mỗi tính năng từ đây có 1 file `.http` riêng, không dồn hết vào `appointments.http`).
  - Verify qua curl thực tế: A xem/huỷ được lịch của A (200), B xem/huỷ lịch của A bị chặn (403), ADMIN xem được mọi lịch (200). Regression: `/appointments` ADMIN-only và không token vẫn đúng như cũ.
  - **Lưu ý môi trường:** máy dev Windows này có dải port `7987-8086` bị OS reserve (`netsh interface ipv4 show excludedportrange protocol=tcp`) nên `server.port=8080` mặc định luôn bind fail — verify phải chạy tạm với `--args="--server.port=9090"`. Không phải bug code, nhưng cần nhớ khi verify lần sau trên máy này.
- [x] Thay `GenericJackson2JsonRedisSerializer` (deprecated) → `GenericJacksonJsonRedisSerializer` (spring-data-redis 4.1.0, gói `org.springframework.data.redis.serializer`, dùng Jackson 3 `tools.jackson.databind.ObjectMapper` thay vì Jackson 2 cổ điển):
  - `CacheConfig`: dùng `GenericJacksonJsonRedisSerializer.builder().enableUnsafeDefaultTyping().build()` — không cần tự tạo `ObjectMapper` + `JavaTimeModule` thủ công nữa vì Jackson 3 `jackson-databind` đã hỗ trợ `java.time` sẵn trong core (không cần module riêng như Jackson 2's `jackson-datatype-jsr310`).
  - Xoá 2 dependency `com.fasterxml.jackson.core:jackson-databind:2.21.4` và `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.4` khỏi `build.gradle` — chỉ tồn tại vì serializer cũ, không còn cần thiết (Jackson 2.x vẫn có mặt gián tiếp qua `jjwt-jackson`, không do project khai báo nữa).
  - Verify qua curl: `/slots` (chứa `List<LocalTime>`), `/dentists`, `/services` — cache miss lần đầu, cache hit lần 2 trả đúng dữ liệu; `docker exec dental-redis redis-cli KEYS '*'` xác nhận đúng 3 key (`slots::`, `dentists-active::all`, `services-active::all`); log không còn lỗi serialize.
- [x] Endpoint ADMIN cập nhật `active` cho Dentist/DentalService (quyết định: nên có — nếu không thì dentist/service nghỉ việc vẫn hiện trong danh sách active vô thời hạn):
  - `PATCH /api/dentists/{id}/active` và `PATCH /api/services/{id}/active`, body `{"active": true|false}`, `@PreAuthorize("hasRole('ADMIN')")`.
  - DTO dùng chung `UpdateActiveRequest` (chỉ 1 field `active`) cho cả 2 endpoint.
  - `DentistService.updateActive` / `DentalServiceCatalogService.updateActive`: `@CacheEvict(allEntries = true)` đúng cache tương ứng (`dentists-active` / `services-active`) sau khi save — bắt buộc vì key cache cố định `'all'`.
  - Verify qua curl: PATIENT gọi bị 403; ADMIN tắt active → dentist/service biến mất khỏi list ngay (cache evict đúng, không stale); bật lại → xuất hiện lại; id không tồn tại → 400.

**Phase 5 hoàn thành 2026-07-07.** Cả 3 mục đều đã xong, build + verify pass toàn bộ.

**Việc phát sinh — Migrate sang MapStruct (2026-07-07, trước khi bắt đầu Phase 6):**
- [x] Thêm dependency: `org.mapstruct:mapstruct:1.6.3` (implementation), `mapstruct-processor:1.6.3` (annotationProcessor), `lombok-mapstruct-binding:0.2.0` (annotationProcessor — bắt buộc để MapStruct processor thấy được getter Lombok sinh ra trong cùng vòng compile, thiếu dòng này sẽ lỗi biên dịch mapper).
- [x] Package mới `mapper/`: `AppointmentMapper` (dùng `@Mapping(target=..., source=...)` cho 3 field flatten từ nested entity: `patientName`←`patient.fullName`, `dentistName`←`dentist.fullName`, `serviceName`←`service.name`; các field còn lại tên khớp trực tiếp nên MapStruct tự map không cần khai báo), `DentistMapper`, `DentalServiceMapper` (2 mapper sau field khớp tên hoàn toàn, không cần `@Mapping`).
- [x] Xoá 3 method `toResponse()` thủ công (`AppointmentService`, `DentistService`, `DentalServiceCatalogService`), inject mapper qua constructor thay thế.
- [x] `AuthService` **không đổi** — build `AuthResponse` từ token/role, không phải map trực tiếp 1 entity, không phù hợp MapStruct.
- [x] Verify: `./gradlew build` pass (mapper impl sinh đúng tại `build/generated/sources/annotationProcessor/java/main/com/vibecode/antijob/mapper/`), curl `/api/appointments`, `/api/dentists`, `/api/services` xác nhận JSON response giữ nguyên hình dạng như trước migrate.
- [x] Cập nhật `rules/02-coding-conventions.md` (dòng về DTO mapping) + Stack/Structure ở đầu file này.

**Phase 6 — Hoàn thành 2026-07-07:**

Đóng toàn bộ khoảng cách giữa `rules/03-domain-rules.md`/`04-dos-and-donts.md` và code thực tế. Default values các config mới đã chốt trực tiếp với user: `buffer_minutes=10`, `break_start/end=12:00/13:00`, `cancel_before_hours=12`, `max_pending_appointments=3`; `close_time` giữ nguyên giá trị seed sẵn `17:00`.

- [x] **1. Migration `V2__clinic_settings_business_rules.sql`** — thêm 5 cột vào `clinic_settings` (`buffer_minutes`, `break_start`, `break_end` nullable, `cancel_before_hours`, `max_pending_appointments`), `UPDATE` set break 12:00–13:00 cho row seed hiện có. `ClinicSettings` entity thêm field tương ứng.
- [x] **2. `SlotService`** — áp dụng đúng công thức domain rule đầy đủ:
  - Trừ `buffer_minutes` giữa 2 ca đã đặt (đệm cả 2 phía overlap check).
  - Loại slot rơi vào `break_start`–`break_end` (bỏ qua nếu null — clinic không nghỉ trưa).
  - Chặn cứng slot không vượt `close_time`, kể cả khi `work_schedule` của dentist cho phép muộn hơn.
  - Loại slot quá khứ khi `date == hôm nay` (so với `LocalTime.now(clock)`).
  - Inject `java.time.Clock` qua constructor (bean mới `config/TimeConfig.java`) để mock "now" trong test — không đổi public signature `getAvailableSlots`, cache key không bị ảnh hưởng.
  - `SlotServiceTest` (10 test, Mockito + `Clock.fixed`): no-schedule→rỗng, normal case, overlap không buffer, buffer loại slot liền kề 2 phía, break loại đúng slot, không break→không loại, vượt close_time bị cắt dù work_schedule cho phép, hôm nay loại slot quá khứ, ngày tương lai không áp past-filter, thiếu `ClinicSettings`→fallback default (khác hành vi hard-fail của `AppointmentService`, có chủ đích — 2 method độc lập).
- [x] **3. `AppointmentService.cancel()`** — chặn huỷ nếu còn dưới `cancel_before_hours` giờ nữa là đến giờ hẹn (`LocalDateTime.now(clock).plusHours(...)` so với `appointmentDate+startTime`), ném `IllegalArgumentException` (400, tái dùng convention có sẵn, không phải case 409).
- [x] **4. `AppointmentService.book()`**:
  - Giới hạn `max_pending_appointments`/patient (query mới `countByPatientIdAndStatus`) — check ngay sau resolve patient, trước khi đụng tới dentist/service (`verifyNoInteractions` xác nhận trong test).
  - `is_active` check cho dentist — áp dụng **cả 2 nhánh** (dentistId tường minh dùng `.filter(Dentist::isActive)`, và auto-assign vốn đã chỉ query `findByActiveTrue()`).
  - Auto-assign dentist khi `dentistId == null` (đã nullable sẵn, không cần đổi DTO) — **không** gọi lại `SlotService` (quyết định của user, chấp nhận trùng lặp nhỏ logic buffer) mà tự lọc: dentist active + có `WorkSchedule` phù hợp ngày/giờ + không conflict (đệm buffer qua `findConflictingForUpdate`), tie-break bằng query mới `countByDentistIdAndAppointmentDateAndStatusNot` (ít lịch nhất trong ngày thắng).
  - Validate chung `validateBookingWindow()` (ngày ≥ hôm nay, ngoài giờ nghỉ trưa, không vượt close_time) áp dụng 1 lần trước khi rẽ nhánh dentist, dùng cho cả 2 nhánh.
  - `clinic_settings` rỗng → hard-fail `IllegalStateException` ở mọi rule mới (khác `SlotService` fallback — quyết định của user).
  - `AppointmentServiceTest` (15 test, Mockito): happy path, conflict, ngày quá khứ, break window, sau close_time, max-pending đạt ngưỡng, dentist tường minh inactive, auto-assign 1/nhiều/0 candidate (tie-break đúng), settings rỗng, cancel đủ sớm/trong hạn/status sai/ownership.
- [x] **5. Test case `.http` mới trong `features/`**: `slot-buffer-break-close-time.http`, `appointment-cancel-deadline.http`, `appointment-max-pending.http`, `appointment-auto-assign.http`, `appointment-invalid-window.http`.
- [x] **6. Đồng bộ docs** — `rules/03-domain-rules.md` cập nhật bảng Business Rules + ghi chú "đã implement Phase 6"; `CLAUDE.md` Entity Design table thêm 5 field `ClinicSettings` mới.
- [x] **7. Verify toàn bộ** — `./gradlew build` pass với 25 unit test mới (`SlotServiceTest` 10, `AppointmentServiceTest` 15) + `contextLoads()`. `bootRun --args="--server.port=9090"` + curl thực tế xác nhận đúng: V2 migration tự chạy sạch trên DB đã baseline; buffer/break đúng slot bị loại (kể cả với data thật có sẵn appointment cũ); ngày quá khứ/giờ nghỉ trưa/sau đóng cửa đều 400 đúng message; max-pending 400 ở lịch PENDING thứ 4; huỷ trong vòng 12h thực (so với giờ hệ thống thật) bị 400, huỷ đủ sớm 200; auto-assign chọn đúng dentist ít lịch hơn (BS B thay vì BS A đang bận). Log không có lỗi ngoài dự kiến.

**Phase 7 — Hoàn thành 2026-07-08:**

Khảo sát lại toàn bộ project (không có kế hoạch sẵn) phát hiện 3 gap thật, user chọn làm cả 3: validation tầng DTO, pagination, dọn docs lỗi thời.

- [x] **1. Validation tầng DTO** — thêm `spring-boot-starter-validation` (chưa từng có trong project dù `rules/01-project-context.md` ghi sai là đã có). Annotate `jakarta.validation.constraints.*` lên `AppointmentRequest` (`serviceId`/`appointmentDate`/`startTime` `@NotNull`, `notes` `@Size(max=500)`, `dentistId` cố tình KHÔNG `@NotNull` vì null = auto-assign), `RegisterRequest`/`RegisterStaffRequest` (`email` `@NotBlank @Email`, `password` `@NotBlank @Size(min=6)`, `fullName`/`phone` `@NotBlank`, `role` `@NotNull` ở staff), `LoginRequest` (`email`/`password` `@NotBlank`, cố tình KHÔNG `@Email` — tránh lộ thông tin phân biệt "sai định dạng" vs "sai mật khẩu"). `@Valid` thêm vào mọi `@RequestBody` ở `AppointmentController`/`AuthController`/`DentistController`/`DentalServiceController`. `GlobalExceptionHandler` thêm handler `MethodArgumentNotValidException` → 400, gộp lỗi nhiều field thành 1 message nối `"; "` (không thêm field `errors` mới vào `ApiResponse`, giữ nguyên cấu trúc response cũ).
- [x] **2. Pagination cho `GET /api/appointments`** — DTO mới `PageResponse<T>` (`content`/`page`/`size`/`totalElements`/`totalPages`). `AppointmentService.findAll(Pageable)` dùng `appointmentRepository.findAll(pageable)` có sẵn từ `JpaRepository` (không cần method repository mới). Controller nhận `@PageableDefault(size=20, sort="appointmentDate", direction=DESC)`. **Quyết định:** CHỈ áp dụng cho `/api/appointments` (bảng phình to theo thời gian) — KHÔNG áp dụng cho `/api/dentists`/`/api/services` (danh sách nhỏ, bị chặn quy mô, đang cache key cố định `'all'`, thêm pagination phá cache mà không giải quyết rủi ro thật). Breaking change response shape (`List<T>` → `PageResponse<T>`) — đã cập nhật `appointments.http` cho khớp.
- [x] **3. Dọn docs** — `rules/02-coding-conventions.md`: sửa ví dụ DTO (record→class thật có validation), bảng exception (3 class ảo `ResourceNotFoundException`/`SlotAlreadyBookedException`/`InvalidAppointmentException` → vocabulary thật `IllegalArgumentException`/`IllegalStateException`/`AccessDeniedException`/`MethodArgumentNotValidException`/`RuntimeException`), section Caching (viết lại mô tả đúng Redis đã có từ Phase 4, không còn "chưa triển khai"), sửa luôn `ApiResponse` example (record sai → class thật, không có `timestamp`/`errors` field). `rules/01-project-context.md`: sửa dòng Mapping (MapStruct), thêm `mapper/` vào structure diagram, thêm `CacheConfig`/`TimeConfig` vào mô tả `config/`, thêm lệnh docker Redis.
- [x] **4. Test case `.http` mới**: `features/dto-validation.http`, `features/appointment-pagination.http`.
- [x] **5. Verify toàn bộ** — `./gradlew build` pass, 26/26 test (25 cũ + `contextLoads`). `bootRun --args="--server.port=9090 --spring.data.redis.port=17000"` + curl xác nhận: mọi case validation trả đúng 400 kèm message field; pagination page 0/1 trả đúng nội dung khác nhau, `totalElements`/`totalPages` đúng; regression book/register hợp lệ vẫn 200. **Gotcha môi trường mới:** máy dev này giờ còn bị Windows exclude cả dải port quanh `6316-6415` (chặn Redis mặc định `6379`) — dải exclude là **động**, đổi mỗi lần Docker Desktop/Hyper-V restart, khác hẳn dải `7987-8086` ghi nhận ở Phase 5. Không sửa `application.yaml`; verify lần này phải map Redis container sang port khác (`17000`) + override runtime bằng `--spring.data.redis.port=17000`, giống cách xử lý cho `server.port`. Từ nay mỗi lần verify trên máy này: `netsh interface ipv4 show excludedportrange protocol=tcp` để biết port nào đang bị chặn trước khi chạy `bootRun`/docker.

**Việc phát sinh — Project hygiene docs + Swagger (2026-07-08, cùng ngày, sau Phase 7):**
- [x] `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md` mới tại root `antijob/antijob/` (chưa từng có trước đó).
- [x] Swagger UI: thêm `springdoc-openapi-starter-webmvc-ui:2.8.5` — tương thích tốt với Spring Boot 4.1.0 dù là bản mới nhất target Boot 3.x (không gặp vấn đề version-mismatch như Flyway/Redis serializer trước đây). `config/OpenApiConfig.java` khai báo metadata + security scheme `bearerAuth` (JWT) để nút Authorize trên Swagger UI hoạt động.
- [x] `SecurityConfig`: thêm `permitAll()` cho `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` — không đổi hành vi các route khác (đã verify `/api/appointments` không token vẫn 403 như cũ).
- [x] Verify: `curl http://localhost:9090/v3/api-docs` trả đúng OpenAPI JSON với `security: [{"bearerAuth":[]}]`; `curl http://localhost:9090/swagger-ui/index.html` → 200.

**Phase 8 — Infra hygiene (Hoàn thành 2026-07-09):**

User chỉ ra 4 gap hạ tầng thật (đã verify đúng bằng cách đọc code trước khi làm): không có docker-compose, không có deployment script, `application.yaml` hardcode 100% (kể cả JWT secret), Flyway chưa được test trong CI vì không có pipeline nào.

- [x] **1. `docker-compose.yml`** — Postgres + Redis, named volume (`dental-db-data`, `dental-redis-data`) để không mất data khi xoá container (khắc phục luôn pain point đã note từ Phase 1: trước đây `docker run` thủ công không có volume). Env var có default khớp giá trị cũ (`DB_PASSWORD:-postgres`...).
- [x] **2. Env-var hoá `application.yaml`** — `${DB_HOST:localhost}`, `${DB_PORT:5432}`, `${DB_NAME:dental_db}`, `${DB_USERNAME:postgres}`, `${DB_PASSWORD:postgres}`, `${REDIS_HOST:localhost}`, `${REDIS_PORT:6379}`, `${JWT_SECRET:...}`, `${JWT_EXPIRATION_MS:86400000}` — default giữ nguyên y hệt giá trị hardcode cũ nên **không breaking** cho dev flow hiện tại. `.env.example` mới liệt kê đầy đủ + note quan trọng: chạy app trong container thì phải set `DB_HOST=dental-db`/`REDIS_HOST=dental-redis` (không phải `localhost`) và join network của compose.
- [x] **3. `Dockerfile`** (multi-stage: `eclipse-temurin:21-jdk` build → `eclipse-temurin:21-jre` runtime, `bootJar -x test`) + `.dockerignore` + `deploy.sh` (build/run tổng quát, không gắn cứng nền tảng cloud cụ thể — user xác nhận chưa có hạ tầng cloud nào để nhắm tới).
- [x] **4. GitHub Actions CI** (`e:\gochocTap\.github\workflows\antijob-ci.yml` — **đặt ở root repo git thật** `e:\gochocTap`, KHÔNG nằm trong `antijob/`, vì GitHub chỉ đọc workflow từ `.github/workflows/` ở repo root; đã confirm bằng `git rev-parse --show-toplevel`). Trigger chỉ khi đổi `VIBECODING/antijob/**` (path filter) để không chạy nhầm khi sửa project khác trong cùng repo (vd. `ANTIGRAVITYJOBS`). Dùng Postgres+Redis service container thật, `./gradlew build` chạy migration Flyway thật trong CI (đóng đúng gap #4 user nêu).
- [x] **5. Verify end-to-end thật** (không chỉ compile): `docker compose up -d` → 2 container healthy → `bootRun` không override gì (dùng default env) → login vẫn 200 (regression-free) → `./deploy.sh build` build image thành công → `./deploy.sh run --network antijob_default` với `.env` trỏ `DB_HOST=dental-db`/`REDIS_HOST=dental-redis` → container tự kết nối đúng qua Docker network DNS, login + Swagger UI qua port 8080 của container đều 200. Dọn container/`.env` test sau khi verify xong.
- [x] **6. Docs**: README (docker-compose + full-Docker run instructions, bảng CI/CD), CONTRIBUTING (mục CI mới), CHANGELOG (Phase 8 entry).

**Phase 9 — Security hardening (Hoàn thành 2026-07-11):**

3 agent khảo sát song song (business/security/test coverage) phát hiện nhiều gap thật khi đọc code trực tiếp. User chốt chỉ làm Phase 9 (bảo mật — duy nhất có rủi ro khai thác thật) đợt này, Phase 10-14 (vòng đời appointment/RECEPTIONIST, admin CRUD ClinicSettings/WorkSchedule, profile self-service, test coverage, observability, notification) để làm roadmap tham khảo sau — xem đầy đủ trong `C:\Users\ADMIN\.claude\plans\ti-p-t-c-c-ng-vi-c-parsed-hinton.md`.

- [x] **1. Spring profile `prod`** — `application-prod.yaml` mới, tắt `show-sql`. Kích hoạt qua `SPRING_PROFILES_ACTIVE=prod`, không set = dev y hệt cũ.
- [x] **2. Fail-fast JWT_SECRET** — `config/StartupSecurityValidator.java` (`ApplicationRunner`): nếu profile=prod VÀ `jwt.secret` vẫn bằng đúng giá trị default hardcode → `throw IllegalStateException`, chặn app start hẳn (verify thật: app crash đúng như thiết kế khi thiếu `JWT_SECRET` ở prod).
- [x] **3. Gate ADMIN seed** — `DataInitializer.seedAdmin()` thêm `ADMIN_EMAIL`/`ADMIN_PASSWORD` (default password RỖNG, không phải `admin123`). Dev (không profile): vẫn seed `admin123` như cũ nếu không set gì (không breaking). Prod: nếu `ADMIN_PASSWORD` rỗng → **bỏ qua seed hoàn toàn**, chỉ log warning — không còn admin backdoor mặc định trên prod. Verify thật cả 2 nhánh (skip có warning đúng, và seed đúng khi có `ADMIN_PASSWORD`/`ADMIN_EMAIL` custom).
- [x] **4. `GlobalExceptionHandler` không lộ chi tiết lỗi** — `handleRuntime` giờ `log.error(ex)` server-side (SLF4J, đã thêm `@Slf4j`) + trả message chung chung cho client thay vì `"Internal error: " + ex.getMessage()`.
- [x] **5. CORS** — `CorsConfigurationSource` bean trong `SecurityConfig`, đọc `CORS_ALLOWED_ORIGINS` (comma-separated, rỗng mặc định = chặn hết). Verify thật: preflight OPTIONS từ origin được phép → 200 + đúng header; origin lạ → 403.
- [x] **6. Rate limiting** — `security/RateLimitFilter.java` (`OncePerRequestFilter`, dùng `StringRedisTemplate` có sẵn, không thêm lib mới): tối đa 5 request/60s/IP cho `/api/auth/login` + `/api/auth/register`, vượt ngưỡng → 429 ngay tại filter. Verify thật: 5 lần đầu qua bình thường (400 do sai password), lần 6-7 → 429.
- [x] **7. Gotcha filter ordering**: `addFilterBefore(rateLimitFilter, JwtAuthFilter.class)` **lỗi runtime** (`IllegalArgumentException` tại `HttpSecurity`) vì Spring Security chỉ chấp nhận anchor filter là well-known filter class đã có vị trí xác định, không phải filter custom khác chưa đăng ký. Fix: cả `rateLimitFilter` và `jwtAuthFilter` cùng anchor vào `UsernamePasswordAuthenticationFilter.class` (well-known filter) — bài học chung khi thêm filter custom mới vào chain sau này.
- [x] **8. Verify toàn bộ**: `./gradlew build` 26/26 test pass; `bootRun` prod thiếu `JWT_SECRET` → fail to start đúng thiết kế; prod đủ env → start OK, `show-sql` tắt, admin seed đúng theo `ADMIN_PASSWORD`; regression register/login/Swagger/403 không đổi.
- [x] **9. Docs**: `.env.example` (JWT_SECRET/ADMIN_EMAIL/ADMIN_PASSWORD/CORS_ALLOWED_ORIGINS/SPRING_PROFILES_ACTIVE), README (mục "Chạy production" mới), CHANGELOG (Phase 9 entry).

**Phase 10 — Vòng đời appointment + RECEPTIONIST (Hoàn thành 2026-07-11):**

Trước phase này, `AppointmentStatus` có 5 giá trị nhưng chỉ 2 giá trị (`PENDING`/`CANCELLED`) đạt được qua API — `CONFIRMED`/`COMPLETED`/`NO_SHOW` không có endpoint nào set, và `RECEPTIONIST` là role rỗng hoàn toàn (đăng nhập được nhưng không làm được gì). User chốt phạm vi RECEPTIONIST trước đó (xem roadmap Phase 9): quyền gần bằng ADMIN trên appointment (xem tất cả + confirm/complete/no-show mọi lịch), không đụng user management.

- [x] **1. 3 endpoint mới trong `AppointmentController`**: `PATCH /api/appointments/{id}/confirm` (PENDING→CONFIRMED), `PATCH /api/appointments/{id}/complete` (CONFIRMED→COMPLETED), `PATCH /api/appointments/{id}/no-show` (PENDING/CONFIRMED→NO_SHOW). Không có `@PreAuthorize` — quyền check hoàn toàn ở service layer (giống pattern `cancel` có sẵn), vì logic phân quyền phụ thuộc dữ liệu (dentist được giao) chứ không chỉ role tĩnh.
- [x] **2. `AppointmentService.assertCanManage()`** (method mới) — ADMIN và RECEPTIONIST quản lý được mọi lịch hẹn; DENTIST chỉ quản lý được lịch hẹn được giao cho mình; PATIENT không có quyền (ném `AccessDeniedException` → 403 qua handler có sẵn).
- [x] **3. `assertCanAccess()` đổi signature** — thêm tham số `allowReceptionist`: `findById` cho RECEPTIONIST xem full như ADMIN (`allowReceptionist=true`), `cancel` giữ nguyên hành vi cũ không cho RECEPTIONIST (`allowReceptionist=false`, đúng phạm vi đã chốt — cancel không nằm trong quyền RECEPTIONIST).
- [x] **4. `GET /api/appointments`** đổi `@PreAuthorize` từ `hasRole('ADMIN')` → `hasRole('ADMIN') or hasRole('RECEPTIONIST')`.
- [x] **5. Refactor nhỏ**: tách `getAppointmentOrThrow(id)` dùng chung cho `findById`/`cancel`/`confirm`/`complete`/`markNoShow` (trước đó lặp lại `findById().orElseThrow()` ở 2 chỗ, giờ 5 chỗ nên tách).
- [x] **6. `AppointmentServiceTest`**: 12 test mới cho confirm/complete/markNoShow (theo role ADMIN/RECEPTIONIST/dentist-được-giao/dentist-không-được-giao/PATIENT, theo status hợp lệ/không hợp lệ) + 1 test `findById` xác nhận RECEPTIONIST xem được lịch không phải của mình.
- [x] **7. Test `.http` mới**: `features/appointment-lifecycle.http` — full flow PENDING→CONFIRMED→COMPLETED qua confirm/complete, PENDING→NO_SHOW trực tiếp, phân quyền RECEPTIONIST/PATIENT trên cả action lẫn `GET /api/appointments`. **Lưu ý**: `RegisterStaffRequest.phone` là `@NotBlank` kể cả cho role RECEPTIONIST/ADMIN (không có entity hồ sơ dùng đến field này) — phải điền phone khi test `register-staff`, không phải bug mới của Phase 10.
- [x] **8. Verify toàn bộ**: `./gradlew build` 37/37 test pass. `bootRun` (dev) + curl thực tế xác nhận đúng 10 kịch bản: PATIENT bị 403 khi tự confirm lịch của mình, RECEPTIONIST confirm/complete/no-show đúng transition, confirm/no-show sai trạng thái → 400 đúng message, RECEPTIONIST xem `GET /api/appointments` → 200, PATIENT bị 403. Regression: `cancel` endpoint cũ không đổi hành vi.

**Phase 11 — Admin CRUD config + profile self-service (Hoàn thành 2026-07-12):**

Theo roadmap đã chốt ở `C:\Users\ADMIN\.claude\plans\ti-p-t-c-c-ng-vi-c-parsed-hinton.md` (Phase 11). Quyết định đã chốt trước đó: **không tích hợp email thật** — quên mật khẩu dừng ở bước tạo token, không gửi mail.

- [x] **1. `ClinicSettings`**: `GET/PUT /api/clinic-settings` (ADMIN, `@PreAuthorize` ở class-level). `ClinicSettingsMapper` (MapStruct, chỉ `toResponse`) + `ClinicSettingsAdminService` (update dùng manual setter, giống pattern `updateActive` có sẵn — không dùng MapStruct `@MappingTarget` để nhất quán với style hiện tại). `update()` có `@CacheEvict("slots")` vì các field này (buffer/break/close time) ảnh hưởng trực tiếp `SlotService`.
- [x] **2. `WorkSchedule`**: CRUD đầy đủ (`WorkScheduleController`, base `/api`, `@PreAuthorize` class-level ADMIN) — `GET/POST /api/dentists/{dentistId}/work-schedules`, `PUT/DELETE /api/work-schedules/{id}`. `WorkScheduleService` validate `startTime < endTime` trước khi chạm DB, và pre-check trùng `dayOfWeek` qua `findByDentistIdAndDayOfWeek` (dựa vào unique constraint `uq_schedule` có sẵn từ V1, nhưng check ở service để trả 400 sạch thay vì lộ constraint violation). `dayOfWeek` không sửa được qua `update()` (immutable sau khi tạo — muốn đổi ngày thì xoá tạo lại, tránh phải re-check uniqueness phức tạp).
- [x] **3. `GET/PATCH /api/patients/me`**: `PatientController`/`PatientService`/`PatientMapper` mới. `updateMe()` chỉ set 5 field theo đúng roadmap (phone/dateOfBirth/gender/address/medicalHistory) — **không** cho sửa `fullName` (ngoài phạm vi đã chốt).
- [x] **4. `PATCH /api/dentists/{id}` full edit** — `UpdateDentistRequest` (fullName/phone/specialization/licenseNumber/bio). Thêm `DentistRepository.existsByLicenseNumberAndIdNot()` để pre-check trùng license (cột có `UNIQUE` constraint từ V1) → trả 400 sạch thay vì lộ `DataIntegrityViolationException` thành 500. `DentistResponse` mở rộng thêm `phone`/`licenseNumber`/`active` (trước đó thiếu, nên sau khi sửa xong không nhìn thấy lại field vừa sửa).
- [x] **5. `POST /api/services`**: `DentalServiceCatalogService.create()` — dùng builder tạo entity thủ công (giống pattern `AppointmentService.book()`), set `active=true` mặc định. `DentalServiceResponse` mở rộng thêm `active`.
- [x] **6. Đổi mật khẩu + quên/đặt lại mật khẩu**:
  - `PATCH /api/auth/me/password` (`ChangePasswordRequest`: currentPassword/newPassword) — verify `currentPassword` bằng `passwordEncoder.matches()` trước khi đổi.
  - `POST /api/auth/forgot-password` — luôn trả message chung chung dù email không tồn tại (tránh account enumeration); nếu tồn tại thì sinh `UUID` reset token, lưu `resetToken`/`resetTokenExpiry` (30 phút) vào `User`, **log ra server (SLF4J) thay vì gửi email** (đúng quyết định "chưa có SMTP" — token không trả về trong response để không lộ qua API).
  - `POST /api/auth/reset-password` — validate token + hạn dùng qua `findByResetToken().filter(...)`, đổi password xong thì xoá `resetToken`/`resetTokenExpiry` (token dùng 1 lần).
  - Migration `V3__password_reset.sql`: thêm `reset_token`/`reset_token_expiry` vào `users` + unique index (Postgres cho phép nhiều NULL trong unique index).
  - `SecurityConfig`: thêm `/api/auth/forgot-password`, `/api/auth/reset-password` vào `permitAll()`. `RateLimitFilter`: thêm `/api/auth/forgot-password` vào `LIMITED_PATHS` (chống dò email hàng loạt).
- [x] **7. Unit test mới**: `AuthServiceTest` (7 test: changePassword đúng/sai mật khẩu, forgotPassword email tồn tại/không tồn tại, resetPassword token hợp lệ/hết hạn/không tồn tại), `WorkScheduleServiceTest` (9 test: create/update/delete happy path + validate thời gian + trùng ngày + not-found), `PatientServiceTest` (4 test), `DentistServiceTest` (3 test: update happy path/trùng license/not-found), `ClinicSettingsAdminServiceTest` (3 test).
- [x] **8. Test `.http` mới**: `features/admin-clinic-config.http` (ClinicSettings + WorkSchedule CRUD + dentist/service full edit, kèm check 403 cho PATIENT), `features/patient-profile-and-password.http` (patient self-service + đổi/quên/đặt lại mật khẩu full flow).
- [x] **9. Verify toàn bộ**: `./gradlew build` 64/64 test pass (migration V3 chạy sạch trên DB baseline sẵn có). `bootRun` (dev) + curl thực tế xác nhận đúng toàn bộ endpoint mới, bao gồm: reset-password full flow với token thật lấy từ log server (đặt lại mật khẩu thành công, token không tái sử dụng được sau khi dùng), đổi mật khẩu xong login bằng mật khẩu cũ bị 400/mật khẩu mới 200, license trùng bị 400 sạch (không phải 500), work-schedule trùng ngày bị 400. Regression: `GET /api/dentists`/`GET /api/services` vẫn hoạt động đúng và phản ánh field/data mới sau khi sửa qua endpoint mới, `GET /api/appointments` ADMIN/RECEPTIONIST-only không đổi, Swagger UI vẫn 200. **Lưu ý môi trường**: gặp lỗi 500 "Invalid UTF-8 middle byte" khi truyền tiếng Việt qua `curl -d` trực tiếp trong Git Bash — không phải bug code, do Bash tool không encode UTF-8 đúng khi truyền string qua `-d`; fix bằng cách ghi JSON ra file UTF-8 thật rồi dùng `curl --data-binary @file`.

**Phase 12 — Test coverage tự động (Hoàn thành 2026-07-13):**

Theo roadmap gốc ở `C:\Users\ADMIN\.claude\plans\ti-p-t-c-c-ng-vi-c-parsed-hinton.md` (Phase 12), điều chỉnh lại phạm vi controller cho khớp trạng thái thực tế sau Phase 10-11 (từ 4 controller lên 7). Trước phase này project có 64 unit test service-layer (Mockito) nhưng 0% coverage tự động ở controller/repository/security. Quyết định đã chốt với user trước khi code: Testcontainers (Postgres thật) cho `@DataJpaTest`, không dùng H2; làm tuần tự cả 5 mục trong 1 phiên.

- [x] **1. `@WebMvcTest` + MockMvc cho 7 controller** — mỗi controller 1 file test (`AuthControllerTest`, `AppointmentControllerTest`, `DentistControllerTest`, `DentalServiceControllerTest`, `ClinicSettingsControllerTest`, `WorkScheduleControllerTest`, `PatientControllerTest`). Verify validation DTO → 400, `@PreAuthorize` chặn sai role → 403, happy path đúng status/shape. Mock service qua `@MockitoBean`.
  - `config/MethodSecurityTestConfig.java` (test-only, `@TestConfiguration @EnableMethodSecurity`) dùng chung cho mọi controller test — cần thiết vì `@WebMvcTest` không tự bật method security.
  - Mỗi `@WebMvcTest` phải `excludeFilters` loại `SecurityConfig`/`JwtAuthFilter`/`RateLimitFilter` thật — nếu không, Spring vẫn cố dựng `JwtAuthFilter` (được slice giữ lại vì nó là `Filter`) và fail vì thiếu bean `JwtUtil`/Redis (không muốn kéo cả hạ tầng JWT/Redis vào slice test).
  - **Gotcha:** endpoint có tham số `Authentication authentication` trong controller (không phải `@PreAuthorize` role check) không tự resolve được từ `@WithMockUser` một mình — `@WithMockUser` chỉ set `SecurityContextHolder` (đủ cho AOP `@PreAuthorize`) nhưng KHÔNG set `request.getUserPrincipal()` (Spring MVC cần cái này để bind tham số `Authentication`) vì cầu nối đó do 1 filter Security thật đảm nhiệm mà slice test không có. Fix: dùng `.principal(new UsernamePasswordAuthenticationToken(email, null, authorities))` trên request builder (set trực tiếp ở tầng MockHttpServletRequest, không cần filter).
- [x] **2. `@DataJpaTest` cho repository** — `AppointmentRepositoryTest` (pessimistic lock `findConflictingForUpdate`, các query count) và `WorkScheduleRepositoryTest` (`findByDentistIdAndDayOfWeekForUpdate`, unique constraint `(dentist_id, day_of_week)`). Dùng Testcontainers Postgres thật (`@Container @ServiceConnection`), `@AutoConfigureTestDatabase(replace = NONE)` để không bị JPA slice tự thay bằng embedded DB. `ClinicSettingsRepository` không có custom query method nên không cần test riêng (chỉ CRUD chuẩn JpaRepository).
  - **Gotcha Testcontainers 2.x** (kéo theo bởi Spring Boot 4.1.0, `testcontainers.version=2.0.5` trong BOM): toàn bộ artifact id đổi sang tiền tố `testcontainers-*` (`org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql` — KHÔNG phải `org.testcontainers:junit-jupiter`/`org.testcontainers:postgresql` như convention 1.x cũ). `PostgreSQLContainer` chuyển sang package `org.testcontainers.postgresql` và **không còn generic** (`PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")`, không phải `PostgreSQLContainer<?>`/`<>`).
  - **Gotcha Spring Boot 4 test slice**: `@DataJpaTest`/`TestEntityManager`/`AutoConfigureTestDatabase` không còn nằm trong 1 module `spring-boot-test-autoconfigure` như Boot 3 — bị tách theo domain giống main starters: `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure` (dependency `spring-boot-starter-data-jpa-test`), `TestEntityManager` → `org.springframework.boot.jpa.test.autoconfigure`, `AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure`.
- [x] **3. Test cho `JwtUtil`/`JwtAuthFilter`** — `JwtUtilTest` (5 test: generate/extract, valid, expired, malformed, sai secret), `JwtAuthFilterTest` (5 test Mockito thuần: token hợp lệ set context, token invalid/thiếu header/sai prefix không set context, không ghi đè context đã có sẵn).
- [x] **4. Jacoco** — plugin `jacoco` trong `build.gradle`, `jacocoTestReport` (HTML+XML) chạy sau `test`, `jacocoTestCoverageVerification` gate line coverage tối thiểu 50% (baseline thực tế đo được ~58%, không đặt 100% cứng nhắc) gắn vào `check`. CI (`antijob-ci.yml`) publish report HTML làm artifact `jacoco-coverage-report`.
- [x] **5. Verify toàn bộ** — `./gradlew build` pass (126 test: 64 cũ + 9 JWT + 10 repository Testcontainers + ~43 controller MockMvc), Jacoco gate pass. `docker compose up -d` (Postgres+Redis dev) cần chạy trước khi test vì `AntijobApplicationTests.contextLoads()` (`@SpringBootTest`) cần Postgres thật ở `localhost:5432` — khác với `@DataJpaTest` mới (tự chạy Postgres riêng qua Testcontainers, không phụ thuộc docker-compose dev). Docs cập nhật: README (mục Test + CI/CD), CHANGELOG (Phase 12 entry), CLAUDE.md (mục này).

**Phase 13 — Observability/ops (Hoàn thành 2026-07-13, cùng ngày với Phase 12):**

Theo roadmap gốc (`C:\Users\ADMIN\.claude\plans\ti-p-t-c-c-ng-vi-c-parsed-hinton.md`, Phase 13). Bỏ mục Micrometer/Prometheus (đánh dấu "tuỳ chọn" trong roadmap gốc) vì chưa có hạ tầng scrape nào (Prometheus/Grafana) để nhắm tới — quyết định hợp lý, không phải bỏ sót.

- [x] **1. `spring-boot-starter-actuator`** — `GET /actuator/health` duy nhất được expose (`management.endpoints.web.exposure.include: health`), `show-details: never` (không lộ chi tiết DB/Redis connection ra response). `SecurityConfig` thêm `permitAll()` cho `/actuator/health/**` — health probe không cần JWT.
- [x] **2. `Dockerfile` `HEALTHCHECK`** — cài `curl` ở runtime stage (base image `eclipse-temurin:21-jre` không có sẵn), `HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health`.
- [x] **3. `docker-compose.yml`**: service `antijob-app` mới, đặt trong Compose profile riêng (`profiles: ["full"]`) — **cố tình không chạy cùng** `docker compose up -d` mặc định (tránh xung đột port 8080 với dev đang `bootRun` song song), bật bằng `docker compose --profile full up -d --build`.
  - **Bug tìm & fix lúc verify thật**: ban đầu truyền `JWT_SECRET: ${JWT_SECRET:-}` (và tương tự cho `ADMIN_EMAIL`/`ADMIN_PASSWORD`/`CORS_ALLOWED_ORIGINS`/`SPRING_PROFILES_ACTIVE`) qua `environment:` — khi host không set biến, Compose vẫn set env var đó thành **chuỗi rỗng** trong container (khác hẳn "chưa set"). Spring Boot coi biến rỗng là "đã có giá trị" nên **không** fallback về default trong `application.yaml` nữa → `JwtUtil` bean crash thật (`WeakKeyException: key byte array is 0 bits`), container `Exited (1)`. Fix: bỏ hẳn 5 biến này khỏi `environment:` của `antijob-app` — chỉ giữ `DB_HOST=dental-db`/`REDIS_HOST=dental-redis` (bắt buộc, vì `localhost` không resolve được sang container khác) + `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` (khớp service postgres). Muốn override 5 biến kia cho môi trường giống prod thì dùng `deploy.sh`/`.env` như cũ, không phải qua compose profile này.
  - **Bài học chung**: `${VAR:-}` (default rỗng) trong Compose KHÔNG tương đương "để trống hoàn toàn" khi biến gốc dùng làm placeholder Spring Boot (`${JWT_SECRET:default-value}`) — chỉ nên dùng `${VAR:-}` cho biến compose tự dùng nội bộ (image tag, port mapping...), không nên dùng cho biến sẽ truyền tiếp vào 1 ứng dụng Spring Boot có default riêng trong `application.yaml`, vì 2 lớp default (Compose-level và Spring-level) xung đột nhau.
  - Verify thật: `docker compose --profile full up -d --build` → `docker ps` hiện `antijob-app ... (healthy)` sau ~10s (log `docker inspect` thấy đúng 1 lần fail ban đầu do app chưa kịp start, retry sau 5s thành công) → `curl http://localhost:8080/actuator/health` → `200 {"status":"UP"}`.
- [x] **4. `CorrelationIdFilter`** (`config/CorrelationIdFilter.java`, `@Component implements Ordered` với `getOrder()=HIGHEST_PRECEDENCE`) — đọc header `X-Request-ID` từ client nếu có, không thì tự sinh UUID, đặt vào MDC key `requestId`, echo lại qua response header, xoá MDC ở `finally`. **Quyết định thiết kế**: KHÔNG add vào `HttpSecurity` chain qua `addFilterBefore` (như `JwtAuthFilter`/`RateLimitFilter`) — dùng `Ordered.HIGHEST_PRECEDENCE` cho 1 servlet filter độc lập để chạy sớm hơn cả `springSecurityFilterChain`, đơn giản hơn nhiều so với phải tìm well-known anchor class phù hợp (xem gotcha `addFilterBefore` ở Phase 9) và filter này không có nhu cầu tương tác với `SecurityContext`.
- [x] **5. `logback-spring.xml` mới** — pattern console thêm `[%X{requestId:--}]` để phân biệt log của các request đồng thời. Verify thật qua `bootRun`: log có định dạng `... [-] ...` khi ngoài request (MDC rỗng, default `--`), curl thật với header `X-Request-ID: my-custom-trace-123` → response echo đúng lại header, không set header → tự sinh UUID khác nhau mỗi request.
- [x] **6. `CorrelationIdFilterTest`** (4 test Mockito thuần) — verify MDC được set trong lúc `filterChain.doFilter` chạy và bị xoá sau đó (kể cả khi chain ném exception), header luôn được set, tái dùng đúng request-id có sẵn từ client, `getOrder()` đúng `HIGHEST_PRECEDENCE`.
- [x] **7. Verify toàn bộ** — `./gradlew build` pass (129 test tổng, gồm 4 `CorrelationIdFilterTest` mới). `bootRun` thật + curl xác nhận `/actuator/health` 200 không cần token, header `X-Request-ID` hoạt động đúng cả 2 nhánh. `docker compose --profile full up -d --build` xác nhận healthcheck thật chuyển `starting`→`healthy`. Regression: `docker compose up -d` mặc định (không profile) vẫn chỉ chạy đúng 2 container `dental-db`/`dental-redis` như trước, không có gì đổi. Docs cập nhật: README (Tech Stack + mục "Chạy full stack" + mục Observability mới), CHANGELOG (Phase 13 entry), CLAUDE.md (mục này).

**Phase 14 — Notification + static analysis + CI polish (Hoàn thành 2026-07-13, cùng ngày với Phase 12-13):**

Theo roadmap gốc (`C:\Users\ADMIN\.claude\plans\ti-p-t-c-c-ng-vi-c-parsed-hinton.md`, Phase 14). User chọn MailHog (SMTP giả lập cho dev, không gửi mail thật ra ngoài) thay vì tích hợp SMTP provider thật — hợp lý vì project chưa có domain/hạ tầng production nào cần gửi mail thật.

- [x] **1. `EmailService`** (`service/EmailService.java`) — wrapper `JavaMailSender.send(SimpleMailMessage)`, bọc try/catch nuốt mọi exception + `log.error` thay vì propagate. **Quyết định thiết kế quan trọng**: gửi email là side-effect, KHÔNG được làm fail transaction chính — nếu SMTP down, đặt lịch/huỷ lịch/quên mật khẩu vẫn phải thành công, chỉ mất thông báo email (chấp nhận được, khác hẳn nghiệp vụ core).
- [x] **2. MailHog** (`docker-compose.yml`, service `mailhog` mới, image `mailhog/mailhog:v1.0.1`) — SMTP giả lập `localhost:1025` (không cần auth) + UI xem mail đã gửi tại `localhost:8025`. `spring.mail.*` trong `application.yaml` trỏ mặc định vào đây qua `MAIL_HOST`/`MAIL_PORT` (default `localhost:1025`), đổi sang SMTP thật chỉ cần set env var, không sửa code.
- [x] **3. Nối 3 luồng nghiệp vụ với email thật**:
  - `AuthService.forgotPassword` — gửi mã reset qua email, **không còn log token ra server** như trước (Phase 11 để tạm vì chưa có SMTP) — bớt 1 kênh lộ thông tin nhạy cảm giờ không cần thiết nữa.
  - `AppointmentService.book` — email xác nhận đặt lịch sau khi save thành công.
  - `AppointmentService.cancel` — email thông báo huỷ lịch sau khi save thành công.
  - Cả 3 chỗ đều gọi `emailService.send()` sau khi transaction chính đã chắc chắn thành công (sau `repository.save()`), không phải trước.
- [x] **4. Test**: `EmailServiceTest` (2 test: build đúng `SimpleMailMessage` từ from/to/subject/body, exception từ `mailSender.send()` không propagate ra ngoài). `AuthServiceTest`/`AppointmentServiceTest` cập nhật thêm `@Mock EmailService` + verify gửi đúng người nhận ở luồng thành công.
- [x] **5. SpotBugs** (`com.github.spotbugs` plugin, gắn vào `check`) — chạy lần đầu phát hiện 34 finding, toàn bộ đều false-positive chuẩn cho project Lombok/JPA: `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` (26+8, entity/DTO trả tham chiếu mutable — convention có chủ đích của project, không phải lỗ hổng thật trong app monolith) và `CT_CONSTRUCTOR_THROW` (2, `JwtUtil` cố tình throw khi key rỗng/yếu — đúng thiết kế fail-fast từ Phase 9). Thêm exclude filter `config/spotbugs/exclude.xml` loại đúng 3 pattern này, giữ nguyên mọi bug pattern khác — sau khi lọc: 0 finding thật.
- [x] **6. CI polish** (`antijob-ci.yml`) — publish thêm 2 artifact mới: `junit-test-report` (JUnit HTML report) và `spotbugs-report` (SpotBugs HTML+XML), cạnh `jacoco-coverage-report` có sẵn từ Phase 12.
- [x] **7. Verify toàn bộ, có gửi mail thật** — `./gradlew build` pass (131 test, gồm SpotBugs gate). `docker compose up -d` (thêm `mailhog`) + `bootRun` thật + curl: register/login → forgot-password → book → cancel, mỗi bước xác nhận qua MailHog API (`curl localhost:8025/api/v2/messages`) thấy đúng 3 email tuần tự ("Xác nhận đặt lịch hẹn", "Đặt lại mật khẩu", "Huỷ lịch hẹn") với đúng người nhận, subject tiếng Việt encode UTF-8 đúng chuẩn. Docs cập nhật: README (Tech Stack, mục Email mới, yêu cầu môi trường), `.env.example` (biến `MAIL_*`), CHANGELOG (Phase 14 entry), CLAUDE.md (mục này).

**Roadmap gốc (Phase 9-14) coi như đã hoàn thành toàn bộ.** Không còn phase nào tồn đọng — các việc phát sinh sau này (nếu có) sẽ được thêm thành Phase mới khi user yêu cầu, không dựa vào roadmap cũ nữa.

**Phase 15 — JWT logout/revoke (Hoàn thành 2026-07-14):**

Sau đánh giá "sẵn sàng sử dụng" (xem mục dưới), user chọn khắc phục giới hạn "JWT sống hết 24h dù đổi mật khẩu/logout" làm việc tiếp theo.

- [x] **1. `TokenRevocationService`** (`security/TokenRevocationService.java`, mới) — JWT là stateless nên không "xoá" được token đã phát; thay vào đó lưu 1 mốc `notBefore` (epoch millis) theo email trong Redis (`token:notBefore:<email>`, TTL = đúng bằng `jwt.expiration-ms` vì sau đó token cũ tự hết hạn nên không cần giữ key nữa). `revokeAllTokens(email)` ghi mốc = thời điểm hiện tại; `isRevoked(email, issuedAt)` so `issuedAt` của token với mốc đã lưu — token phát trước mốc bị coi là đã thu hồi. Đây là revoke **toàn bộ token của user** (mọi thiết bị), không phải revoke 1 token đơn lẻ theo `jti` — đơn giản hơn và đúng nhu cầu thực tế (logout nên đăng xuất khỏi mọi phiên, đổi mật khẩu nên vô hiệu hoá token cũ ở mọi nơi).
- [x] **2. `JwtUtil.extractIssuedAt(token)`** (method mới) — lấy `issuedAt` claim có sẵn từ lúc `generateToken` (không cần thêm claim mới).
- [x] **3. `JwtAuthFilter`** — sau khi `isValid(token)` đúng, gọi thêm `tokenRevocationService.isRevoked(email, issuedAt)`; nếu bị thu hồi thì bỏ qua không set `SecurityContext` (giống hệt xử lý token invalid — request tiếp tục chain nhưng không có auth, dẫn tới 403 ở tầng Security do endpoint yêu cầu authenticated).
- [x] **4. `POST /api/auth/logout`** (`AuthController`, yêu cầu đã đăng nhập — không giới hạn role) — gọi `AuthService.logout(email)` → `tokenRevocationService.revokeAllTokens(email)`.
- [x] **5. Nối vào 2 luồng đổi mật khẩu có sẵn** — `AuthService.changePassword()` và `AuthService.resetPassword()` đều gọi `tokenRevocationService.revokeAllTokens(email)` sau khi lưu mật khẩu mới, để token phát hành trước đó (có thể đã bị lộ, đúng lý do đổi mật khẩu) không dùng được nữa ngay lập tức thay vì phải chờ hết hạn 24h.
- [x] **6. Test mới**: `TokenRevocationServiceTest` (4 test: ghi đúng key/TTL, chưa từng revoke → false, issuedAt trước/sau mốc revoke). `JwtUtilTest` thêm test `extractIssuedAt`. `JwtAuthFilterTest` thêm test token bị revoke không set SecurityContext + cập nhật constructor/stub cho các test hiện có. `AuthServiceTest`/`AuthControllerTest` cập nhật mock `TokenRevocationService` + verify `revokeAllTokens` được gọi đúng ở `changePassword`/`resetPassword`/`logout`.
- [x] **7. Test `.http` mới**: `features/auth-logout-token-revocation.http` — full flow: login → gọi API OK → logout → dùng lại token cũ bị 403 → login lại token mới OK → đổi mật khẩu bằng token đó → dùng lại token trước khi đổi bị 403 → login mật khẩu mới OK.
- [x] **8. Verify toàn bộ**: `./gradlew build` pass (SpotBugs 0 finding thật, Jacoco gate qua). `docker compose up -d` (Postgres+Redis+MailHog) + `bootRun` thật + curl xác nhận đúng: token dùng được trước logout (200) → logout (200) → token cũ bị revoke ngay (403) → login mới hoạt động bình thường (200) → đổi mật khẩu bằng token mới → token đó bị revoke ngay sau đổi mật khẩu (403) → login bằng mật khẩu mới thành công (200). Regression: `GET /api/appointments` không token vẫn 403, `/actuator/health` vẫn 200, Swagger `/v3/api-docs` vẫn 200.
- [x] **9. Docs**: `.http` mới (mục 7), `CLAUDE.md` (mục này + xoá dòng "Không có logout/revoke token" khỏi "Giới hạn đã biết" bên dưới), `CHANGELOG.md` (Phase 15 entry).

**Giới hạn còn lại sau Phase 15** — `revokeAllTokens` là "logout mọi thiết bị" (không phải revoke từng token/session riêng lẻ theo thiết bị) — chấp nhận được cho quy mô hiện tại, nhưng nếu sau này cần "đăng xuất thiết bị A mà vẫn giữ thiết bị B" thì cần model theo `jti` + danh sách token đang hoạt động (refresh-token pattern), phức tạp hơn nhiều so với nhu cầu thực tế hiện có.

**Phase 16 — Micrometer/Prometheus (Hoàn thành 2026-07-14, cùng ngày với Phase 15):**

User chọn khắc phục giới hạn "Không có Micrometer/Prometheus" (bỏ qua ở Phase 13 vì lúc đó chưa có hạ tầng scrape) làm việc tiếp theo — chỉ cần nền tảng export metrics đúng chuẩn Prometheus, KHÔNG cần dựng thêm container Prometheus/Grafana thật (vẫn chưa có hạ tầng scrape, nhưng giờ endpoint đã sẵn sàng để gắn vào bất cứ lúc nào).

- [x] **1. `io.micrometer:micrometer-registry-prometheus`** thêm vào `build.gradle` (cạnh `spring-boot-starter-actuator` có sẵn từ Phase 13) — Spring Boot Actuator tự động cấu hình `PrometheusMeterRegistry` khi thấy dependency này trên classpath, không cần code cấu hình thủ công.
- [x] **2. `application.yaml`**: `management.endpoints.web.exposure.include` thêm `prometheus` (giữ nguyên `health` có sẵn). `management.metrics.tags.application: antijob` — gắn label `application="antijob"` vào mọi metric, cần thiết để phân biệt nếu sau này Prometheus scrape nhiều service.
- [x] **3. `SecurityConfig`**: `/actuator/prometheus` thêm vào cùng nhóm `permitAll()` với `/actuator/health/**` — theo đúng quyết định đã có ở Phase 13 (health probe không cần JWT), áp dụng tương tự cho metrics scrape (Prometheus scraper thường không mang theo JWT). Chấp nhận được vì đây là project dev/demo chưa expose ra internet công khai; nếu triển khai thật có traffic công khai, nên giới hạn `/actuator/prometheus` theo IP/network riêng thay vì `permitAll` tuyệt đối.
- [x] **4. Verify thật qua curl** (không cần dựng Prometheus thật để xác nhận): `curl /actuator/prometheus` trả đúng format text Prometheus (`# HELP`/`# TYPE`), có sẵn metric JVM (`application_started_time_seconds`), cache (`cache_gets_total` cho `dentists-active`/`services-active`/`slots` — đúng 3 cache name đã cấu hình từ Phase 4/5), DB connection pool (Hikari), và quan trọng nhất: **`http_server_requests_seconds_count`** tự động ghi nhận đúng method/URI/status cho mỗi request thật đã gọi trong lúc verify (vd. `GET /v3/api-docs` status 200, `GET /api/appointments` không token status 403). Regression: `/actuator/health` vẫn 200, `GET /api/appointments` không token vẫn 403, Swagger vẫn 200.
- [x] **5. Không có test tự động mới** — đây thuần là khai báo cấu hình (dependency + yaml + security matcher), không có logic nghiệp vụ để unit test; đã verify bằng smoke test thật ở bước 4 thay vì viết test giả cho cấu hình framework.
- [x] **6. Docs**: README (mục Observability cập nhật thêm Prometheus + cách scrape), `CLAUDE.md` (mục này + xoá dòng giới hạn Micrometer/Prometheus), `CHANGELOG.md` (Phase 16 entry).

**Giới hạn còn lại sau Phase 16** — chưa có Prometheus/Grafana container thật nào chạy để scrape endpoint này (chỉ mới có endpoint sẵn sàng) — cần thêm khi thực sự có nhu cầu dashboard/alerting. `/actuator/prometheus` hiện `permitAll` — nếu deploy ra mạng công khai thật, nên giới hạn theo network/IP allowlist thay vì để public như hiện tại (đang chấp nhận được vì scope dev/demo).

---

## Đánh giá "sẵn sàng sử dụng" (2026-07-14)

Sau khi roadmap Phase 9-14 hoàn thành, user yêu cầu tiếp tục tới khi dự án "dùng ngon lành được" — thực hiện 1 vòng audit + smoke test end-to-end thật (không chỉ chạy unit test) để xác nhận, thay vì chỉ tin vào checklist.

**Audit tĩnh:** grep toàn bộ `src/main/java` không còn `TODO`/`FIXME`/`XXX` sót lại. Không có secret hardcode ngoài `admin123` (default dev có chủ đích, đã document).

**Smoke test thật, nối tiếp 1 luồng nghiệp vụ hoàn chỉnh** (không phải test rời rạc từng endpoint) — `docker compose up -d` (Postgres+Redis+MailHog) + `bootRun` thật + curl, **không tìm thấy bug nào**:
1. Admin login → xem/hiểu `clinic-settings` → tạo dịch vụ mới → xem danh sách dentist/work-schedule → tạo tài khoản RECEPTIONIST → toggle dentist active — tất cả 200 đúng shape.
2. Patient tự đăng ký/login → xem slot trống → xem/sửa hồ sơ (`/patients/me`) → đặt lịch (auto-assign dentist) — 200 đúng, đúng dentist/slot.
3. Kiểm tra phân quyền chéo: PATIENT gọi `GET /appointments` (danh sách) → đúng 403; RECEPTIONIST gọi cùng endpoint → đúng 200.
4. RECEPTIONIST confirm → complete lịch hẹn vừa đặt — đúng chuyển trạng thái PENDING→CONFIRMED→COMPLETED.
5. **Quan trọng nhất**: full flow quên mật khẩu **qua email thật** (không dùng log token nữa từ Phase 14) — forgot-password → lấy token thật từ MailHog API (`curl localhost:8025/api/v2/messages`, parse quoted-printable body bằng Node vì máy không có Python/jq) → reset-password với token đó → login bằng mật khẩu mới thành công → login bằng mật khẩu cũ bị từ chối đúng → dùng lại token đã dùng bị từ chối đúng ("Token không hợp lệ hoặc đã hết hạn"). Đây là regression quan trọng nhất cần verify vì Phase 14 đã xoá hẳn đường log token — nếu email hoặc parse token sai thì tính năng này sẽ hoàn toàn không dùng được, và test đã xác nhận không phải vậy.
6. Rate limiting: 6 lần gọi `/api/auth/login` liên tiếp → 429 đúng sau khi vượt ngưỡng (ngưỡng tính cộng dồn theo IP trong cửa sổ 60s, kể cả các lần login thành công trước đó trong cùng phiên — đúng thiết kế, không phải bug).
7. Swagger UI (`/swagger-ui/index.html`) và OpenAPI JSON (`/v3/api-docs`) đều 200.

**Kết luận: dự án đã sẵn sàng cho mục đích dev/demo/portfolio.** Toàn bộ luồng nghiệp vụ chính (đặt lịch, vòng đời appointment, phân quyền 4 role, profile self-service, admin CRUD config, quên/đổi mật khẩu qua email thật, rate limiting, observability) đã được verify thật, không chỉ test tự động. `./gradlew build` xanh (131 test + Jacoco 50%+ + SpotBugs 0 finding thật).

**Giới hạn đã biết, cố tình chưa làm (không chặn "dùng được", chỉ cần biết trước khi lên production thật):**
- ~~Không có logout/revoke token~~ — đã khắc phục ở Phase 15 (`TokenRevocationService`, xem mục Phase 15 ở trên). Giới hạn còn lại: revoke là theo user (mọi thiết bị), không phải theo từng phiên/thiết bị riêng lẻ.
- ~~Không có Micrometer/Prometheus~~ — đã khắc phục ở Phase 16 (`/actuator/prometheus`, xem mục Phase 16 ở trên). Giới hạn còn lại: chưa có Prometheus/Grafana thật nào scrape endpoint này, và endpoint hiện `permitAll` (chấp nhận được cho dev/demo, nên giới hạn network khi deploy thật ra internet công khai).
- MailHog chỉ dùng được cho dev (không gửi mail thật ra ngoài) — cần đổi `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD` sang SMTP provider thật trước khi có người dùng thật.
- `admin123` là mật khẩu seed mặc định ở dev — **bắt buộc đổi qua `ADMIN_PASSWORD` trước khi chạy `SPRING_PROFILES_ACTIVE=prod`** (đã có fail-fast tương tự cho JWT_SECRET, nhưng ADMIN_PASSWORD không bắt buộc — nếu để trống ở prod thì đơn giản là không tạo admin nào, không phải lỗ hổng, nhưng cần nhớ set để có tài khoản đầu tiên).
