package com.vibecode.antijob.mapper;

import com.vibecode.antijob.dto.PatientResponse;
import com.vibecode.antijob.entity.Patient;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    PatientResponse toResponse(Patient patient);
}
