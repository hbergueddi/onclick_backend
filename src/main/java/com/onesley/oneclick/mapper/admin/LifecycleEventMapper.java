package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.LifecycleEventDto;
import com.onesley.oneclick.entity.admin.LifecycleEvent;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code LifecycleEvent ↔ LifecycleEventDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface LifecycleEventMapper {

    LifecycleEventDto toDto(LifecycleEvent entity);

    List<LifecycleEventDto> toDtoList(List<LifecycleEvent> entities);
}
