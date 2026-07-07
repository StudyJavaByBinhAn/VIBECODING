# 03 — Domain Rules

## Slot Management (Core Logic)

**Công thức:**
```
Slot trống = Lịch làm việc BS − Lịch hẹn đã đặt − Giờ nghỉ trưa − Buffer time
```

**Bắt buộc:**
- Slot tính theo `duration_minutes` của dịch vụ (không cố định 30p).
- Có `buffer_minutes` giữa 2 ca (config trong `ClinicSettings`).
- Loại slot rơi vào `break_start` → `break_end`.
- Không trả slot quá khứ.
- Không trả slot ngoài `work_schedule` của bác sĩ.
- Không trả slot vượt `close_time` của `ClinicSettings`, kể cả khi `work_schedule` của bác sĩ cho phép muộn hơn (lớp chặn cứng riêng, phòng dữ liệu work_schedule cấu hình sai).

**Trạng thái: đã implement đầy đủ ở Phase 6 (`SlotService`, `AppointmentService`), có unit test (`SlotServiceTest`, `AppointmentServiceTest`).**

## Race Condition — Pessimistic Lock

```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ws FROM WorkSchedule ws WHERE ws.dentist.id = :dentistId AND ws.workDate = :date")
Optional<WorkSchedule> findForUpdate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

// Service
@Transactional
public AppointmentResponse bookAppointment(AppointmentRequest req, Long patientId) {
    // 1. Acquire lock
    // 2. Check conflict (SAU lock, TRƯỚC insert)
    // 3. Conflict → throw SlotAlreadyBookedException (409)
    // 4. OK → save appointment
    // 5. Evict slot cache
}
```

## Business Rules

| Rule | Điều kiện |
|------|-----------|
| Ngày hẹn | `>= today` |
| Giờ hẹn | Nằm trong `work_schedule` của BS, không rơi vào `break_start`-`break_end`, không vượt `close_time` |
| Bác sĩ | `is_active = true` — áp dụng cả khi client gửi `dentistId` tường minh lẫn khi auto-assign |
| Dịch vụ | `is_active = true` |
| Huỷ lịch | Chỉ `PENDING` hoặc `CONFIRMED` |
| Huỷ lịch | Trước ít nhất `cancel_before_hours` giờ (config, mặc định 12) |
| Auto-assign | Chọn BS ít appointment nhất trong ngày, trong số các BS active có `work_schedule` phù hợp + không conflict (đệm `buffer_minutes`) tại giờ yêu cầu |
| Giới hạn | Max `max_pending_appointments` (config, mặc định 3) lịch PENDING / patient |

## Appointment Status Flow

```
PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
   ↓          ↓
CANCELLED  CANCELLED
               ↓
            NO_SHOW
```
