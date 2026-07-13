package com.vibecode.antijob.controller;

import com.vibecode.antijob.config.MethodSecurityTestConfig;
import com.vibecode.antijob.config.SecurityConfig;
import com.vibecode.antijob.dto.AppointmentRequest;
import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.dto.PageResponse;
import com.vibecode.antijob.enums.AppointmentStatus;
import com.vibecode.antijob.security.JwtAuthFilter;
import com.vibecode.antijob.security.RateLimitFilter;
import com.vibecode.antijob.service.AppointmentService;
import com.vibecode.antijob.service.SlotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AppointmentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}))
@Import(MethodSecurityTestConfig.class)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private SlotService slotService;

    private static UsernamePasswordAuthenticationToken principal(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private AppointmentResponse sampleResponse() {
        return AppointmentResponse.builder()
                .id(1L).patientName("Patient A").dentistName("BS A").serviceName("Khám tổng quát")
                .appointmentDate(LocalDate.of(2026, 7, 20))
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 30))
                .status(AppointmentStatus.PENDING).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_asAdmin_returns200() throws Exception {
        when(appointmentService.findAll(any())).thenReturn(
                PageResponse.<AppointmentResponse>builder().content(List.of(sampleResponse()))
                        .page(0).size(20).totalElements(1).totalPages(1).build());

        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void getAll_asPatient_returns403() throws Exception {
        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_ownerPatient_returns200() throws Exception {
        when(appointmentService.findById(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/appointments/1").principal(principal("patient@dental.vn", "PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getById_notOwner_returns403() throws Exception {
        when(appointmentService.findById(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new AccessDeniedException("Không có quyền truy cập"));

        mockMvc.perform(get("/api/appointments/1").principal(principal("other@dental.vn", "PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void book_validRequest_returns200() throws Exception {
        AppointmentRequest req = new AppointmentRequest();
        req.setServiceId(1L);
        req.setAppointmentDate(LocalDate.of(2026, 7, 20));
        req.setStartTime(LocalTime.of(9, 0));
        when(appointmentService.book(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/appointments").with(csrf())
                        .principal(principal("patient@dental.vn", "PATIENT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void book_missingServiceId_returns400() throws Exception {
        AppointmentRequest req = new AppointmentRequest();
        req.setAppointmentDate(LocalDate.of(2026, 7, 20));
        req.setStartTime(LocalTime.of(9, 0));

        mockMvc.perform(post("/api/appointments").with(csrf())
                        .principal(principal("patient@dental.vn", "PATIENT"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void book_asAdmin_returns403() throws Exception {
        AppointmentRequest req = new AppointmentRequest();
        req.setServiceId(1L);
        req.setAppointmentDate(LocalDate.of(2026, 7, 20));
        req.setStartTime(LocalTime.of(9, 0));

        mockMvc.perform(post("/api/appointments").with(csrf())
                        .principal(principal("admin@vibecode.local", "ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_ownerPatient_returns200() throws Exception {
        when(appointmentService.cancel(anyLong(), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/appointments/1/cancel").with(csrf())
                        .principal(principal("patient@dental.vn", "PATIENT")))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_pastDeadline_returns409() throws Exception {
        when(appointmentService.cancel(anyLong(), any()))
                .thenThrow(new IllegalStateException("Không thể huỷ lịch trong vòng 12 giờ trước giờ hẹn"));

        mockMvc.perform(patch("/api/appointments/1/cancel").with(csrf())
                        .principal(principal("patient@dental.vn", "PATIENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_receptionist_returns200() throws Exception {
        when(appointmentService.confirm(anyLong(), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/appointments/1/confirm").with(csrf())
                        .principal(principal("receptionist@dental.vn", "RECEPTIONIST")))
                .andExpect(status().isOk());
    }

    @Test
    void getSlots_returnsAvailableSlots() throws Exception {
        when(slotService.getAvailableSlots(1L, LocalDate.of(2026, 7, 20)))
                .thenReturn(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30)));

        mockMvc.perform(get("/api/slots").param("dentistId", "1").param("date", "2026-07-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
