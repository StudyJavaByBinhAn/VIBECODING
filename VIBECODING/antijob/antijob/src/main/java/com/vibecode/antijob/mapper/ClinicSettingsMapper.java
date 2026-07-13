package com.vibecode.antijob.mapper;

import com.vibecode.antijob.dto.ClinicSettingsResponse;
import com.vibecode.antijob.entity.ClinicSettings;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClinicSettingsMapper {

    ClinicSettingsResponse toResponse(ClinicSettings settings);
}
