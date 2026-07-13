package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.ClinicSettingsResponse;
import com.vibecode.antijob.dto.UpdateClinicSettingsRequest;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.ClinicSettingsAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinic-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClinicSettingsController {

    private final ClinicSettingsAdminService clinicSettingsAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<ClinicSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(clinicSettingsAdminService.get()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ClinicSettingsResponse>> update(@Valid @RequestBody UpdateClinicSettingsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật cấu hình thành công", clinicSettingsAdminService.update(req)));
    }
}
