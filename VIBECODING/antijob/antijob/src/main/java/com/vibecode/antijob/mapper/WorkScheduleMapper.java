package com.vibecode.antijob.mapper;

import com.vibecode.antijob.dto.WorkScheduleResponse;
import com.vibecode.antijob.entity.WorkSchedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkScheduleMapper {

    @Mapping(target = "dentistId", source = "dentist.id")
    @Mapping(target = "dentistName", source = "dentist.fullName")
    WorkScheduleResponse toResponse(WorkSchedule workSchedule);
}
