package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.FraudAlertDto;
import com.onesley.oneclick.entity.admin.FraudAlert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code FraudAlert ↔ FraudAlertDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface FraudAlertMapper {

    FraudAlertDto toDto(FraudAlert entity);

    List<FraudAlertDto> toDtoList(List<FraudAlert> entities);
}
