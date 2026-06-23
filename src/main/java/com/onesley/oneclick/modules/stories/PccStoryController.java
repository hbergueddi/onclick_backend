package com.onesley.oneclick.modules.stories;

import com.onesley.oneclick.modules.stories.api.PccStoryDtos.CreateStoryDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.StoryDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.StoryViewCountDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.UpdateStoryDto;
import com.onesley.oneclick.modules.stories.internal.PccStoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller {@code /api/pcc/stories} — « Stories » (PCC), contenu éphémère type Instagram
 * (un staff/owner publie, les membres du tenant visionnent).
 *
 * <p>RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:STORIES')")} uniquement
 * (jamais isAuthenticated/hasRole). Le scoping fin (membre → vivantes de son tenant ; staff → toutes
 * + écriture) est porté par {@link PccStoryService} (ABAC). Ressource {@code STORIES} seedée par V72.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET    /}      — VIEW:STORIES   — mes stories tenant (membre → vivantes ; staff → toutes).</li>
 *   <li>{@code POST   /}      — CREATE:STORIES — publier une story (staff du tenant).</li>
 *   <li>{@code PATCH  /{id}}  — UPDATE:STORIES — éditer une story (staff du tenant).</li>
 *   <li>{@code DELETE /{id}}  — DELETE:STORIES — supprimer une story (staff du tenant ; soft-delete).</li>
 * </ul>
 *
 * <p>NB : CLIENT a VIEW (une story est du contenu destiné au membre) mais PAS CREATE/UPDATE/DELETE
 * → ne peut pas gérer. STAFF (opérationnel) a VIEW mais PAS d'écriture non plus (calqué annonces).</p>
 */
@RestController
@RequestMapping("/api/pcc/stories")
@Tag(name = "PccStory", description = "Stories (PCC) — contenu éphémère (staff publie, membres visionnent)")
@RequiredArgsConstructor
public class PccStoryController {

    private final PccStoryService service;

    @GetMapping
    @Operation(summary = "Mes stories tenant (membre → vivantes/visibles ; staff/admin → toutes)")
    @PreAuthorize("hasAuthority('VIEW:STORIES')")
    public List<StoryDto> listForMe() {
        return service.listForMe();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publier une story (staff du tenant ; média + légende + durée + ordre)")
    @PreAuthorize("hasAuthority('CREATE:STORIES')")
    public StoryDto create(@Valid @RequestBody CreateStoryDto body) {
        return service.create(body);
    }

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publier une story pour un tenant CIBLE (super-admin cross-tenant, Lot 4b)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public StoryDto adminCreate(@RequestParam UUID tenantId, @Valid @RequestBody CreateStoryDto body) {
        return service.adminCreate(tenantId, body);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Éditer une story (staff du tenant ; remplacement complet)")
    @PreAuthorize("hasAuthority('UPDATE:STORIES')")
    public StoryDto update(@PathVariable UUID id, @Valid @RequestBody UpdateStoryDto body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une story (staff du tenant ; soft-delete)")
    @PreAuthorize("hasAuthority('DELETE:STORIES')")
    public void delete(@PathVariable UUID id) {
        service.softDelete(id);
    }

    // ─── Gap #7 — tracking de vues (Instagram-style) ─────────────────────────────

    @PostMapping("/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marque une story « vue » par le membre (idempotent ; ring unread→read)")
    @PreAuthorize("hasAuthority('VIEW:STORIES')")  // le membre visionne ; ABAC service (tenant de la story)
    public void markViewed(@PathVariable UUID id) {
        service.markViewed(id);
    }

    @GetMapping("/view-counts")
    @Operation(summary = "Nombre de vues par story du tenant (stats staff/admin « vue par X membres »)")
    @PreAuthorize("hasAuthority('VIEW:STORIES')")  // ABAC service : staff/admin du tenant only (membre → 403)
    public List<StoryViewCountDto> viewCounts() {
        return service.viewCounts();
    }
}
