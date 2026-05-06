package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.InvoiceLineDto;
import com.onesley.oneclick.entity.contract.InvoiceLine;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code InvoiceLine ↔ InvoiceLineDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface InvoiceLineMapper {

    InvoiceLineDto toDto(InvoiceLine entity);

    List<InvoiceLineDto> toDtoList(List<InvoiceLine> entities);
}
