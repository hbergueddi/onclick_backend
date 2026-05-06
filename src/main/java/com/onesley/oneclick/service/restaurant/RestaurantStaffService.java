package com.onesley.oneclick.service.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantStaffDto;
import com.onesley.oneclick.mapper.restaurant.RestaurantStaffMapper;
import com.onesley.oneclick.repository.restaurant.RestaurantStaffRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RestaurantStaff} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantStaffService {

    private final RestaurantStaffRepository repository;
    private final RestaurantStaffMapper mapper;

    public RestaurantStaffService(RestaurantStaffRepository repository, RestaurantStaffMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RestaurantStaffDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RestaurantStaffDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
