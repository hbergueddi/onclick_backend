package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.ChatMessageDto;
import com.onesley.oneclick.entity.support.ChatMessage;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ChatMessage ↔ ChatMessageDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ChatMessageMapper {

    ChatMessageDto toDto(ChatMessage entity);

    List<ChatMessageDto> toDtoList(List<ChatMessage> entities);
}
