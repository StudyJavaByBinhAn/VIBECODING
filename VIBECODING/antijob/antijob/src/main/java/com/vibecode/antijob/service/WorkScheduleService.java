package com.vibecode.antijob.service;

import com.vibecode.antijob.dto.CreateWorkScheduleRequest;
import com.vibecode.antijob.dto.UpdateWorkScheduleRequest;
import com.vibecode.antijob.dto.WorkScheduleResponse;
import com.vibecode.antijob.entity.Dentist;
import com.vibecode.antijob.entity.WorkSchedule;
import com.vibecode.antijob.mapper.WorkScheduleMapper;
import com.vibecode.antijob.repository.DentistRepository;
import com.vibecode.antijob.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final DentistRepository dentistRepository;
    private final WorkScheduleMapper workScheduleMapper;

    @Transactional(readOnly = true)
    public List<WorkScheduleResponse> findByDentist(Long dentistId) {
        return workScheduleRepository.findByDentistId(dentistId).stream()
                .map(workScheduleMapper::toResponse)
                .toList();
    }

    @Transactional
    public WorkScheduleResponse create(Long dentistId, CreateWorkScheduleRequest req) {
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new IllegalArgumentException("startTime phải trước endTime");
        }

        Dentist dentist = dentistRepository.findById(dentistId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ id=" + dentistId));

        if (workScheduleRepository.findByDentistIdAndDayOfWeek(dentistId, req.getDayOfWeek()).isPresent()) {
            throw new IllegalArgumentException("Bác sĩ đã có lịch làm việc cho " + req.getDayOfWeek());
        }

        WorkSchedule ws = WorkSchedule.builder()
                .dentist(dentist)
                .dayOfWeek(req.getDayOfWeek())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .active(req.isActive())
                .build();

        return workScheduleMapper.toResponse(workScheduleRepository.save(ws));
    }

    @Transactional
    public WorkScheduleResponse update(Long id, UpdateWorkScheduleRequest req) {
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new IllegalArgumentException("startTime phải trước endTime");
        }

        WorkSchedule ws = workScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch làm việc id=" + id));

        ws.setStartTime(req.getStartTime());
        ws.setEndTime(req.getEndTime());
        ws.setActive(req.isActive());

        return workScheduleMapper.toResponse(workScheduleRepository.save(ws));
    }

    @Transactional
    public void delete(Long id) {
        if (!workScheduleRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy lịch làm việc id=" + id);
        }
        workScheduleRepository.deleteById(id);
    }
}
