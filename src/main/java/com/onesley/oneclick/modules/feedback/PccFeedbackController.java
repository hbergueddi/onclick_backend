package com.onesley.oneclick.modules.feedback;

import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.CreateFeedbackDto;
import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.FeedbackDto;
import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.ReplyFeedbackDto;
import com.onesley.oneclick.modules.feedback.internal.PccFeedbackService;
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
 * REST controller {@code /api/pcc/feedbacks} — « Avis » (PCC Lot 7), thread membre ↔ owner (Adil).
 *
 * <p>RBAC v2 senior strict — {@code @PreAuthorize("hasAuthority('VERB:FEEDBACK')")} uniquement
 * (jamais isAuthenticated/hasRole). Le scoping fin (self pour le membre, owner-scope pour la
 * lecture inbox + le reply, member-only pour mark-read) est porté par {@link PccFeedbackService}
 * (ABAC). Ressource {@code FEEDBACK} seedée par V69.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /}              — CREATE:FEEDBACK — le membre envoie un avis.</li>
 *   <li>{@code GET /mine}          — VIEW:FEEDBACK — mes avis (self-scope).</li>
 *   <li>{@code GET /inbox}         — VIEW:FEEDBACK — inbox owner-scopée (staff/admin ; un CLIENT
 *       y voit ses propres avis — défense en profondeur côté service).</li>
 *   <li>{@code PATCH /{id}/reply}  — UPDATE:FEEDBACK — l'owner/admin répond (owner-scope service).</li>
 *   <li>{@code PATCH /{id}/read}   — UPDATE:FEEDBACK — le membre marque la réponse lue (self).</li>
 * </ul>
 *
 * <h3>Mapping Edge Functions legacy</h3>
 * <ul>
 *   <li>{@code send-pcc-feedback}        → {@code POST /api/pcc/feedbacks}</li>
 *   <li>{@code send-pcc-feedback-reply}  → {@code PATCH /api/pcc/feedbacks/{id}/reply}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pcc/feedbacks")
@Tag(name = "PccFeedback", description = "Avis (PCC Lot 7) — thread membre ↔ owner (Adil)")
@RequiredArgsConstructor
public class PccFeedbackController {

    private final PccFeedbackService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envoyer un avis (sentiment + catégorie + commentaire, resto ciblé optionnel)")
    @PreAuthorize("hasAuthority('CREATE:FEEDBACK')")
    public FeedbackDto create(@Valid @RequestBody CreateFeedbackDto body) {
        return service.create(body);
    }

    @GetMapping("/mine")
    @Operation(summary = "Mes avis (self-scope), du plus récent au plus ancien")
    @PreAuthorize("hasAuthority('VIEW:FEEDBACK')")
    public List<FeedbackDto> mine() {
        return service.listMine();
    }

    @GetMapping("/inbox")
    @Operation(summary = "Inbox owner-scopée : avis ciblant mes restos + avis généraux (staff/admin)")
    @PreAuthorize("hasAuthority('VIEW:FEEDBACK')")
    public List<FeedbackDto> inbox() {
        return service.listForOwner();
    }

    @PatchMapping("/{id}/reply")
    @Operation(summary = "Répondre à un avis (owner/admin ; owner-scope ; 409 si déjà répondu)")
    @PreAuthorize("hasAuthority('UPDATE:FEEDBACK')")
    public FeedbackDto reply(@PathVariable UUID id, @Valid @RequestBody ReplyFeedbackDto body) {
        return service.reply(id, body.replyText());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer la réponse comme lue (le membre auteur uniquement)")
    @PreAuthorize("hasAuthority('UPDATE:FEEDBACK')")
    public FeedbackDto markRead(@PathVariable UUID id) {
        return service.markReplyRead(id);
    }
}
