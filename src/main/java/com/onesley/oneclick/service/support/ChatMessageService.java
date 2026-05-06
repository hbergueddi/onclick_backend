package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.ChatMessageDto;
import com.onesley.oneclick.mapper.support.ChatMessageMapper;
import com.onesley.oneclick.repository.support.ChatMessageRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ChatMessage} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMessageRepository repository;
    private final ChatMessageMapper mapper;

    public ChatMessageService(ChatMessageRepository repository, ChatMessageMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ChatMessageDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ChatMessageDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
