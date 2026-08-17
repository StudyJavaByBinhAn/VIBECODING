# Worklog

Nhật ký các phiên làm việc trên project này — 1 file Markdown riêng cho mỗi task/phase đáng kể, để đọc lại nhanh mà không phải lục `CLAUDE.md` (vốn đã rất dài) hay `git log`.

Khác với `CLAUDE.md`/`CHANGELOG.md` (tài liệu chính thức, append-only, viết cho người đọc code sau này) — folder này là **tường thuật ngắn, theo phiên làm việc**, viết cho chính bạn đọc lại: đã làm gì, tại sao, quyết định nào đã chốt, gotcha nào gặp phải, verify ra sao.

## Quy ước đặt tên

```
YYYY-MM-DD-slug-ngan-gon.md
```

Ví dụ: `2026-08-06-kafka-phase-a.md`. Nếu 1 ngày có nhiều task không liên quan, mỗi task 1 file riêng (không gộp).

## Cấu trúc 1 file log

```markdown
# <Tiêu đề ngắn>

**Ngày:** YYYY-MM-DD
**Bối cảnh:** vì sao làm việc này (1-2 câu)

## Đã làm
- ...

## Quyết định đáng nhớ
- ...

## Gotcha / vấn đề gặp phải
- ...

## Verify
- ...

## Còn lại / bước tiếp theo
- ...
```

Không cần đủ hết mọi mục — bỏ mục nào không có nội dung, đừng viết cho đủ.
