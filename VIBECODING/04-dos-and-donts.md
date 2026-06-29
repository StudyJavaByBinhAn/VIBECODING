# 04 — DO & DON'T

## DO ✅

- Viết unit test cho `SlotService` + `AppointmentService`.
- `@Transactional` cho mọi write operation.
- Validate ở 2 tầng: DTO (annotation) + Service (business logic).
- Log đúng mức: `INFO` = business event, `ERROR` = exception, `DEBUG` = dev.
- Return `ApiResponse<T>` ở **mọi** endpoint.
- Dùng `Optional` đúng — `.orElseThrow()`, không `.get()` trần.

## DON'T ❌

- Không trả entity từ controller → luôn map sang DTO.
- Không `@Autowired` field → dùng constructor injection (`@RequiredArgsConstructor`).
- Không hardcode → config trong `application.yml` hoặc `ClinicSettings`.
- Không `CascadeType.ALL` khi chưa hiểu impact.
- Không catch `Exception` chung → catch exception cụ thể.
- Không để N+1 query → dùng `@EntityGraph` / `JOIN FETCH`.
- Không `System.out.println()` → dùng SLF4J `log.info()`.
- Không nói "xong" khi chưa `mvn compile` + `mvn test` pass.
