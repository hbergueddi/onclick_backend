package com.onesley.oneclick.mapper.pcc;

import com.onesley.oneclick.dto.pcc.ResourceBookingDto;
import com.onesley.oneclick.entity.pcc.ResourceBooking;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ResourceBooking ↔ ResourceBookingDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ResourceBookingMapper {

    ResourceBookingDto toDto(ResourceBooking entity);

    List<ResourceBookingDto> toDtoList(List<ResourceBooking> entities);
}
