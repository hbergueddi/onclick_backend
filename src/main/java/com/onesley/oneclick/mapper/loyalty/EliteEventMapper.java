package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteEventDto;
import com.onesley.oneclick.entity.loyalty.EliteEvent;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code EliteEvent ↔ EliteEventDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface EliteEventMapper {

    EliteEventDto toDto(EliteEvent entity);

    List<EliteEventDto> toDtoList(List<EliteEvent> entities);
}
