package com.vibecode.antijob.controller;

import com.vibecode.antijob.config.MethodSecurityTestConfig;
import com.vibecode.antijob.config.SecurityConfig;
import com.vibecode.antijob.dto.ClinicSettingsResponse;
import com.vibecode.antijob.dto.UpdateClinicSettingsRequest;
import com.vibecode.antijob.security.JwtAuthFilter;
import com.vibecode.antijob.security.RateLimitFilter;
import com.vibecode.antijob.service.ClinicSettingsAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClinicSettingsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}))
@Import(MethodSecurityTestConfig.class)
class ClinicSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ClinicSettingsAdminService clinicSettingsAdminService;

    private ClinicSettingsResponse sample() {
        return ClinicSettingsResponse.builder().id(1L).clinicName("Dental Clinic")
                .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30).maxAdvanceBookingDays(30).bufferMinutes(10)
                .cancelBeforeHours(12).maxPendingAppointments(3).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_asAdmin_returns200() throws Exception {
        when(clinicSettingsAdminService.get()).thenReturn(sample());

        mockMvc.perform(get("/api/clinic-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinicName").value("Dental Clinic"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void get_asPatient_returns403() throws Exception {
        mockMvc.perform(get("/api/clinic-settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_validRequest_returns200() throws Exception {
        when(clinicSettingsAdminService.update(any())).thenReturn(sample());
        UpdateClinicSettingsRequest req = new UpdateClinicSettingsRequest();
        req.setClinicName("Dental Clinic");
        req.setOpenTime(LocalTime.of(8, 0));
        req.setCloseTime(LocalTime.of(17, 0));
        req.setSlotDurationMinutes(30);
        req.setMaxAdvanceBookingDays(30);
        req.setBufferMinutes(10);
        req.setCancelBeforeHours(12);
        req.setMaxPendingAppointments(3);

        mockMvc.perform(put("/api/clinic-settings").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_missingClinicName_returns400() throws Exception {
        UpdateClinicSettingsRequest req = new UpdateClinicSettingsRequest();
        req.setOpenTime(LocalTime.of(8, 0));
        req.setCloseTime(LocalTime.of(17, 0));
        req.setSlotDurationMinutes(30);
        req.setMaxAdvanceBookingDays(30);
        req.setCancelBeforeHours(12);
        req.setMaxPendingAppointments(3);

        mockMvc.perform(put("/api/clinic-settings").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
