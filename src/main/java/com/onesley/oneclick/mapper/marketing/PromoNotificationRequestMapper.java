package com.onesley.oneclick.mapper.marketing;

import com.onesley.oneclick.dto.marketing.PromoNotificationRequestDto;
import com.onesley.oneclick.entity.marketing.PromoNotificationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code PromoNotificationRequest ↔ PromoNotificationRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface PromoNotificationRequestMapper {

    @Mapping(target = "status", source = "status.code")
    PromoNotificationRequestDto toDto(PromoNotificationRequest entity);

    List<PromoNotificationRequestDto> toDtoList(List<PromoNotificationRequest> entities);
}
