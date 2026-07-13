package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.PatientResponse;
import com.vibecode.antijob.dto.UpdatePatientRequest;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PatientResponse>> getMe(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.getMe(authentication.getName())));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<PatientResponse>> updateMe(@Valid @RequestBody UpdatePatientRequest req, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật hồ sơ thành công", patientService.updateMe(authentication.getName(), req)));
    }
}
