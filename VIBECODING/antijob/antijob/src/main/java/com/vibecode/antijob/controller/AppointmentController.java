package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.AppointmentService;
import com.vibecode.antijob.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final SlotService slotService;

    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.findAll()));
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.findById(id)));
    }

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(@RequestBody AppointmentRequest req) {
        AppointmentResponse created = appointmentService.book(req);
        return ResponseEntity.ok(ApiResponse.ok("Đặt lịch thành công", created));
    }

    @PatchMapping("/appointments/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Huỷ lịch thành công", appointmentService.cancel(id)));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<LocalTime>>> getSlots(
            @RequestParam Long dentistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(slotService.getAvailableSlots(dentistId, date)));
    }
}
