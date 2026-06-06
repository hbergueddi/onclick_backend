package com.onesley.oneclick.modules.seminar;

import com.onesley.oneclick.modules.seminar.api.SeminarDtos.CreateSeminarRequestDto;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.SeminarRequestDto;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.UpdateSeminarStatusDto;
import com.onesley.oneclick.modules.seminar.internal.PccSeminarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST controller {@code /api/pcc/seminars} — « Séminaires » (PCC), demandes de devis B2B.
 *
 * <p>RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:SEMINARS')")} uniquement
 * (jamais isAuthenticated/hasRole). Le scoping fin (self pour le membre, tenant-wide pour le
 * commercial, isolation tenant sur le changement de statut) est porté par {@link PccSeminarService}
 * (ABAC). Ressource {@code SEMINARS} seedée par V73.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /}                — CREATE:SEMINARS — le membre soumet une demande.</li>
 *   <li>{@code GET /mine}            — VIEW:SEMINARS — mes demandes (self-scope, sans notes internes).</li>
 *   <li>{@code GET /inbox}           — VIEW:SEMINARS — inbox commercial tenant-wide (staff/admin ;
 *       un CLIENT y voit ses propres demandes — défense en profondeur côté service).</li>
 *   <li>{@code PATCH /{id}/status}   — UPDATE:SEMINARS — le commercial change le statut (+ notes).</li>
 * </ul>
 *
 * <h3>Mapping legacy</h3>
 * <ul>
 *   <li>RPC {@code create_seminar_request}  → {@code POST /api/pcc/seminars}</li>
 *   <li>RPC {@code update_seminar_status} / EF {@code send-pcc-seminar-status-update}
 *       → {@code PATCH /api/pcc/seminars/{id}/status}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pcc/seminars")
@Tag(name = "PccSeminar", description = "Séminaires (PCC) — demandes de devis B2B membre → commercial")
@RequiredArgsConstructor
public class SeminarController {

    private final PccSeminarService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Soumettre une demande de séminaire (entreprise, contact, participants, dates, besoins)")
    @PreAuthorize("hasAuthority('CREATE:SEMINARS')")
    public SeminarRequestDto create(@Valid @RequestBody CreateSeminarRequestDto body) {
        return service.create(body);
    }

    @GetMapping("/mine")
    @Operation(summary = "Mes demandes de séminaire (self-scope), du plus récent au plus ancien")
    @PreAuthorize("hasAuthority('VIEW:SEMINARS')")
    public List<SeminarRequestDto> mine() {
        return service.listMine();
    }

    @GetMapping("/inbox")
    @Operation(summary = "Inbox commercial : toutes les demandes du tenant (staff/admin)")
    @PreAuthorize("hasAuthority('VIEW:SEMINARS')")
    public List<SeminarRequestDto> inbox() {
        return service.listForStaff();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'une demande (+ notes internes ; commercial uniquement)")
    @PreAuthorize("hasAuthority('UPDATE:SEMINARS')")
    public SeminarRequestDto updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateSeminarStatusDto body) {
        return service.updateStatus(id, body);
    }
}
