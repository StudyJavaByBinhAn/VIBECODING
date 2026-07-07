package com.vibecode.antijob.mapper;

import com.vibecode.antijob.dto.DentalServiceResponse;
import com.vibecode.antijob.entity.DentalService;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DentalServiceMapper {

    DentalServiceResponse toResponse(DentalService dentalService);
}
