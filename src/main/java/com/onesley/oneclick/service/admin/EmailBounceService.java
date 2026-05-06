package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.EmailBounceDto;
import com.onesley.oneclick.mapper.admin.EmailBounceMapper;
import com.onesley.oneclick.repository.admin.EmailBounceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link EmailBounce} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class EmailBounceService {

    private final EmailBounceRepository repository;
    private final EmailBounceMapper mapper;

    public EmailBounceService(EmailBounceRepository repository, EmailBounceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<EmailBounceDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<EmailBounceDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
