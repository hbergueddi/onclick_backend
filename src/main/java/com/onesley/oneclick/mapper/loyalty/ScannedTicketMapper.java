package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.ScannedTicketDto;
import com.onesley.oneclick.entity.loyalty.ScannedTicket;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code ScannedTicket ↔ ScannedTicketDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ScannedTicketMapper {

    @Mapping(target = "status", source = "status.code")
    ScannedTicketDto toDto(ScannedTicket entity);

    List<ScannedTicketDto> toDtoList(List<ScannedTicket> entities);
}
