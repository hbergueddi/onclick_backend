package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.core.tenant.Tenant;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.shared.events.UserRegisteredEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.UUID;

/**
 * Service métier {@link User} — signup, lookup, update, soft delete.
 *
 * <p>Pattern : {@code @Transactional(readOnly=true)} par défaut, écritures explicites.
 * {@code passwordHash} jamais exposé en lecture (DTO le filtre).
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public UserService(UserRepository repository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public UserDto findById(UUID id) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        return UserDto.from(user);
    }

    @Cacheable(value = CacheConfig.CACHE_USERS_BY_EMAIL, key = "#email.toLowerCase()")
    public UserDto findByEmail(String email) {
        User user = repository.findByEmailIgnoreCase(email)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User by email: " + email));
        return UserDto.from(user);
    }

    public Page<UserDto> findAll(int page, int size) {
        return repository.findAll(PageRequest.of(page, size)).map(UserDto::from);
    }

    @Transactional
    public UserDto create(UserCreateDto dto) {
        if (repository.existsByEmailIgnoreCase(dto.email())) {
            throw new ConflictException("Email déjà utilisé : " + dto.email());
        }
        if (dto.phone() != null && repository.existsByPhone(dto.phone())) {
            throw new ConflictException("Téléphone déjà utilisé : " + dto.phone());
        }
        Role role = roleRepository.findById(dto.roleId())
            .orElseThrow(() -> new NotFoundException("Role", dto.roleId()));

        User user = new User(
            UUID.randomUUID(),
            role,
            dto.email().toLowerCase(),
            passwordEncoder.encode(dto.password()),
            dto.firstName(),
            dto.lastName()
        );
        if (dto.phone() != null) user.setPhone(dto.phone());
        if (dto.language() != null) user.setLanguage(dto.language());
        if (dto.tenantId() != null) {
            user.setTenant(entityManager.getReference(Tenant.class, dto.tenantId()));
        }
        User saved = repository.save(user);

        // Publish event Spring Modulith → Kafka topic 'user.registered'
        eventPublisher.publishEvent(new UserRegisteredEvent(
            saved.getId(), dto.tenantId(), dto.email().toLowerCase(),
            dto.firstName(), dto.lastName(),
            role.getCode(),
            dto.language() != null ? dto.language() : "fr",
            Instant.now()
        ));

        return UserDto.from(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_USERS_BY_EMAIL, allEntries = true)
    public UserDto patch(UUID id, UserUpdateDto dto) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        if (dto.firstName() != null) user.setFirstName(dto.firstName());
        if (dto.lastName() != null) user.setLastName(dto.lastName());
        if (dto.phone() != null) {
            // Check uniqueness only if phone changes
            if (!dto.phone().equals(user.getPhone()) && repository.existsByPhone(dto.phone())) {
                throw new ConflictException("Téléphone déjà utilisé : " + dto.phone());
            }
            user.setPhone(dto.phone());
        }
        if (dto.avatarUrl() != null) user.setAvatarUrl(dto.avatarUrl());
        if (dto.language() != null) user.setLanguage(dto.language());
        return UserDto.from(repository.save(user));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_USERS_BY_EMAIL, allEntries = true)
    public void softDelete(UUID id) {
        User user = repository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("User", id));
        user.markDeleted();
        repository.save(user);
    }
}
