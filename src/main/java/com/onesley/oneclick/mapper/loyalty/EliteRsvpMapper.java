package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteRsvpDto;
import com.onesley.oneclick.entity.loyalty.EliteRsvp;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code EliteRsvp ↔ EliteRsvpDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface EliteRsvpMapper {

    EliteRsvpDto toDto(EliteRsvp entity);

    List<EliteRsvpDto> toDtoList(List<EliteRsvp> entities);
}
