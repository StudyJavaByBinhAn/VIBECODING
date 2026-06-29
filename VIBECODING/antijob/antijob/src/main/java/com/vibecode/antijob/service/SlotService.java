package com.vibecode.antijob.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlotService {

    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;

    /**
     * Trả về danh sách slot khả dụng cho bác sĩ vào ngày chỉ định.
     * Hiện tại mock: tất cả slot trong giờ làm việc đều trống.
     */
    public List<LocalTime> getAvailableSlots(Long dentistId, LocalDate date) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime current = OPEN;
        while (current.plusMinutes(SLOT_MINUTES).compareTo(CLOSE) <= 0) {
            slots.add(current);
            current = current.plusMinutes(SLOT_MINUTES);
        }
        return slots;
    }
}
