package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.OnboardingRequestDto;
import com.onesley.oneclick.entity.contract.OnboardingRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code OnboardingRequest ↔ OnboardingRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface OnboardingRequestMapper {

    @Mapping(target = "status", source = "status.code")
    OnboardingRequestDto toDto(OnboardingRequest entity);

    List<OnboardingRequestDto> toDtoList(List<OnboardingRequest> entities);
}
