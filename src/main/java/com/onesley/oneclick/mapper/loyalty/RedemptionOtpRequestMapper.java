package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.RedemptionOtpRequestDto;
import com.onesley.oneclick.entity.loyalty.RedemptionOtpRequest;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RedemptionOtpRequest ↔ RedemptionOtpRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RedemptionOtpRequestMapper {

    RedemptionOtpRequestDto toDto(RedemptionOtpRequest entity);

    List<RedemptionOtpRequestDto> toDtoList(List<RedemptionOtpRequest> entities);
}
