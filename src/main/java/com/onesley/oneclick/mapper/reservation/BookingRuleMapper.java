package com.onesley.oneclick.mapper.reservation;

import com.onesley.oneclick.dto.reservation.BookingRuleDto;
import com.onesley.oneclick.entity.reservation.BookingRule;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code BookingRule ↔ BookingRuleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface BookingRuleMapper {

    BookingRuleDto toDto(BookingRule entity);

    List<BookingRuleDto> toDtoList(List<BookingRule> entities);
}
