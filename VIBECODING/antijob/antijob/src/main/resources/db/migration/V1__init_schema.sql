-- ============================================================
-- V1__init_schema.sql
-- Dental Clinic Booking — khởi tạo schema + seed data
-- ============================================================

-- ============================================================
-- 1. TABLES
-- ============================================================

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(30)  NOT NULL,   -- PATIENT | DENTIST | ADMIN | RECEPTIONIST
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE patients (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    full_name      VARCHAR(255) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    date_of_birth  DATE,
    gender         VARCHAR(10),          -- MALE | FEMALE | OTHER
    address        TEXT,
    medical_history TEXT
);

CREATE TABLE dentists (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    specialization  VARCHAR(255),
    license_number  VARCHAR(100) NOT NULL UNIQUE,
    bio             TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE dental_services (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255)   NOT NULL,
    description      TEXT,
    duration_minutes INT            NOT NULL,
    price            NUMERIC(12, 2) NOT NULL,
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE work_schedules (
    id          BIGSERIAL PRIMARY KEY,
    dentist_id  BIGINT      NOT NULL REFERENCES dentists(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,   -- MONDAY … SUNDAY
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_schedule UNIQUE (dentist_id, day_of_week)
);

CREATE TABLE appointments (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT      NOT NULL REFERENCES patients(id),
    dentist_id       BIGINT      NOT NULL REFERENCES dentists(id),
    service_id       BIGINT      NOT NULL REFERENCES dental_services(id),
    appointment_date DATE        NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELLED | NO_SHOW
    notes            TEXT,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE clinic_settings (
    id                      BIGSERIAL PRIMARY KEY,
    clinic_name             VARCHAR(255) NOT NULL,
    open_time               TIME         NOT NULL,
    close_time              TIME         NOT NULL,
    slot_duration_minutes   INT          NOT NULL DEFAULT 30,
    max_advance_booking_days INT         NOT NULL DEFAULT 30
);

-- ============================================================
-- 2. INDEXES
-- ============================================================

-- appointments: query chính theo ngày + bác sĩ (check conflict, slot trống)
CREATE INDEX idx_appt_dentist_date   ON appointments (dentist_id, appointment_date);
-- appointments: query theo bệnh nhân (xem lịch sử)
CREATE INDEX idx_appt_patient        ON appointments (patient_id);
-- appointments: lọc theo status
CREATE INDEX idx_appt_status         ON appointments (status);
-- work_schedules: query theo bác sĩ + ngày trong tuần
CREATE INDEX idx_schedule_dentist    ON work_schedules (dentist_id, day_of_week);
-- patients: tìm theo số điện thoại
CREATE INDEX idx_patient_phone       ON patients (phone);
-- dentists: lọc bác sĩ đang hoạt động
CREATE INDEX idx_dentist_active      ON dentists (is_active);
-- users: login lookup
CREATE INDEX idx_user_email          ON users (email);

-- ============================================================
-- 3. SEED DATA
-- ============================================================

-- Users
INSERT INTO users (email, password, role) VALUES
    ('admin@dental.vn',      '$2a$10$dummyHashAdmin',      'ADMIN'),
    ('bsnguyenvana@dental.vn','$2a$10$dummyHashDentist1',  'DENTIST'),
    ('bstranthib@dental.vn', '$2a$10$dummyHashDentist2',   'DENTIST'),
    ('patient1@gmail.com',   '$2a$10$dummyHashPatient1',   'PATIENT'),
    ('patient2@gmail.com',   '$2a$10$dummyHashPatient2',   'PATIENT');

-- Dentists (user_id 2, 3)
INSERT INTO dentists (user_id, full_name, phone, specialization, license_number, bio, is_active) VALUES
    (2, 'BS. Nguyễn Văn A', '0901000001', 'Chỉnh nha',         'DN-001', 'Chuyên gia niềng răng 10 năm kinh nghiệm.', TRUE),
    (3, 'BS. Trần Thị B',   '0901000002', 'Nha khoa tổng quát','DN-002', 'Điều trị tổng quát và thẩm mỹ răng.',       TRUE);

-- Patients (user_id 4, 5)
INSERT INTO patients (user_id, full_name, phone, date_of_birth, gender, address) VALUES
    (4, 'Lê Văn C',   '0911000001', '1995-03-20', 'MALE',   '123 Lê Lợi, Q1, TP.HCM'),
    (5, 'Phạm Thị D', '0911000002', '1990-07-15', 'FEMALE', '456 Nguyễn Huệ, Q1, TP.HCM');

-- Dental services
INSERT INTO dental_services (name, description, duration_minutes, price, is_active) VALUES
    ('Làm sạch răng',   'Cạo vôi răng và đánh bóng',             30,  200000, TRUE),
    ('Trám răng',       'Trám composite thẩm mỹ',                 45,  350000, TRUE),
    ('Nhổ răng',        'Nhổ răng thường (không phẫu thuật)',      30,  300000, TRUE),
    ('Niềng răng mắc cài', 'Tư vấn và lắp mắc cài kim loại',    60, 1500000, TRUE),
    ('Tẩy trắng răng',  'Tẩy trắng bằng laser',                  60,  800000, TRUE);

-- Work schedules — BS. Nguyễn Văn A (dentist_id = 1): Thứ 2 → Thứ 6, 8h–17h
INSERT INTO work_schedules (dentist_id, day_of_week, start_time, end_time, is_active) VALUES
    (1, 'MONDAY',    '08:00', '17:00', TRUE),
    (1, 'TUESDAY',   '08:00', '17:00', TRUE),
    (1, 'WEDNESDAY', '08:00', '17:00', TRUE),
    (1, 'THURSDAY',  '08:00', '17:00', TRUE),
    (1, 'FRIDAY',    '08:00', '17:00', TRUE);

-- Work schedules — BS. Trần Thị B (dentist_id = 2): Thứ 2, 4, 6 + Thứ 7 sáng
INSERT INTO work_schedules (dentist_id, day_of_week, start_time, end_time, is_active) VALUES
    (2, 'MONDAY',    '08:00', '17:00', TRUE),
    (2, 'WEDNESDAY', '08:00', '17:00', TRUE),
    (2, 'FRIDAY',    '08:00', '17:00', TRUE),
    (2, 'SATURDAY',  '08:00', '12:00', TRUE);

-- Clinic settings
INSERT INTO clinic_settings (clinic_name, open_time, close_time, slot_duration_minutes, max_advance_booking_days) VALUES
    ('Nha Khoa VibeCode', '08:00', '17:00', 30, 30);

-- Sample appointments
INSERT INTO appointments (patient_id, dentist_id, service_id, appointment_date, start_time, end_time, status, notes) VALUES
    (1, 1, 1, CURRENT_DATE + 1, '09:00', '09:30', 'CONFIRMED', 'Lần đầu khám'),
    (2, 2, 2, CURRENT_DATE + 2, '10:00', '10:45', 'PENDING',   NULL),
    (1, 1, 5, CURRENT_DATE + 3, '14:00', '15:00', 'PENDING',   'Yêu cầu tẩy trắng nhanh');
