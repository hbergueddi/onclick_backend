package com.onesley.oneclick.modules.announcement;

import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.AnnouncementDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.CreateAnnouncementDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.MarkReadDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.UpdateAnnouncementDto;
import com.onesley.oneclick.modules.announcement.internal.AnnouncementService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller {@code /api/announcements} — « Annonces tenant » (Lot 8), comm B2B staff
 * descendante (tenant-admin → owners/staff).
 *
 * <p>RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:ANNOUNCEMENTS')")} uniquement
 * (jamais isAuthenticated/hasRole). Le scoping fin (tenant du caller, admin-only en écriture,
 * staff-visible en lecture, self en mark-read) est porté par {@link AnnouncementService} (ABAC).
 * Ressource {@code ANNOUNCEMENTS} seedée par V70.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET    /}              — VIEW:ANNOUNCEMENTS   — mes annonces visibles (tenant-scope ;
 *       admin → tout, staff → publiées).</li>
 *   <li>{@code POST   /{id}/read}     — VIEW:ANNOUNCEMENTS   — acquitter une annonce (self).</li>
 *   <li>{@code POST   /}              — CREATE:ANNOUNCEMENTS — publier une annonce (tenant-admin).</li>
 *   <li>{@code PATCH  /{id}}          — UPDATE:ANNOUNCEMENTS — éditer (tenant-admin).</li>
 *   <li>{@code PATCH  /{id}/archive}  — UPDATE:ANNOUNCEMENTS — archiver (tenant-admin).</li>
 *   <li>{@code DELETE /{id}}          — DELETE:ANNOUNCEMENTS — soft-delete (tenant-admin).</li>
 * </ul>
 *
 * <p>NB : le mark-read est gardé par VIEW (lecture-acquittement, pas une mutation de l'annonce) +
 * ABAC self/tenant-scope service. STAFF a VIEW mais PAS CREATE/UPDATE/DELETE → ne peut pas gérer.</p>
 */
@RestController
@RequestMapping("/api/announcements")
@Tag(name = "Announcement", description = "Annonces tenant (Lot 8) — comm B2B staff descendante (Adil → owners)")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    @Operation(summary = "Mes annonces visibles (tenant-scope : admin → tout, staff → publiées)")
    @PreAuthorize("hasAuthority('VIEW:ANNOUNCEMENTS')")
    public List<AnnouncementDto> listForMe() {
        return service.listForMe();
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Acquitter une annonce (le caller, pour la version affichée)")
    @PreAuthorize("hasAuthority('VIEW:ANNOUNCEMENTS')")
    public AnnouncementDto markRead(@PathVariable UUID id, @Valid @RequestBody(required = false) MarkReadDto body) {
        return service.markRead(id, body != null ? body.bodyVersion() : null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publier une annonce (tenant-admin ; max 1 épinglée par priorité)")
    @PreAuthorize("hasAuthority('CREATE:ANNOUNCEMENTS')")
    public AnnouncementDto create(@Valid @RequestBody CreateAnnouncementDto body) {
        return service.create(body);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Éditer une annonce (tenant-admin ; édition du corps = ré-acquittement)")
    @PreAuthorize("hasAuthority('UPDATE:ANNOUNCEMENTS')")
    public AnnouncementDto update(@PathVariable UUID id, @Valid @RequestBody UpdateAnnouncementDto body) {
        return service.update(id, body);
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archiver une annonce (tenant-admin ; sort de la bannière)")
    @PreAuthorize("hasAuthority('UPDATE:ANNOUNCEMENTS')")
    public AnnouncementDto archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une annonce (tenant-admin ; soft-delete)")
    @PreAuthorize("hasAuthority('DELETE:ANNOUNCEMENTS')")
    public void delete(@PathVariable UUID id) {
        service.softDelete(id);
    }
}
