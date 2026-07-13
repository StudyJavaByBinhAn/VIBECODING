package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.CreateWorkScheduleRequest;
import com.vibecode.antijob.dto.UpdateWorkScheduleRequest;
import com.vibecode.antijob.dto.WorkScheduleResponse;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.mapper.WorkScheduleMapper;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkScheduleServiceTest {

    @Mock
    private WorkScheduleRepository workScheduleRepository;
    @Mock
    private DentistRepository dentistRepository;
    @Mock
    private WorkScheduleMapper workScheduleMapper;

    private WorkScheduleService workScheduleService;

    private Dentist dentist;

    @BeforeEach
    void setUp() {
        workScheduleService = new WorkScheduleService(workScheduleRepository, dentistRepository, workScheduleMapper);
        dentist = Dentist.builder().id(10L).fullName("BS A").active(true).build();
    }

    private CreateWorkScheduleRequest createRequest(DayOfWeek day, LocalTime start, LocalTime end) {
        CreateWorkScheduleRequest req = new CreateWorkScheduleRequest();
        req.setDayOfWeek(day);
        req.setStartTime(start);
        req.setEndTime(end);
        req.setActive(true);
        return req;
    }

    // ---------- create() ----------

    @Test
    void create_happyPath_savesSchedule() {
        CreateWorkScheduleRequest req = createRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));

        when(dentistRepository.findById(10L)).thenReturn(Optional.of(dentist));
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(10L, DayOfWeek.MONDAY)).thenReturn(Optional.empty());
        when(workScheduleRepository.save(any(WorkSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workScheduleMapper.toResponse(any(WorkSchedule.class))).thenReturn(WorkScheduleResponse.builder().build());

        workScheduleService.create(10L, req);

        ArgumentCaptor<WorkSchedule> captor = ArgumentCaptor.forClass(WorkSchedule.class);
        verify(workScheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getDentist()).isEqualTo(dentist);
        assertThat(captor.getValue().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void create_startAfterEnd_throwsIllegalArgument_skipsLookup() {
        CreateWorkScheduleRequest req = createRequest(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(8, 0));

        assertThatThrownBy(() -> workScheduleService.create(10L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dentistRepository, never()).findById(any());
    }

    @Test
    void create_dentistNotFound_throwsIllegalArgument() {
        CreateWorkScheduleRequest req = createRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));

        when(dentistRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workScheduleService.create(10L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(workScheduleRepository, never()).save(any());
    }

    @Test
    void create_dayAlreadyScheduled_throwsIllegalArgument() {
        CreateWorkScheduleRequest req = createRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));

        when(dentistRepository.findById(10L)).thenReturn(Optional.of(dentist));
        when(workScheduleRepository.findByDentistIdAndDayOfWeek(10L, DayOfWeek.MONDAY))
                .thenReturn(Optional.of(WorkSchedule.builder().build()));

        assertThatThrownBy(() -> workScheduleService.create(10L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(workScheduleRepository, never()).save(any());
    }

    // ---------- update() ----------

    @Test
    void update_happyPath_updatesFields() {
        WorkSchedule existing = WorkSchedule.builder().id(1L).dentist(dentist).dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build();

        UpdateWorkScheduleRequest req = new UpdateWorkScheduleRequest();
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(18, 0));
        req.setActive(false);

        when(workScheduleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workScheduleRepository.save(any(WorkSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workScheduleMapper.toResponse(any(WorkSchedule.class))).thenReturn(WorkScheduleResponse.builder().build());

        workScheduleService.update(1L, req);

        assertThat(existing.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(existing.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void update_startAfterEnd_throwsIllegalArgument_skipsLookup() {
        UpdateWorkScheduleRequest req = new UpdateWorkScheduleRequest();
        req.setStartTime(LocalTime.of(18, 0));
        req.setEndTime(LocalTime.of(9, 0));

        assertThatThrownBy(() -> workScheduleService.update(1L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(workScheduleRepository, never()).findById(any());
    }

    @Test
    void update_notFound_throwsIllegalArgument() {
        UpdateWorkScheduleRequest req = new UpdateWorkScheduleRequest();
        req.setStartTime(LocalTime.of(8, 0));
        req.setEndTime(LocalTime.of(17, 0));

        when(workScheduleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workScheduleService.update(1L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- delete() ----------

    @Test
    void delete_existing_deletesSuccessfully() {
        when(workScheduleRepository.existsById(1L)).thenReturn(true);

        workScheduleService.delete(1L);

        verify(workScheduleRepository).deleteById(1L);
    }

    @Test
    void delete_notFound_throwsIllegalArgument() {
        when(workScheduleRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> workScheduleService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(workScheduleRepository, never()).deleteById(any());
    }
}
