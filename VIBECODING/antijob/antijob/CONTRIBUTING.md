# Contributing

## Trước khi code

Đọc theo thứ tự:
1. `CLAUDE.md` — tổng quan project, 4 nguyên tắc làm việc, progress tracking từng phase.
2. `../rules/01-project-context.md` — tech stack, cấu trúc, lệnh chạy.
3. `../rules/02-coding-conventions.md` — naming, entity/DTO pattern, exception vocabulary, caching.
4. `../rules/03-domain-rules.md` — business rule (tính slot, race condition, huỷ lịch, auto-assign...).
5. `../rules/04-dos-and-donts.md` — checklist DO/DON'T.

## Nguyên tắc

- **Think before coding** — nêu rõ assumption, không chắc thì hỏi thay vì đoán.
- **Simplicity first** — không thêm feature/abstraction ngoài yêu cầu.
- **Surgical changes** — chỉ sửa đúng chỗ cần sửa, không refactor code đang chạy tốt kèm theo.
- **Verify before done** — `./gradlew build` phải pass, test thủ công qua `.http`/curl cho behavior thay đổi, không báo "xong" khi chưa verify.

## Coding convention (tóm tắt — chi tiết xem `rules/02-coding-conventions.md`)

- Constructor injection (`@RequiredArgsConstructor`), không `@Autowired` field.
- Entity ↔ DTO map qua MapStruct (`mapper/` package), không trả entity từ controller.
- Request DTO validate bằng `jakarta.validation` + `@Valid` trên controller.
- Mọi response wrap `ApiResponse<T>`.
- `@Transactional` cho mọi write operation.
- Exception vocabulary: `IllegalArgumentException`→400, `IllegalStateException`→409, `AccessDeniedException`→403, `MethodArgumentNotValidException`→400 — không tạo custom exception class mới trừ khi 5 loại này không đủ diễn tả.
- List endpoint có thể phình to (như `/api/appointments`) dùng `Pageable`/`PageResponse<T>`; list nhỏ/cố định (`/api/dentists`, `/api/services`) giữ `List<T>` trần.

## Testing

- Unit test service layer bằng Mockito (`@Mock`/`@InjectMocks`), inject `Clock` khi logic phụ thuộc thời gian hiện tại — không gọi `LocalTime.now()`/`LocalDate.now()` trực tiếp trong code cần test.
- Mỗi tính năng mới thêm 1 file `.http` riêng trong `src/test/http/features/` (quy ước từ Phase 5) — không dồn chung vào `appointments.http`.
- Chạy `./gradlew test` trước khi coi là xong; nếu đổi behavior ảnh hưởng response thật, chạy `bootRun` + curl/`.http` verify sống, không chỉ dựa vào unit test.

## Commit

- Message ngắn gọn, mô tả **why** hơn **what**.
- Không commit khi chưa được yêu cầu rõ ràng trong phiên làm việc (áp dụng cho AI agent hỗ trợ code).
- Không dùng `--no-verify`/bỏ qua hook trừ khi có lý do rõ ràng và được xác nhận.

## CI

`.github/workflows/antijob-ci.yml` (ở root repo git, không nằm trong thư mục `antijob/antijob/`) tự chạy `./gradlew build` với Postgres + Redis service container trên mỗi push/PR đổi code trong `antijob/`. Migration Flyway mới (file `V*.sql`) sẽ được test thật trong CI, không chỉ local — nếu migration lỗi, CI đỏ trước khi merge. Sửa file workflow này khi cần đổi bước CI, không sửa trực tiếp trên GitHub UI.

## Môi trường dev đặc thù (Windows)

Một số máy dev Windows có dải port bị hệ thống reserve động (`netsh interface ipv4 show excludedportrange protocol=tcp`), khiến `bootRun`/Docker báo "port already in use" dù không có process nào thật sự đang dùng port đó. Đây là vấn đề máy, **không sửa `application.yaml`** — dùng tham số runtime để đổi port tạm thời, ví dụ:

```bash
./gradlew bootRun --args="--server.port=9090 --spring.data.redis.port=17000"
```
