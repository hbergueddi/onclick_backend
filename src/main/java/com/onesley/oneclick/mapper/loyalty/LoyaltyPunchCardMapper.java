package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.LoyaltyPunchCardDto;
import com.onesley.oneclick.entity.loyalty.LoyaltyPunchCard;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code LoyaltyPunchCard ↔ LoyaltyPunchCardDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface LoyaltyPunchCardMapper {

    LoyaltyPunchCardDto toDto(LoyaltyPunchCard entity);

    List<LoyaltyPunchCardDto> toDtoList(List<LoyaltyPunchCard> entities);
}
