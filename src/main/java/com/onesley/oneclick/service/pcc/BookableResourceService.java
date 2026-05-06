package com.onesley.oneclick.service.pcc;

import com.onesley.oneclick.dto.pcc.BookableResourceDto;
import com.onesley.oneclick.mapper.pcc.BookableResourceMapper;
import com.onesley.oneclick.repository.pcc.BookableResourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link BookableResource} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class BookableResourceService {

    private final BookableResourceRepository repository;
    private final BookableResourceMapper mapper;

    public BookableResourceService(BookableResourceRepository repository, BookableResourceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<BookableResourceDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<BookableResourceDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
