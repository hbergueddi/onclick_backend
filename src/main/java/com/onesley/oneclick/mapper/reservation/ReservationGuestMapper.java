package com.onesley.oneclick.mapper.reservation;

import com.onesley.oneclick.dto.reservation.ReservationGuestDto;
import com.onesley.oneclick.entity.reservation.ReservationGuest;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ReservationGuest ↔ ReservationGuestDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ReservationGuestMapper {

    ReservationGuestDto toDto(ReservationGuest entity);

    List<ReservationGuestDto> toDtoList(List<ReservationGuest> entities);
}
