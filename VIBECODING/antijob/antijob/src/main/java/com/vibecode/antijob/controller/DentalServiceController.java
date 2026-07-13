package com.vibecode.antijob.controller;

import com.vibecode.antijob.dto.CreateDentalServiceRequest;
import com.vibecode.antijob.dto.DentalServiceResponse;
import com.vibecode.antijob.dto.UpdateActiveRequest;
import com.vibecode.antijob.exception.ApiResponse;
import com.vibecode.antijob.service.DentalServiceCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class DentalServiceController {

    private final DentalServiceCatalogService dentalServiceCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DentalServiceResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(dentalServiceCatalogService.findAllActive()));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DentalServiceResponse>> updateActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái thành công", dentalServiceCatalogService.updateActive(id, req.isActive())));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DentalServiceResponse>> create(@Valid @RequestBody CreateDentalServiceRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo dịch vụ thành công", dentalServiceCatalogService.create(req)));
    }
}
