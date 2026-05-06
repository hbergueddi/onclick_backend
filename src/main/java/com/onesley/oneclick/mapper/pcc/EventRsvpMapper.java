package com.onesley.oneclick.mapper.pcc;

import com.onesley.oneclick.dto.pcc.EventRsvpDto;
import com.onesley.oneclick.entity.pcc.EventRsvp;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code EventRsvp ↔ EventRsvpDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface EventRsvpMapper {

    EventRsvpDto toDto(EventRsvp entity);

    List<EventRsvpDto> toDtoList(List<EventRsvp> entities);
}
