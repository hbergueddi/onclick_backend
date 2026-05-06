package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.LoyaltyPointDto;
import com.onesley.oneclick.entity.loyalty.LoyaltyPoint;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code LoyaltyPoint ↔ LoyaltyPointDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface LoyaltyPointMapper {

    LoyaltyPointDto toDto(LoyaltyPoint entity);

    List<LoyaltyPointDto> toDtoList(List<LoyaltyPoint> entities);
}
