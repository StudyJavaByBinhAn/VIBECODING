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
// Request — dùng record + validation
public record AppointmentRequest(
    @NotNull Long serviceId,
    Long dentistId,                           // nullable = auto-assign
    @NotNull @FutureOrPresent LocalDate date,
    @NotNull LocalTime startTime,
    String notes
) {}

// Response — không chứa sensitive data
public record AppointmentResponse(
    Long id, LocalDate date, LocalTime startTime,
    LocalTime endTime, AppointmentStatus status,
    DentistSummary dentist, ServiceSummary service
) {}
```

- DTO hiện tại dùng class Lombok (`@Data`), có thể chuyển sang `record` khi thêm validation.
- Không expose `password`, `passwordHash`.
- Map Entity ↔ DTO thủ công (chưa có MapStruct). Không trả entity từ controller.

## API Response Format

```java
public record ApiResponse<T>(
    boolean success, String message, T data,
    LocalDateTime timestamp, List<String> errors
) {}
```

Mọi endpoint trả `ApiResponse<T>` — kể cả error.

## Exception → HTTP Status

| Exception | Status |
|-----------|--------|
| `ResourceNotFoundException` | 404 |
| `SlotAlreadyBookedException` | 409 |
| `InvalidAppointmentException` | 400 |
| `AccessDeniedException` | 403 |
| `MethodArgumentNotValid` | 400 |

Tất cả xử lý tại `@RestControllerAdvice GlobalExceptionHandler`.

## Caching (chưa triển khai)

Redis/`@Cacheable` chưa có trong project — khi làm, dùng `@Cacheable`/`@CacheEvict`/`@CachePut`,
không gọi Redis client trực tiếp. Key pattern dự kiến: `slots:{date}:{serviceId}` · `dentists:active` · `services:active`.
