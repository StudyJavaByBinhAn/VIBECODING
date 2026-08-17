-- Chạy tự động bởi Postgres image chỉ khi data dir còn trống (lần khởi tạo volume đầu tiên).
-- Tạo database riêng cho email-service (bảng audit sent_emails) — không share schema với
-- booking-service (POSTGRES_DB=dental_db), đúng quyết định kiến trúc Phase 18.
CREATE DATABASE email_service_db;
