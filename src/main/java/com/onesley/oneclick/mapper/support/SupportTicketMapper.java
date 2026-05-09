package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.SupportTicketDto;
import com.onesley.oneclick.entity.support.SupportTicket;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code SupportTicket ↔ SupportTicketDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface SupportTicketMapper {

    @Mapping(target = "status", source = "status.code")
    SupportTicketDto toDto(SupportTicket entity);

    List<SupportTicketDto> toDtoList(List<SupportTicket> entities);
}
