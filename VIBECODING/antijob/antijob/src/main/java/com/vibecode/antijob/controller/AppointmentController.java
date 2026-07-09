package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.dto.PageResponse;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.AppointmentService;
import com.vibecode.antijob.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> getAll(
            @PageableDefault(size = 20, sort = "appointmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.findAll(pageable)));
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.findById(id, authentication)));
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(@Valid @RequestBody AppointmentRequest req, Authentication authentication) {
        AppointmentResponse created = appointmentService.book(req, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Đặt lịch thành công", created));
    }

    @PatchMapping("/appointments/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Huỷ lịch thành công", appointmentService.cancel(id, authentication)));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<LocalTime>>> getSlots(
            @RequestParam Long dentistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(slotService.getAvailableSlots(dentistId, date)));
    }
}
