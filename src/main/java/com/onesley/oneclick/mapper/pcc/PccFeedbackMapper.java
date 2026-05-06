package com.onesley.oneclick.mapper.pcc;

import com.onesley.oneclick.dto.pcc.PccFeedbackDto;
import com.onesley.oneclick.entity.pcc.PccFeedback;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code PccFeedback ↔ PccFeedbackDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface PccFeedbackMapper {

    PccFeedbackDto toDto(PccFeedback entity);

    List<PccFeedbackDto> toDtoList(List<PccFeedback> entities);
}
