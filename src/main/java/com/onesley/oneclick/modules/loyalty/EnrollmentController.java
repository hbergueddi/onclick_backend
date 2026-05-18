package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.EnrollMemberDto;
import com.onesley.oneclick.modules.loyalty.api.EnrollMemberResultDto;
import com.onesley.oneclick.modules.loyalty.api.EnrollmentRecordDto;
import com.onesley.oneclick.modules.loyalty.internal.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints "Inscrire membre" — port commit legacy e7a8b49b (Sprint 9 HOMU).
 *
 * <p>Sous {@code /api/loyalty} (préfixe loyalty plutôt que /api/restaurants/...
 * ou /api/users/... car le concept "enrollment" est owned par le module loyalty —
 * on crédite des welcome_points, c'est la finalité métier).
 *
 * <p>RBAC validé dans le service ({@code EnrollmentService.enrollMember}) plutôt
 * que ici, car la règle dépend du restaurantId du payload : SUPERADMIN ou staff
 * actif du restaurant ciblé.
 */
@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Enrollment", description = "Inscription d'un nouveau membre avec welcome points")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/enroll-member")
    @Operation(
        summary = "Inscrit un nouveau membre dans le programme fidélité d'un restaurant",
        description = "Trouve ou crée le user, crédite welcome_points (plafonné par gain_rules.welcome_points_max). "
                    + "RBAC : SUPERADMIN ou staff actif du restaurant ciblé."
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EnrollMemberResultDto> enrollMember(@Valid @RequestBody EnrollMemberDto dto) {
        return ResponseEntity.ok(enrollmentService.enrollMember(dto));
    }

    @GetMapping("/enrollments/by-restaurant/{restaurantId}")
    @Operation(
        summary = "N dernières inscriptions welcome d'un restaurant (panneau UI sous le wizard)",
        description = "Filtre loyalty_transactions reason='welcome' joint avec users. "
                    + "RBAC : SUPERADMIN ou staff actif du restaurant."
    )
    @PreAuthorize("isAuthenticated()")
    public List<EnrollmentRecordDto> listRecentEnrollments(
        @PathVariable UUID restaurantId,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return enrollmentService.listRecentEnrollments(restaurantId, limit);
    }
}
