package com.onesley.oneclick.mapper.misc;

import com.onesley.oneclick.dto.misc.LoyaltyPlafondDto;
import com.onesley.oneclick.entity.misc.LoyaltyPlafond;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code LoyaltyPlafond ↔ LoyaltyPlafondDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface LoyaltyPlafondMapper {

    LoyaltyPlafondDto toDto(LoyaltyPlafond entity);

    List<LoyaltyPlafondDto> toDtoList(List<LoyaltyPlafond> entities);
}
