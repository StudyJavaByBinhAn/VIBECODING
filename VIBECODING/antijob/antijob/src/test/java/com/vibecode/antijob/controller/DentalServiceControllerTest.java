package com.vibecode.antijob.controller;

import com.vibecode.antijob.config.MethodSecurityTestConfig;
import com.vibecode.antijob.config.SecurityConfig;
import com.vibecode.antijob.dto.CreateDentalServiceRequest;
import com.vibecode.antijob.dto.DentalServiceResponse;
import com.vibecode.antijob.dto.UpdateActiveRequest;
import com.vibecode.antijob.security.JwtAuthFilter;
import com.vibecode.antijob.security.RateLimitFilter;
import com.vibecode.antijob.service.DentalServiceCatalogService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DentalServiceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}))
@Import(MethodSecurityTestConfig.class)
class DentalServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private DentalServiceCatalogService dentalServiceCatalogService;

    private DentalServiceResponse sample() {
        return DentalServiceResponse.builder().id(1L).name("Khám tổng quát")
                .durationMinutes(30).price(BigDecimal.valueOf(200_000)).active(true).build();
    }

    @Test
    void getActive_noAuthRequired_returns200() throws Exception {
        when(dentalServiceCatalogService.findAllActive()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateActive_asAdmin_returns200() throws Exception {
        when(dentalServiceCatalogService.updateActive(anyLong(), anyBoolean())).thenReturn(sample());
        UpdateActiveRequest req = new UpdateActiveRequest();
        req.setActive(false);

        mockMvc.perform(patch("/api/services/1/active").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void updateActive_asPatient_returns403() throws Exception {
        UpdateActiveRequest req = new UpdateActiveRequest();
        req.setActive(false);

        mockMvc.perform(patch("/api/services/1/active").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_validRequest_returns200() throws Exception {
        when(dentalServiceCatalogService.create(any())).thenReturn(sample());
        CreateDentalServiceRequest req = new CreateDentalServiceRequest();
        req.setName("Khám tổng quát");
        req.setDurationMinutes(30);
        req.setPrice(BigDecimal.valueOf(200_000));

        mockMvc.perform(post("/api/services").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_negativeDuration_returns400() throws Exception {
        CreateDentalServiceRequest req = new CreateDentalServiceRequest();
        req.setName("Khám tổng quát");
        req.setDurationMinutes(-5);
        req.setPrice(BigDecimal.valueOf(200_000));

        mockMvc.perform(post("/api/services").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
