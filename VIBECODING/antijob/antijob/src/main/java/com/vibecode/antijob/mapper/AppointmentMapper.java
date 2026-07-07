package com.vibecode.antijob.mapper;

import com.vibecode.antijob.dto.AppointmentResponse;
import com.vibecode.antijob.entity.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "patientName", source = "patient.fullName")
    @Mapping(target = "dentistName", source = "dentist.fullName")
    @Mapping(target = "serviceName", source = "service.name")
    AppointmentResponse toResponse(Appointment appointment);
}
