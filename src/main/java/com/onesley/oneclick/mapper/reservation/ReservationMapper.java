package com.onesley.oneclick.mapper.reservation;

import com.onesley.oneclick.dto.reservation.ReservationDto;
import com.onesley.oneclick.entity.reservation.Reservation;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface ReservationMapper {

    ReservationDto toDto(Reservation entity);

    List<ReservationDto> toDtoList(List<Reservation> entities);
}
