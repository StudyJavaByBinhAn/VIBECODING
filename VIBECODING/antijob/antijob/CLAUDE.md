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
