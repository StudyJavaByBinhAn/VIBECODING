package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.DentistResponse;
import com.vibecode.antijob.dto.UpdateActiveRequest;
import com.vibecode.antijob.dto.UpdateDentistRequest;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.DentistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dentists")
@RequiredArgsConstructor
public class DentistController {

    private final DentistService dentistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DentistResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(dentistService.findAllActive()));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DentistResponse>> updateActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái thành công", dentistService.updateActive(id, req.isActive())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DentistResponse>> update(@PathVariable Long id, @Valid @RequestBody UpdateDentistRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ bác sĩ thành công", dentistService.update(id, req)));
    }
}
