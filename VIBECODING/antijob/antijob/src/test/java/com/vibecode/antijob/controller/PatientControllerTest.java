package com.vibecode.antijob.controller;

import com.vibecode.antijob.config.MethodSecurityTestConfig;
import com.vibecode.antijob.config.SecurityConfig;
import com.vibecode.antijob.dto.PatientResponse;
import com.vibecode.antijob.dto.UpdatePatientRequest;
import com.vibecode.antijob.security.JwtAuthFilter;
import com.vibecode.antijob.security.RateLimitFilter;
import com.vibecode.antijob.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}))
@Import(MethodSecurityTestConfig.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private PatientService patientService;

    private static UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken("patient@dental.vn", null);
    }

    private PatientResponse sample() {
        return PatientResponse.builder().id(1L).fullName("Patient A").phone("0900000002").build();
    }

    @Test
    void getMe_authenticated_returns200() throws Exception {
        when(patientService.getMe("patient@dental.vn")).thenReturn(sample());

        mockMvc.perform(get("/api/patients/me").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Patient A"));
    }

    @Test
    void updateMe_validRequest_returns200() throws Exception {
        when(patientService.updateMe(org.mockito.ArgumentMatchers.eq("patient@dental.vn"), any())).thenReturn(sample());
        UpdatePatientRequest req = new UpdatePatientRequest();
        req.setPhone("0900000009");

        mockMvc.perform(patch("/api/patients/me").with(csrf())
                        .principal(principal())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateMe_missingPhone_returns400() throws Exception {
        UpdatePatientRequest req = new UpdatePatientRequest();

        mockMvc.perform(patch("/api/patients/me").with(csrf())
                        .principal(principal())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
