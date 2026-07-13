package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.CreateWorkScheduleRequest;
import com.vibecode.antijob.dto.UpdateWorkScheduleRequest;
import com.vibecode.antijob.dto.WorkScheduleResponse;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.WorkScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    @GetMapping("/dentists/{dentistId}/work-schedules")
    public ResponseEntity<ApiResponse<List<WorkScheduleResponse>>> getByDentist(@PathVariable Long dentistId) {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleService.findByDentist(dentistId)));
    }

    @PostMapping("/dentists/{dentistId}/work-schedules")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> create(@PathVariable Long dentistId, @Valid @RequestBody CreateWorkScheduleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo lịch làm việc thành công", workScheduleService.create(dentistId, req)));
    }

    @PutMapping("/work-schedules/{id}")
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> update(@PathVariable Long id, @Valid @RequestBody UpdateWorkScheduleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật lịch làm việc thành công", workScheduleService.update(id, req)));
    }

    @DeleteMapping("/work-schedules/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        workScheduleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Xoá lịch làm việc thành công", null));
    }
}
