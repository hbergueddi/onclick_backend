package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.ClientLoyaltySummaryViewDto;
import com.onesley.oneclick.entity.loyalty.ClientLoyaltySummaryView;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ClientLoyaltySummaryView ↔ ClientLoyaltySummaryViewDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ClientLoyaltySummaryViewMapper {

    ClientLoyaltySummaryViewDto toDto(ClientLoyaltySummaryView entity);

    List<ClientLoyaltySummaryViewDto> toDtoList(List<ClientLoyaltySummaryView> entities);
}
