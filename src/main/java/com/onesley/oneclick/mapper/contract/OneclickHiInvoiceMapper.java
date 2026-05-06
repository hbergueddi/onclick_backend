package com.onesley.oneclick.mapper.contract;

import com.onesley.oneclick.dto.contract.OneclickHiInvoiceDto;
import com.onesley.oneclick.entity.contract.OneclickHiInvoice;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code OneclickHiInvoice ↔ OneclickHiInvoiceDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface OneclickHiInvoiceMapper {

    OneclickHiInvoiceDto toDto(OneclickHiInvoice entity);

    List<OneclickHiInvoiceDto> toDtoList(List<OneclickHiInvoice> entities);
}
