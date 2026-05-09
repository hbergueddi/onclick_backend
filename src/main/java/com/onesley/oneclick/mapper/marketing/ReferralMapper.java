package com.onesley.oneclick.mapper.marketing;

import com.onesley.oneclick.dto.marketing.ReferralDto;
import com.onesley.oneclick.entity.marketing.Referral;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code Referral ↔ ReferralDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ReferralMapper {

    @Mapping(target = "status", source = "status.code")
    ReferralDto toDto(Referral entity);

    List<ReferralDto> toDtoList(List<Referral> entities);
}
