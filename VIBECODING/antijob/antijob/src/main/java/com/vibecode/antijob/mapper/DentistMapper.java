package com.vibecode.antijob.mapper;

import com.vibecode.antijob.dto.DentistResponse;
import com.vibecode.antijob.entity.Dentist;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DentistMapper {

    DentistResponse toResponse(Dentist dentist);
}
