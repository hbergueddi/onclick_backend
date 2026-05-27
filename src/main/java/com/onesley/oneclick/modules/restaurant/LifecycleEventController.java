package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.modules.restaurant.api.LifecycleEventCreateDto;
import com.onesley.oneclick.modules.restaurant.api.LifecycleEventDto;
import com.onesley.oneclick.modules.restaurant.internal.LifecycleEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/lifecycle-events} — journal append-only du cycle
 * de vie des restaurants (vue admin GALAXY).
 *
 * <p>RBAC v2 senior strict : {@code hasAuthority('VERB:LIFECYCLE')} uniquement
 * (migration V45, SUPERADMIN). Append-only → VIEW + CREATE seulement.
 */
@RestController
@RequestMapping("/api/lifecycle-events")
@Tag(name = "Lifecycle Events", description = "Journal cycle de vie restaurants (admin)")
@RequiredArgsConstructor
public class LifecycleEventController {

    private final LifecycleEventService service;

    @GetMapping
    @Operation(summary = "Liste les événements de cycle de vie (récents d'abord)")
    @PreAuthorize("hasAuthority('VIEW:LIFECYCLE')")
    public List<LifecycleEventDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Journalise un événement de cycle de vie")
    @PreAuthorize("hasAuthority('CREATE:LIFECYCLE')")
    public LifecycleEventDto create(@Valid @RequestBody LifecycleEventCreateDto dto) {
        return service.create(dto);
    }
}
