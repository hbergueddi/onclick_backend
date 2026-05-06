package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantGainRuleDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantGainRuleMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantGainRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantGainRule} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantGainRuleService {

    private final RestaurantGainRuleRepository repository;
    private final RestaurantGainRuleMapper mapper;

    public RestaurantGainRuleService(RestaurantGainRuleRepository repository, RestaurantGainRuleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantGainRuleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantGainRuleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
