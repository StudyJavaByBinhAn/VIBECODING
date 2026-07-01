# 🗄️ DATABASE DESIGN - MODULAR MONOLITH
## Hệ thống Đặt lịch Nha khoa (Dental Booking System)

---

## 📋 KHÁI QUÁT THIẾT KẾ

### Nguyên tắc Thiết kế
- ✅ **Modular Separation:** Mỗi phân hệ có bảng độc lập, có khóa chính riêng
- ✅ **Event-Based Integration:** Các phân hệ liên kết thông qua ID reference (không dùng FOREIGN KEY cứng)
- ✅ **Microservices Ready:** Dễ dàng tách riêng database khi chuyển sang Microservices
- ✅ **Concurrency Safe:** Composite Index + Optimistic Locking cho slot booking
- ✅ **MySQL Optimized:** Indexing chiến lược để tránh Full Table Scan

### Database Tools
- **MySQL 8.x** - Dữ liệu transactional (User, Doctor, Slot, Booking)
- **MongoDB** - Hồ sơ bệnh án nha khoa (nested documents)

---

# PHẦN 1: USER/PATIENT MODULE (Mô-đun Người dùng & Khách hàng)

## Mục đích
Quản lý thông tin bệnh nhân, hồ sơ, liên hệ, phân quyền.

---

## Bảng 1: `users`
Lưu thông tin cơ bản người dùng (bệnh nhân, nha sĩ, quản trị viên)

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `user_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | Email đăng nhập |
| `password_hash` | VARCHAR(255) | NOT NULL | Hash mật khẩu (bcrypt) |
| `full_name` | VARCHAR(255) | NOT NULL | Họ tên đầy đủ |
| `phone_number` | VARCHAR(20) | UNIQUE, NULLABLE | Số điện thoại |
| `user_type` | ENUM('PATIENT', 'DOCTOR', 'ADMIN') | NOT NULL | Phân loại người dùng |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Thời gian tạo |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | Thời gian cập nhật |
| `is_active` | BOOLEAN | DEFAULT TRUE | Trạng thái kích hoạt |
| `last_login` | TIMESTAMP | NULLABLE | Lần đăng nhập cuối |

### Indexes (Chỉ mục)

```sql
-- Tìm kiếm bệnh nhân nhanh theo email
CREATE UNIQUE INDEX idx_user_email ON users(email);

-- Tìm kiếm theo số điện thoại
CREATE INDEX idx_user_phone ON users(phone_number);

-- Tìm kiếm theo loại người dùng
CREATE INDEX idx_user_type ON users(user_type);

-- Lọc người dùng hoạt động
CREATE INDEX idx_user_active_created ON users(is_active, created_at);
```

---

## Bảng 2: `patients`
Lưu thông tin chi tiết bệnh nhân (mở rộng từ `users`)

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `patient_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `user_id` | BIGINT | NOT NULL, UNIQUE | Liên kết với users (NO FK) |
| `date_of_birth` | DATE | NULLABLE | Ngày sinh |
| `gender` | ENUM('MALE', 'FEMALE', 'OTHER') | NULLABLE | Giới tính |
| `address` | VARCHAR(500) | NULLABLE | Địa chỉ |
| `city` | VARCHAR(100) | NULLABLE | Thành phố |
| `allergies` | TEXT | NULLABLE | Dị ứng (JSON text) |
| `insurance_number` | VARCHAR(50) | NULLABLE | Số bảo hiểm |
| `emergency_contact` | VARCHAR(100) | NULLABLE | Liên hệ khẩn cấp |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Thời gian tạo |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | Thời gian cập nhật |

### Indexes

```sql
-- Tìm bệnh nhân theo user_id nhanh
CREATE UNIQUE INDEX idx_patient_user_id ON patients(user_id);

-- Tìm kiếm theo thành phố
CREATE INDEX idx_patient_city ON patients(city);

-- Lọc theo ngày sinh (tính tuổi)
CREATE INDEX idx_patient_dob ON patients(date_of_birth);
```

---

## Bảng 3: `patient_addresses` (Tùy chọn)
Lưu nhiều địa chỉ cho một bệnh nhân

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `address_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `patient_id` | BIGINT | NOT NULL, INDEX | Liên kết bệnh nhân (NO FK) |
| `address_type` | ENUM('HOME', 'WORK', 'OTHER') | NOT NULL | Loại địa chỉ |
| `street` | VARCHAR(255) | NOT NULL | Đường |
| `city` | VARCHAR(100) | NOT NULL | Thành phố |
| `postal_code` | VARCHAR(20) | NULLABLE | Mã bưu chính |
| `is_primary` | BOOLEAN | DEFAULT FALSE | Địa chỉ chính |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |

### Indexes

```sql
-- Tìm địa chỉ của bệnh nhân nhanh
CREATE INDEX idx_patient_addresses_patient_id ON patient_addresses(patient_id);

-- Tìm địa chỉ chính
CREATE INDEX idx_patient_addresses_primary ON patient_addresses(patient_id, is_primary);
```

---

# PHẦN 2: DOCTOR & SCHEDULE MODULE (Mô-đun Nha sĩ & Ca làm việc)

## Mục đích
Quản lý thông tin nha sĩ, chuyên môn, ca làm việc thường xuyên.

---

## Bảng 4: `doctors`
Lưu thông tin chi tiết nha sĩ

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `doctor_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `user_id` | BIGINT | NOT NULL, UNIQUE | Liên kết với users (NO FK) |
| `license_number` | VARCHAR(50) | UNIQUE, NOT NULL | Số giấy phép hành nghề |
| `specialization` | VARCHAR(100) | NOT NULL | Chuyên ngành (Implant, Niềng, v.v.) |
| `experience_years` | INT | DEFAULT 0 | Năm kinh nghiệm |
| `office_location` | VARCHAR(255) | NULLABLE | Vị trí phòng khám |
| `max_patients_per_day` | INT | DEFAULT 20 | Tối đa bệnh nhân/ngày |
| `consultation_duration_minutes` | INT | DEFAULT 30 | Thời lượng khám (phút) |
| `bio` | TEXT | NULLABLE | Tiểu sử nha sĩ |
| `avatar_url` | VARCHAR(500) | NULLABLE | URL ảnh đại diện |
| `is_available` | BOOLEAN | DEFAULT TRUE | Nha sĩ có hoạt động |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

### Indexes

```sql
-- Tìm nha sĩ theo user_id
CREATE UNIQUE INDEX idx_doctor_user_id ON doctors(user_id);

-- Tìm nha sĩ theo chuyên ngành
CREATE INDEX idx_doctor_specialization ON doctors(specialization);

-- Tìm nha sĩ hoạt động
CREATE INDEX idx_doctor_available ON doctors(is_available);

-- Tìm nha sĩ theo vị trí phòng khám
CREATE INDEX idx_doctor_location ON doctors(office_location);
```

---

## Bảng 5: `doctor_schedules`
Lưu ca làm việc thường xuyên của nha sĩ (ví dụ: Thứ 2-6, 8:00-17:00)

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `schedule_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `doctor_id` | BIGINT | NOT NULL, INDEX | Liên kết nha sĩ (NO FK) |
| `day_of_week` | ENUM('MONDAY', 'TUESDAY', ..., 'SUNDAY') | NOT NULL | Ngày trong tuần |
| `start_time` | TIME | NOT NULL | Giờ bắt đầu (08:00) |
| `end_time` | TIME | NOT NULL | Giờ kết thúc (17:00) |
| `is_active` | BOOLEAN | DEFAULT TRUE | Ca làm việc có hoạt động |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

### Indexes

```sql
-- Tìm ca làm việc của nha sĩ theo ngày
CREATE INDEX idx_doctor_schedule_day ON doctor_schedules(doctor_id, day_of_week, is_active);

-- Tìm nha sĩ làm việc vào ngày cụ thể
CREATE INDEX idx_doctor_schedule_lookup ON doctor_schedules(doctor_id, day_of_week);
```

### Ghi chú
- Bảng này lưu lịch làm việc **tạo thành** (recurring schedule)
- Khi cần tìm slot trống, join với `slots` để lấy slot khả dụng

---

## Bảng 6: `doctor_schedule_exceptions` (Tùy chọn)
Lưu ngoại lệ ca làm việc (nghỉ phép, ngày lễ, ca thêm)

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `exception_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `doctor_id` | BIGINT | NOT NULL, INDEX | Liên kết nha sĩ (NO FK) |
| `exception_date` | DATE | NOT NULL | Ngày ngoại lệ |
| `exception_type` | ENUM('DAY_OFF', 'EXTENDED_HOURS', 'HOLIDAY') | NOT NULL | Loại ngoại lệ |
| `start_time` | TIME | NULLABLE | Giờ bắt đầu nếu có |
| `end_time` | TIME | NULLABLE | Giờ kết thúc nếu có |
| `reason` | VARCHAR(255) | NULLABLE | Lý do |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |

### Indexes

```sql
-- Tìm ngày nghỉ của nha sĩ
CREATE INDEX idx_schedule_exception_date ON doctor_schedule_exceptions(doctor_id, exception_date);

-- Lọc theo loại ngoại lệ
CREATE INDEX idx_schedule_exception_type ON doctor_schedule_exceptions(exception_type);
```

---

# PHẦN 3: SLOT/AVAILABILITY MODULE (Mô-đun Khung giờ khám)

## Mục đích
Quản lý khung giờ khám có sẵn, trạng thái slot (trống, đã đặt, bảo lưu).

---

## Bảng 7: `appointment_slots` ⭐ **CRITICAL TABLE**
Lưu tất cả khung giờ khám có sẵn

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `slot_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `doctor_id` | BIGINT | NOT NULL, INDEX | Liên kết nha sĩ (NO FK) |
| `slot_date` | DATE | NOT NULL, INDEX | Ngày khám |
| `start_time` | TIME | NOT NULL | Giờ bắt đầu |
| `end_time` | TIME | NOT NULL | Giờ kết thúc |
| `status` | ENUM('AVAILABLE', 'BOOKED', 'RESERVED', 'CANCELLED') | DEFAULT 'AVAILABLE' | Trạng thái slot |
| `version` | BIGINT | DEFAULT 0 | **Optimistic Locking** - Phòng chống race condition |
| `booked_by` | BIGINT | NULLABLE, INDEX | ID bệnh nhân đã đặt (NO FK) |
| `reserved_until` | TIMESTAMP | NULLABLE | Thời gian giữ slot (nếu RESERVED) |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

### Indexes ⭐ **CRITICAL FOR PERFORMANCE**

```sql
-- ✅ COMPOSITE INDEX: Tìm slot trống của nha sĩ vào ngày cụ thể
-- THIS IS THE MAIN QUERY FOR SLOT SEARCH
CREATE INDEX idx_slot_doctor_date_status ON appointment_slots(
    doctor_id, 
    slot_date, 
    status,
    start_time
);

-- ✅ Tìm slot theo bệnh nhân đã đặt
CREATE INDEX idx_slot_booked_by ON appointment_slots(booked_by);

-- ✅ Tìm slot trong khoảng thời gian (ngày)
CREATE INDEX idx_slot_date_range ON appointment_slots(slot_date);

-- ✅ Tìm slot hết hạn giữ (để release)
CREATE INDEX idx_slot_reserved_until ON appointment_slots(
    status, 
    reserved_until
) WHERE status = 'RESERVED';

-- ✅ Tìm nhanh slot theo doctor + ngày
CREATE INDEX idx_slot_lookup ON appointment_slots(
    doctor_id, 
    slot_date
);
```

### Điều khoản Ràng buộc

```sql
-- Đảm bảo start_time < end_time
ALTER TABLE appointment_slots 
ADD CONSTRAINT chk_slot_time CHECK (start_time < end_time);

-- Đảm bảo booked_by không NULL nếu status = BOOKED
ALTER TABLE appointment_slots 
ADD CONSTRAINT chk_slot_booked CHECK (
    (status = 'BOOKED' AND booked_by IS NOT NULL) OR
    (status != 'BOOKED' AND booked_by IS NULL)
);
```

### 🔒 Concurrency Control
- **Redis Distributed Lock:** Khi book slot, apply lock key = `slot:{doctor_id}:{slot_date}:{start_time}`
- **Optimistic Locking:** Trường `version` được increment mỗi khi update. JPA `@Version` sẽ tự động kiểm tra
  ```
  UPDATE appointment_slots 
  SET status = 'BOOKED', version = version + 1 
  WHERE slot_id = ? AND version = ?
  ```

---

## Bảng 8: `slot_templates` (Tùy chọn)
Lưu template khung giờ để generate slots hàng loạt (giảm chứng chỉ thủ công)

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `template_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `doctor_id` | BIGINT | NOT NULL, INDEX | Liên kết nha sĩ (NO FK) |
| `day_of_week` | ENUM('MONDAY', ..., 'SUNDAY') | NOT NULL | Ngày lặp |
| `start_time` | TIME | NOT NULL | Giờ bắt đầu |
| `end_time` | TIME | NOT NULL | Giờ kết thúc |
| `slot_duration_minutes` | INT | DEFAULT 30 | Thời lượng mỗi slot |
| `break_time_start` | TIME | NULLABLE | Giờ nghỉ bắt đầu |
| `break_time_end` | TIME | NULLABLE | Giờ nghỉ kết thúc |
| `is_active` | BOOLEAN | DEFAULT TRUE | Template có hoạt động |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |

### Indexes

```sql
-- Tìm template của nha sĩ
CREATE INDEX idx_template_doctor ON slot_templates(doctor_id, is_active);

-- Tìm template theo ngày
CREATE INDEX idx_template_day ON slot_templates(doctor_id, day_of_week);
```

---

# PHẦN 4: BOOKING/APPOINTMENT MODULE (Mô-đun Đặt lịch & Trạng thái hẹn)

## Mục đích
Quản lý lịch hẹn, trạng thái cuộc hẹn, lịch sử tương tác.

---

## Bảng 9: `bookings` ⭐ **MAIN TRANSACTION TABLE**
Lưu thông tin đặt lịch hẹn

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `booking_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `patient_id` | BIGINT | NOT NULL, INDEX | Liên kết bệnh nhân (NO FK) |
| `doctor_id` | BIGINT | NOT NULL, INDEX | Liên kết nha sĩ (NO FK) |
| `slot_id` | BIGINT | NOT NULL, INDEX, UNIQUE | Liên kết slot (NO FK) |
| `booking_date` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Thời gian đặt lịch |
| `appointment_date` | DATE | NOT NULL, INDEX | Ngày cuộc hẹn |
| `appointment_time` | TIME | NOT NULL | Giờ cuộc hẹn |
| `appointment_end_time` | TIME | NOT NULL | Giờ kết thúc hẹn |
| `status` | ENUM('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW') | DEFAULT 'SCHEDULED' | Trạng thái hẹn |
| `visit_type` | ENUM('CONSULTATION', 'TREATMENT', 'FOLLOW_UP', 'EMERGENCY') | DEFAULT 'CONSULTATION' | Loại cuộc hẹn |
| `notes` | TEXT | NULLABLE | Ghi chú từ bệnh nhân |
| `cancellation_reason` | VARCHAR(255) | NULLABLE | Lý do hủy (nếu hủy) |
| `cancelled_at` | TIMESTAMP | NULLABLE | Thời gian hủy |
| `cancelled_by` | BIGINT | NULLABLE | Người hủy (user_id) |
| `version` | BIGINT | DEFAULT 0 | Optimistic Locking |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

### Indexes

```sql
-- ✅ CRITICAL: Tìm hẹn của bệnh nhân
CREATE INDEX idx_booking_patient_id ON bookings(patient_id, appointment_date);

-- ✅ CRITICAL: Tìm hẹn của nha sĩ vào ngày cụ thể
CREATE INDEX idx_booking_doctor_date_status ON bookings(
    doctor_id, 
    appointment_date, 
    status
);

-- ✅ Tìm hẹn theo slot (để kiểm tra trùng lặp)
CREATE UNIQUE INDEX idx_booking_slot_id ON bookings(slot_id);

-- ✅ Tìm hẹn theo ngày hẹn
CREATE INDEX idx_booking_appointment_date ON bookings(appointment_date);

-- ✅ Tìm hẹn chưa confirm
CREATE INDEX idx_booking_status ON bookings(status, appointment_date);

-- ✅ Tìm hẹn bị hủy để tracking
CREATE INDEX idx_booking_cancelled ON bookings(cancelled_by, cancelled_at);
```

### Điều khoản Ràng buộc

```sql
-- Đảm bảo appointment_time < appointment_end_time
ALTER TABLE bookings 
ADD CONSTRAINT chk_booking_time CHECK (appointment_time < appointment_end_time);

-- Đảm bảo slot_id unique (1 slot = 1 booking)
ALTER TABLE bookings 
ADD CONSTRAINT chk_booking_unique_slot UNIQUE (slot_id);

-- Đảm bảo appointment_date không ở quá khứ (soft check)
ALTER TABLE bookings 
ADD CONSTRAINT chk_booking_future_date CHECK (appointment_date >= CURDATE());
```

---

## Bảng 10: `booking_status_history`
Lưu lịch sử thay đổi trạng thái hẹn (audit trail)

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `history_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `booking_id` | BIGINT | NOT NULL, INDEX | Liên kết hẹn (NO FK) |
| `old_status` | ENUM(các status) | NULLABLE | Trạng thái cũ |
| `new_status` | ENUM(các status) | NOT NULL | Trạng thái mới |
| `changed_by` | BIGINT | NULLABLE, INDEX | User thực hiện (NO FK) |
| `reason` | VARCHAR(255) | NULLABLE | Lý do thay đổi |
| `changed_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Thời gian thay đổi |

### Indexes

```sql
-- Tìm lịch sử của hẹn
CREATE INDEX idx_history_booking_id ON booking_status_history(booking_id, changed_at);

-- Tìm thay đổi của người dùng
CREATE INDEX idx_history_changed_by ON booking_status_history(changed_by, changed_at);
```

---

## Bảng 11: `appointment_notes` (Tùy chọn)
Lưu ghi chú chi tiết về từng cuộc hẹn

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `note_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `booking_id` | BIGINT | NOT NULL, INDEX | Liên kết hẹn (NO FK) |
| `doctor_notes` | TEXT | NULLABLE | Ghi chú của nha sĩ (sau khám) |
| `patient_feedback` | VARCHAR(500) | NULLABLE | Phản hồi bệnh nhân |
| `recommended_treatment` | TEXT | NULLABLE | Hướng điều trị đề xuất |
| `follow_up_date` | DATE | NULLABLE | Ngày tái khám |
| `follow_up_slot_id` | BIGINT | NULLABLE, INDEX | Slot tái khám (NO FK) |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

### Indexes

```sql
-- Tìm ghi chú của hẹn
CREATE INDEX idx_note_booking_id ON appointment_notes(booking_id);

-- Tìm tái khám
CREATE INDEX idx_note_follow_up ON appointment_notes(follow_up_date, follow_up_slot_id);
```

---

## Bảng 12: `booking_cancellations` (Tùy chọn)
Lưu chi tiết hủy lịch

### Cấu trúc

| Trường | Kiểu | Constraints | Ghi chú |
|--------|------|-------------|---------|
| `cancellation_id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `booking_id` | BIGINT | NOT NULL, UNIQUE, INDEX | Liên kết hẹn (NO FK) |
| `cancelled_by_type` | ENUM('PATIENT', 'DOCTOR', 'ADMIN', 'SYSTEM') | NOT NULL | Loại người hủy |
| `cancelled_by_id` | BIGINT | NULLABLE, INDEX | ID người hủy (NO FK) |
| `cancellation_reason` | VARCHAR(255) | NOT NULL | Lý do hủy |
| `refund_status` | ENUM('PENDING', 'PROCESSED', 'REJECTED') | DEFAULT 'PENDING' | Trạng thái hoàn tiền |
| `refund_amount` | DECIMAL(10, 2) | NULLABLE | Số tiền hoàn lại |
| `cancelled_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Thời gian hủy |

### Indexes

```sql
-- Tìm hủy của hẹn
CREATE INDEX idx_cancellation_booking_id ON booking_cancellations(booking_id);

-- Tìm hủy theo loại
CREATE INDEX idx_cancellation_type ON booking_cancellations(cancelled_by_type, cancelled_at);

-- Tìm hoàn tiền chưa xử lý
CREATE INDEX idx_cancellation_refund ON booking_cancellations(refund_status, cancelled_at);
```

---

# PHẦN 5: MONGODB COLLECTIONS (Hồ sơ Bệnh án Nha khoa)

## Mục đích
Lưu trữ hồ sơ bệnh án nha khoa với cấu trúc linh hoạt (nested documents), sơ đồ răng, lịch sử điều trị.

---

## Collection 1: `patient_dental_records`
Lưu hồ sơ bệnh án nha khoa của bệnh nhân

### Cấu trúc (BSON Document)

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439011"),
  "patient_id": 123,  // Reference to MySQL users table
  "user_id": 456,     // Reference to MySQL users table (for quick lookup)
  
  // Thông tin cơ bản
  "full_name": "Nguyễn Văn A",
  "date_of_birth": ISODate("1990-05-15"),
  "gender": "MALE",
  
  // Sơ đồ Răng (Tooth Chart)
  "tooth_chart": {
    "teeth": [
      {
        "tooth_id": "11",  // FDI notation (1-8 = sectors, 1-2 = permanent/temporary)
        "status": "HEALTHY",  // HEALTHY, CAVITY, ROOT_CANAL, MISSING, IMPLANT
        "restoration_type": null,
        "notes": ""
      },
      {
        "tooth_id": "12",
        "status": "CAVITY",
        "restoration_type": "FILLING",
        "notes": "Cần điều trị sâu"
      }
      // ... 32 teeth total
    ],
    "last_updated": ISODate("2025-06-01"),
    "updated_by_doctor_id": 789
  },
  
  // Lịch sử Điều trị (Treatment History - Nested)
  "treatment_history": [
    {
      "treatment_id": ObjectId("507f1f77bcf86cd799439012"),
      "booking_id": 1001,
      "date": ISODate("2025-05-20"),
      "doctor_id": 789,
      "doctor_name": "Dr. Nguyễn Thị B",
      "visit_type": "TREATMENT",
      "treatment_type": "CAVITY_FILLING",
      "teeth_treated": ["11", "12"],
      "description": "Điều trị sâu răng trên mặt nhai",
      "materials_used": [
        {
          "material_name": "Composite Resin",
          "quantity": 2,
          "brand": "3M"
        }
      ],
      "cost": 500000,  // VND
      "notes": "Bệnh nhân hợp tác tốt",
      "follow_up_required": true,
      "follow_up_date": ISODate("2025-06-20")
    },
    {
      "treatment_id": ObjectId("507f1f77bcf86cd799439013"),
      "booking_id": 1002,
      "date": ISODate("2025-06-15"),
      "doctor_id": 790,
      "doctor_name": "Dr. Trần Văn C",
      "visit_type": "FOLLOW_UP",
      "treatment_type": "FOLLOW_UP_CHECK",
      "teeth_treated": ["11", "12"],
      "description": "Kiểm tra điều trị sâu",
      "materials_used": [],
      "cost": 0,
      "notes": "Tình trạng tốt, không cần can thiệp thêm",
      "follow_up_required": false
    }
    // ... lịch sử điều trị
  ],
  
  // Kế hoạch Điều trị (Treatment Plan)
  "treatment_plan": {
    "created_date": ISODate("2025-05-15"),
    "created_by_doctor_id": 789,
    "plan_description": "Điều trị sâu toàn bộ các răng",
    "estimated_duration": "3 months",
    "estimated_cost": 5000000,
    "priority_treatments": [
      {
        "treatment": "CAVITY_FILLING",
        "teeth": ["11", "12", "13"],
        "priority": "HIGH",
        "estimated_cost": 1500000
      },
      {
        "treatment": "SCALING_POLISHING",
        "teeth": [],
        "priority": "MEDIUM",
        "estimated_cost": 500000
      }
    ],
    "notes": "Bệnh nhân cần chỉnh sửa kỹ thuật chải răng",
    "status": "ACTIVE"  // ACTIVE, COMPLETED, CANCELLED
  },
  
  // Dị ứng & Bệnh Kèm Theo
  "medical_conditions": {
    "allergies": ["Penicillin", "Latex"],
    "chronic_diseases": ["Diabetes Type 2"],
    "medications": ["Metformin 500mg"],
    "notes": "Kiểm soát lượng đường trong máu"
  },
  
  // Lịch sử X-quang
  "radiographs": [
    {
      "radiograph_id": ObjectId("507f1f77bcf86cd799439014"),
      "date": ISODate("2025-05-20"),
      "type": "PANORAMIC",  // PERIAPICAL, PANORAMIC, BITEWINGS
      "url": "https://s3.dental.com/radiographs/123_panoramic_2025_05_20.jpg",
      "findings": "Sâu räng ở hình 11, 12",
      "uploaded_by_doctor_id": 789
    }
  ],
  
  // Audit & Timestamps
  "created_at": ISODate("2020-01-15"),
  "updated_at": ISODate("2025-06-15"),
  "created_by_doctor_id": 789
}
```

### Indexes (MongoDB)

```javascript
// Tìm bệnh nhân nhanh
db.patient_dental_records.createIndex({ "patient_id": 1 });
db.patient_dental_records.createIndex({ "user_id": 1 });

// Tìm hồ sơ được cập nhật gần đây
db.patient_dental_records.createIndex({ "updated_at": -1 });

// Tìm bệnh nhân theo ngày sinh (analytics)
db.patient_dental_records.createIndex({ "date_of_birth": 1 });

// Tìm trong lịch sử điều trị
db.patient_dental_records.createIndex({ "treatment_history.date": 1 });

// Tìm bệnh nhân có kế hoạch điều trị ACTIVE
db.patient_dental_records.createIndex({ 
  "treatment_plan.status": 1, 
  "treatment_plan.created_date": 1 
});

// Tìm bệnh nhân có dị ứng (queries phức tạp)
db.patient_dental_records.createIndex({ "medical_conditions.allergies": 1 });
```

---

## Collection 2: `dental_materials_inventory` (Tùy chọn)
Quản lý tồn kho vật liệu nha khoa

### Cấu trúc

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439020"),
  "material_id": "COMPOSITE_3M_001",
  "material_name": "3M Composite Resin A2",
  "category": "RESTORATIVE",  // RESTORATIVE, CROWN, FILLING, etc.
  "manufacturer": "3M ESPE",
  "batch_number": "ABC12345",
  "expiry_date": ISODate("2026-12-31"),
  "quantity_in_stock": 15,
  "unit": "SYRINGE",
  "unit_cost": 50000,  // VND
  "reorder_level": 5,
  "supplier_id": "SUP_001",
  "created_at": ISODate("2025-01-01"),
  "updated_at": ISODate("2025-06-15")
}
```

### Indexes

```javascript
db.dental_materials_inventory.createIndex({ "material_id": 1 });
db.dental_materials_inventory.createIndex({ "category": 1 });
db.dental_materials_inventory.createIndex({ "expiry_date": 1 });
db.dental_materials_inventory.createIndex({ "quantity_in_stock": 1 });
```

---

# PHẦN 6: REDIS CACHE & LOCKS

## Cache Keys (Cấu trúc naming convention)

```
// Slot Availability Cache
slots:doctor:{doctor_id}:date:{slot_date} = [list of available slots]
slots:doctor:{doctor_id}:week = {JSON of weekly availability}

// Patient Bookings
bookings:patient:{patient_id} = [list of booking IDs]
bookings:patient:{patient_id}:upcoming = [next 5 bookings]

// Doctor Info Cache
doctor:{doctor_id}:profile = {doctor details}
doctor:{doctor_id}:schedule = {doctor schedule}

// Session Cache
session:{session_id} = {user info, roles}
```

## Distributed Locks (Để chống trùng lịch)

```
// Lock Key Format
lock:slot:book:{doctor_id}:{slot_date}:{start_time}

// Redisson Configuration
RLock lock = redissonClient.getLock("lock:slot:book:" + doctorId + ":" + slotDate + ":" + startTime);
try {
    lock.lock(30, TimeUnit.SECONDS);  // Timeout 30s
    // Book slot logic
} finally {
    lock.unlock();
}

// Alternative: Try-lock với timeout
boolean lockAcquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
if (lockAcquired) {
    // Proceed with booking
} else {
    throw new SlotAlreadyBookedException();
}
```

---

# PHẦN 7: DATA CONSISTENCY & TRANSACTION WORKFLOW

## Booking Flow with Concurrency Protection

```
1. Client requests available slots for doctor on date D
   → Query: SELECT * FROM appointment_slots 
     WHERE doctor_id = ? AND slot_date = ? AND status = 'AVAILABLE'
   → Use Index: idx_slot_doctor_date_status ✅

2. Client selects slot S and initiates booking
   → Acquire Redis Lock: lock:slot:book:{doctor_id}:{date}:{start_time}
   → Lock timeout: 30 seconds

3. Within lock scope:
   a) Start DB transaction
   b) SELECT slot WHERE slot_id = ? FOR UPDATE (pessimistic lock within tx)
   c) Check slot status AVAILABLE (double-check)
   d) INSERT into bookings table
   e) UPDATE appointment_slots SET status = 'BOOKED', version = version + 1
   f) INSERT into booking_status_history
   g) COMMIT transaction

4. Release Redis lock

5. On Optimistic Lock Conflict:
   → Retry step 3 (if version mismatch)
   → Max retries: 3
   → Back-off: 100ms + random(0-500ms)
```

---

# PHẦN 8: DATABASE INITIALIZATION SCRIPTS

## MySQL Creation (Highlights)

```sql
-- Create Database
CREATE DATABASE IF NOT EXISTS dental_booking_system 
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE dental_booking_system;

-- USERS TABLE
CREATE TABLE users (
  user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  phone_number VARCHAR(20) UNIQUE,
  user_type ENUM('PATIENT', 'DOCTOR', 'ADMIN') NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_active BOOLEAN DEFAULT TRUE,
  last_login TIMESTAMP NULL,
  KEY idx_user_email (email),
  KEY idx_user_phone (phone_number),
  KEY idx_user_type (user_type),
  KEY idx_user_active_created (is_active, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PATIENTS TABLE
CREATE TABLE patients (
  patient_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  date_of_birth DATE,
  gender ENUM('MALE', 'FEMALE', 'OTHER'),
  address VARCHAR(500),
  city VARCHAR(100),
  allergies TEXT,
  insurance_number VARCHAR(50),
  emergency_contact VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_patient_user_id (user_id),
  KEY idx_patient_city (city),
  KEY idx_patient_dob (date_of_birth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- APPOINTMENT_SLOTS TABLE (CRITICAL)
CREATE TABLE appointment_slots (
  slot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doctor_id BIGINT NOT NULL,
  slot_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status ENUM('AVAILABLE', 'BOOKED', 'RESERVED', 'CANCELLED') DEFAULT 'AVAILABLE',
  version BIGINT DEFAULT 0,
  booked_by BIGINT,
  reserved_until TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  CONSTRAINT chk_slot_time CHECK (start_time < end_time),
  CONSTRAINT chk_slot_booked CHECK (
    (status = 'BOOKED' AND booked_by IS NOT NULL) OR
    (status != 'BOOKED' AND booked_by IS NULL)
  ),
  
  KEY idx_slot_doctor_date_status (doctor_id, slot_date, status, start_time),
  KEY idx_slot_booked_by (booked_by),
  KEY idx_slot_date_range (slot_date),
  KEY idx_slot_reserved_until (status, reserved_until),
  KEY idx_slot_lookup (doctor_id, slot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BOOKINGS TABLE (MAIN TRANSACTION)
CREATE TABLE bookings (
  booking_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  slot_id BIGINT NOT NULL,
  booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  appointment_date DATE NOT NULL,
  appointment_time TIME NOT NULL,
  appointment_end_time TIME NOT NULL,
  status ENUM('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW') DEFAULT 'SCHEDULED',
  visit_type ENUM('CONSULTATION', 'TREATMENT', 'FOLLOW_UP', 'EMERGENCY') DEFAULT 'CONSULTATION',
  notes TEXT,
  cancellation_reason VARCHAR(255),
  cancelled_at TIMESTAMP NULL,
  cancelled_by BIGINT,
  version BIGINT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  CONSTRAINT chk_booking_time CHECK (appointment_time < appointment_end_time),
  CONSTRAINT chk_booking_unique_slot UNIQUE (slot_id),
  CONSTRAINT chk_booking_future_date CHECK (appointment_date >= CURDATE()),
  
  KEY idx_booking_patient_id (patient_id, appointment_date),
  KEY idx_booking_doctor_date_status (doctor_id, appointment_date, status),
  UNIQUE KEY idx_booking_slot_id (slot_id),
  KEY idx_booking_appointment_date (appointment_date),
  KEY idx_booking_status (status, appointment_date),
  KEY idx_booking_cancelled (cancelled_by, cancelled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ... (More tables as defined above)
```

---

# PHẦN 9: OPTIMIZATION STRATEGIES

## Query Optimization

### ❌ SLOW - Full Table Scan
```sql
-- BAD: Lấy tất cả slot của bệnh nhân vào ngày
SELECT * FROM appointment_slots 
WHERE status = 'AVAILABLE';  -- Full table scan!
```

### ✅ FAST - Using Composite Index
```sql
-- GOOD: Lấy slot trống của nha sĩ vào ngày cụ thể
SELECT * FROM appointment_slots 
WHERE doctor_id = 123 
  AND slot_date = '2025-06-25' 
  AND status = 'AVAILABLE'
ORDER BY start_time;
-- Uses: idx_slot_doctor_date_status ✅
```

## Batch Operations

```sql
-- Generate slots hàng loạt từ template
INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, status)
SELECT 
  st.doctor_id,
  DATE_ADD(CURDATE(), INTERVAL ? DAY),
  st.start_time,
  st.end_time,
  'AVAILABLE'
FROM slot_templates st
WHERE st.doctor_id = ? 
  AND DAYNAME(DATE_ADD(CURDATE(), INTERVAL ? DAY)) = st.day_of_week
  AND st.is_active = TRUE;
```

## Connection Pooling (HikariCP)
- Pool size: 10-20 connections
- Max lifetime: 30 minutes
- Idle timeout: 10 minutes

## Caching Strategy
- Cache doctor info (TTL: 1 hour)
- Cache slot availability (TTL: 5 minutes)
- Cache user sessions (TTL: 24 hours)
- Invalidate cache on INSERT/UPDATE/DELETE

---

# PHẦN 10: MONITORING & ALERTING

## Metrics to Monitor

```
1. Slot Availability Query Latency (Target: < 100ms)
   - Query: idx_slot_doctor_date_status usage
   
2. Booking Transaction Duration (Target: < 500ms)
   - Lock acquisition time
   - DB transaction time
   
3. Distributed Lock Contention (Target: < 5% failed locks)
   - Redis lock wait time
   - Lock timeout events
   
4. Table Sizes
   - appointment_slots: Monitor growth (should grow ~300-500 rows/day per doctor)
   - bookings: Monitor growth (should grow ~50-100 rows/day)
   
5. Index Hit Ratio
   - Monitor slow query log
   - Detect missing indexes
```

## Slow Query Log Configuration

```sql
-- Enable slow query log
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.5;  -- Queries > 500ms

-- Monitor slow queries
SELECT * FROM mysql.slow_log LIMIT 10;
```

---

# TỔNG KHOÁT THIẾT KẾ

## Module Hierarchy

```
┌─────────────────────────────────────────────────────┐
│           MODULAR MONOLITH STRUCTURE               │
├─────────────────────────────────────────────────────┤
│                                                      │
│  MODULE 1: USER/PATIENT                             │
│  ├─ users (PK: user_id)                             │
│  ├─ patients (NO FK, only user_id reference)        │
│  └─ patient_addresses (NO FK, only patient_id ref)  │
│                                                      │
│  MODULE 2: DOCTOR & SCHEDULE                        │
│  ├─ doctors (NO FK, only user_id reference)         │
│  ├─ doctor_schedules (NO FK, only doctor_id ref)    │
│  └─ doctor_schedule_exceptions (NO FK)              │
│                                                      │
│  MODULE 3: SLOT/AVAILABILITY ⭐                     │
│  ├─ appointment_slots (NO FK, doctor_id reference)  │
│  └─ slot_templates (NO FK, doctor_id reference)     │
│                                                      │
│  MODULE 4: BOOKING/APPOINTMENT                      │
│  ├─ bookings (NO FK, all refs only)                 │
│  ├─ booking_status_history (NO FK)                  │
│  ├─ appointment_notes (NO FK)                       │
│  └─ booking_cancellations (NO FK)                   │
│                                                      │
│  MONGODB COLLECTIONS                               │
│  ├─ patient_dental_records                          │
│  └─ dental_materials_inventory                      │
│                                                      │
│  REDIS CACHE & LOCKS                                │
│  ├─ Slot availability cache                         │
│  ├─ Distributed locks for bookings                  │
│  └─ Session cache                                   │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## Critical Indexes Summary

| Index | Table | Purpose | Query Type |
|-------|-------|---------|-----------|
| `idx_slot_doctor_date_status` | appointment_slots | Find available slots | SELECT |
| `idx_booking_patient_id` | bookings | List patient's bookings | SELECT |
| `idx_booking_doctor_date_status` | bookings | Doctor's schedule view | SELECT |
| `idx_booking_slot_id` | bookings | Prevent double-booking | UNIQUE |
| `idx_user_email` | users | Login queries | SELECT |
| `idx_doctor_specialization` | doctors | Find doctors by specialty | SELECT |

---

# ✅ KẾT LUẬN

Thiết kế này cung cấp:
- ✅ **Modular Separation:** 4 phân hệ độc lập, dễ tách sang Microservices
- ✅ **Concurrency Safety:** Redis Locks + Optimistic Locking
- ✅ **Query Performance:** Strategic Composite Indexes
- ✅ **Data Consistency:** Constraints + Version control
- ✅ **Flexibility:** MongoDB for flexible medical records
- ✅ **Scalability:** Chuẩn bị cho event-driven architecture (Phase 2)
