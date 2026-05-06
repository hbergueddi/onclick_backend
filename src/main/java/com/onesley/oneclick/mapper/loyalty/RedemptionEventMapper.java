package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.RedemptionEventDto;
import com.onesley.oneclick.entity.loyalty.RedemptionEvent;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RedemptionEvent ↔ RedemptionEventDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RedemptionEventMapper {

    RedemptionEventDto toDto(RedemptionEvent entity);

    List<RedemptionEventDto> toDtoList(List<RedemptionEvent> entities);
}
