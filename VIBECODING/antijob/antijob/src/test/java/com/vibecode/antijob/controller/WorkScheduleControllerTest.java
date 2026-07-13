package com.vibecode.antijob.controller;

import com.vibecode.antijob.config.MethodSecurityTestConfig;
import com.vibecode.antijob.config.SecurityConfig;
import com.vibecode.antijob.dto.CreateWorkScheduleRequest;
import com.vibecode.antijob.dto.UpdateWorkScheduleRequest;
import com.vibecode.antijob.dto.WorkScheduleResponse;
import com.vibecode.antijob.security.JwtAuthFilter;
import com.vibecode.antijob.security.RateLimitFilter;
import com.vibecode.antijob.service.WorkScheduleService;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkScheduleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}))
@Import(MethodSecurityTestConfig.class)
class WorkScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private WorkScheduleService workScheduleService;

    private WorkScheduleResponse sample() {
        return WorkScheduleResponse.builder().id(1L).dentistId(1L).dentistName("BS A")
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .active(true).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getByDentist_asAdmin_returns200() throws Exception {
        when(workScheduleService.findByDentist(1L)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/dentists/1/work-schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "DENTIST")
    void getByDentist_asDentist_returns403() throws Exception {
        mockMvc.perform(get("/api/dentists/1/work-schedules"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_validRequest_returns200() throws Exception {
        when(workScheduleService.create(anyLong(), any())).thenReturn(sample());
        CreateWorkScheduleRequest req = new CreateWorkScheduleRequest();
        req.setDayOfWeek(DayOfWeek.MONDAY);
        req.setStartTime(LocalTime.of(8, 0));
        req.setEndTime(LocalTime.of(17, 0));

        mockMvc.perform(post("/api/dentists/1/work-schedules").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_missingDayOfWeek_returns400() throws Exception {
        CreateWorkScheduleRequest req = new CreateWorkScheduleRequest();
        req.setStartTime(LocalTime.of(8, 0));
        req.setEndTime(LocalTime.of(17, 0));

        mockMvc.perform(post("/api/dentists/1/work-schedules").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_validRequest_returns200() throws Exception {
        when(workScheduleService.update(anyLong(), any())).thenReturn(sample());
        UpdateWorkScheduleRequest req = new UpdateWorkScheduleRequest();
        req.setStartTime(LocalTime.of(8, 0));
        req.setEndTime(LocalTime.of(12, 0));
        req.setActive(true);

        mockMvc.perform(put("/api/work-schedules/1").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_existingSchedule_returns200() throws Exception {
        mockMvc.perform(delete("/api/work-schedules/1").with(csrf()))
                .andExpect(status().isOk());
    }
}
