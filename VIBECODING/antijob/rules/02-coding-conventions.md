# 02 — Coding Conventions

## Naming

| Loại | Convention | Ví dụ |
|------|-----------|-------|
| Entity | PascalCase | `Appointment`, `WorkSchedule` |
| Table | snake_case (plural) | `appointments`, `work_schedules` |
| DTO | XxxRequest / XxxResponse | `AppointmentRequest` |
| Service method | verb + noun | `bookAppointment()`, `getAvailableSlots()` |
| Enum value | UPPER_SNAKE | `PENDING`, `IN_PROGRESS` |

## Entity Pattern

```java
@Entity
@Table(name = "appointments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)       // Luôn LAZY
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Enumerated(EnumType.STRING)              // Không dùng ORDINAL
    private AppointmentStatus status;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- Mỗi entity tự khai báo `@Id` (chưa có `BaseEntity` chung).
- `@ManyToOne` → luôn `LAZY`. Cần eager → dùng `@EntityGraph` hoặc `JOIN FETCH`.
- Không dùng `CascadeType.ALL` trừ khi hiểu rõ impact.

## DTO Pattern

```java
// Request — class Lombok @Data + jakarta.validation, không phải record
@Data
public class AppointmentRequest {
    @NotNull
    private Long serviceId;

    // nullable có chủ đích — null nghĩa là auto-assign dentist (Phase 6)
    private Long dentistId;

    @NotNull
    private LocalDate appointmentDate;

    @NotNull
    private LocalTime startTime;

    @Size(max = 500)
    private String notes;
}
```

- Mọi request DTO dùng class Lombok (`@Data`), KHÔNG dùng `record` — quyết định giữ nguyên style này khi thêm validation (Phase 7), không đổi sang record.
- Validate ở tầng DTO bằng `jakarta.validation.constraints.*` (`@NotNull`, `@NotBlank`, `@Email`, `@Size`...) + `@Valid` trên tham số `@RequestBody` ở controller. Lỗi validation được `GlobalExceptionHandler` bắt qua `MethodArgumentNotValidException` → 400.
- Không expose `password`, `passwordHash`.
- Map Entity ↔ DTO qua MapStruct (`mapper/` package, interface `@Mapper(componentModel = "spring")`, inject như bean thường qua constructor). Không trả entity từ controller. Field tên khác nhau/nested (vd. `AppointmentResponse.patientName` ← `Appointment.patient.fullName`) dùng `@Mapping(target = ..., source = ...)`; field tên khớp trực tiếp thì để MapStruct tự map, không cần khai báo.
- List endpoint có khả năng phình to theo thời gian (vd. `GET /api/appointments`) dùng `Pageable`/`Page<T>`, map sang `dto/PageResponse<T>` (`content`, `page`, `size`, `totalElements`, `totalPages`) thay vì trả `List<T>` trần. List nhỏ, bị chặn quy mô và đang cache key cố định (`/api/dentists`, `/api/services`) thì giữ nguyên `List<T>`, không cần phân trang.

## API Response Format

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
```

Mọi endpoint trả `ApiResponse<T>` — kể cả error (khi đó `data = null`, `message` chứa nội dung lỗi). Không có field `timestamp`/`errors` riêng — nhiều lỗi validation trên nhiều field được gộp vào 1 chuỗi `message` (nối bằng `"; "`), không phải mảng.

## Exception → HTTP Status

Vocabulary thật dùng exception JDK/Spring có sẵn, KHÔNG có custom exception class riêng nào trong project:

| Exception | Status | Ghi chú |
|-----------|--------|---------|
| `IllegalArgumentException` | 400 | Lỗi business rule / not-found (vd. "Không tìm thấy lịch hẹn id=...") |
| `MethodArgumentNotValidException` | 400 | Lỗi validation tầng DTO (`@Valid` fail) |
| `AccessDeniedException` | 403 | Không đủ quyền / không phải chủ sở hữu resource |
| `IllegalStateException` | 409 | Xung đột (vd. "Slot đã được đặt") |
| `RuntimeException` | 500 | Catch-all cuối cùng cho lỗi không lường trước |

Tất cả xử lý tại `@RestControllerAdvice GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`).

## Caching

Redis đã triển khai từ Phase 4 (`config/CacheConfig.java`, `GenericJacksonJsonRedisSerializer`). Dùng `@Cacheable`/`@CacheEvict` ở tầng Service, không gọi Redis client trực tiếp. Cache name thật đang dùng:
- `slots` (key `#dentistId + ':' + #date`, TTL 5 phút) — evict khi book/cancel appointment.
- `dentists-active` / `services-active` (key cố định `'all'`, TTL 1 giờ) — evict khi ADMIN đổi `active`.
