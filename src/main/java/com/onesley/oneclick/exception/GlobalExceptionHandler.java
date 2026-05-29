package com.onesley.oneclick.exception;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestionnaire global d'exceptions — convertit toute exception en réponse
 * HTTP {@code application/problem+json} (RFC 7807).
 *
 * <p>Format de réponse :
 * <pre>
 * {
 *   "type":     "https://api.oneclick.ma/errors/<slug>",
 *   "title":    "Bad Request",
 *   "status":   400,
 *   "detail":   "phone format invalid",
 *   "instance": "/api/profiles/3a59...",
 *   "timestamp": "2026-05-06T15:00:00.000Z",
 *   "traceId":  "uuid",
 *   ... champs spécifiques selon l'exception
 * }
 * </pre>
 *
 * <p>Le {@code traceId} est un UUID généré par requête, journalisé côté serveur
 * et renvoyé au client — utile pour corréler un bug remonté avec les logs.
 *
 * <p>Cas couverts :
 * <ul>
 *   <li>{@link ApiException} et sous-classes → status défini par l'exception</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 + liste des champs en erreur</li>
 *   <li>{@link MissingServletRequestParameterException} → 400</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 (UUID/enum mal formé)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 (JSON malformé)</li>
 *   <li>{@link AuthenticationException} → 401</li>
 *   <li>{@link AccessDeniedException} → 403</li>
 *   <li>{@link NoResourceFoundException} → 404 (route inexistante)</li>
 *   <li>{@link DataIntegrityViolationException} → 409 (UNIQUE / NOT NULL violations)</li>
 *   <li>{@link Exception} (fallback) → 500 (Sentry-able)</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://api.oneclick.ma/errors/";

    // ── Métier (ApiException et sous-classes) ─────────────────────────────

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest req) {
        ProblemDetail body = problem(ex.getStatus(), ex.getMessage(), req, slug(ex));
        // Pas de log noisy pour des 4xx prévisibles (NotFoundException etc.)
        if (ex.getStatus().is5xxServerError()) {
            String traceId = String.valueOf(body.getProperties().get("traceId"));
            log.error("API exception 5xx [{}]: {}", traceId, ex.getMessage(), ex);
            // Sprint G.6.2 — Sentry capture explicite avec contexte enrichi
            captureToSentry(ex, req, traceId, SentryLevel.ERROR);
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    // ── Validation Bean ───────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest req
    ) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                fe -> fe.getField(),
                fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                (a, b) -> a, // first wins on duplicates
                LinkedHashMap::new
            ));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", req, "validation-failed");
        body.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Validation des paramètres de méthode contrôleur (Spring 6.1+/Boot 4 :
     * {@code @Min}/{@code @Max}/{@code @Pattern} sur {@code @RequestParam}/{@code @PathVariable},
     * sans {@code @Validated}). Sans ce handler, la violation tombait dans le catch-all
     * {@code Exception} → 500 au lieu de 400 (ex: {@code limit} hors borne).
     */
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
        org.springframework.web.method.annotation.HandlerMethodValidationException ex, HttpServletRequest req
    ) {
        return ResponseEntity.badRequest().body(
            problem(HttpStatus.BAD_REQUEST, "Validation failed", req, "validation-failed")
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(
        MissingServletRequestParameterException ex, HttpServletRequest req
    ) {
        return ResponseEntity.badRequest().body(
            problem(HttpStatus.BAD_REQUEST,
                "Required query parameter missing: " + ex.getParameterName(),
                req, "missing-parameter")
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex, HttpServletRequest req
    ) {
        String expected = ex.getRequiredType() == null ? "?" : ex.getRequiredType().getSimpleName();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST,
            String.format("Parameter '%s' has invalid value '%s' (expected %s)",
                ex.getName(), ex.getValue(), expected),
            req, "type-mismatch");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(
        HttpMessageNotReadableException ex, HttpServletRequest req
    ) {
        return ResponseEntity.badRequest().body(
            problem(HttpStatus.BAD_REQUEST, "Malformed JSON request", req, "malformed-json")
        );
    }

    // ── Sécurité ──────────────────────────────────────────────────────────

    @ExceptionHandler({AuthenticationException.class, AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            problem(HttpStatus.UNAUTHORIZED, "Authentication required", req, "unauthorized")
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            problem(HttpStatus.FORBIDDEN, "Access denied", req, "forbidden")
        );
    }

    // ── Infra ─────────────────────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            problem(HttpStatus.NOT_FOUND, "Route not found: " + ex.getResourcePath(), req, "route-not-found")
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(
        DataIntegrityViolationException ex, HttpServletRequest req
    ) {
        // Heuristique : UNIQUE / FK violations → 409, NOT NULL → 400
        String msg = ex.getMostSpecificCause().getMessage();
        boolean isUnique = msg != null && msg.toLowerCase().contains("unique");
        boolean isFk = msg != null && msg.toLowerCase().contains("foreign key");
        HttpStatus status = (isUnique || isFk) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        ProblemDetail body = problem(status, "Data integrity violation", req, "data-integrity");
        body.setProperty("cause", msg);
        log.warn("DataIntegrityViolation [{}]: {}", body.getProperties().get("traceId"), msg);
        return ResponseEntity.status(status).body(body);
    }

    // ── Fallback ──────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAny(Exception ex, HttpServletRequest req) {
        ProblemDetail body = problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            req,
            "internal-error"
        );
        String traceId = String.valueOf(body.getProperties().get("traceId"));
        // 500 = bug serveur, on log + Sentry explicite avec contexte enrichi
        log.error("Unhandled exception [{}] on {}: {}",
            traceId, req.getRequestURI(), ex.getMessage(), ex);
        // Sprint G.6.2 — Sentry capture explicite (le starter auto-capture aussi
        // via logback appender, mais on veut être sûr d'avoir le contexte enrichi).
        captureToSentry(ex, req, traceId, SentryLevel.FATAL);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest req, String type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(ERROR_TYPE_BASE + type));
        pd.setTitle(status.getReasonPhrase());
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("traceId", UUID.randomUUID().toString());
        return pd;
    }

    private static String slug(ApiException ex) {
        return ex.getClass().getSimpleName()
            .replaceAll("Exception$", "")
            .replaceAll("([a-z])([A-Z])", "$1-$2")
            .toLowerCase();
    }

    /**
     * Sprint G.6.2 — Capture explicite vers Sentry avec contexte enrichi.
     *
     * <p>Le starter Sentry Spring Boot 4 capture automatiquement via logback
     * appender (cf {@code SentryLogbackAppender}), mais cette méthode ajoute :
     * <ul>
     *   <li>{@code traceId} comme tag (pour corréler avec les logs)</li>
     *   <li>{@code http.method}, {@code http.path}, {@code http.query} comme contexte</li>
     *   <li>{@code X-Forwarded-For} comme IP côté Sentry user</li>
     *   <li>{@link SentryLevel} explicite (ERROR pour 5xx ApiException, FATAL pour 500 fallback)</li>
     * </ul>
     *
     * <p>Si SENTRY_DSN n'est pas configuré, {@code Sentry.captureException} est
     * un no-op silencieux (pas d'erreur côté app).
     */
    private static void captureToSentry(Throwable ex, HttpServletRequest req, String traceId, SentryLevel level) {
        Sentry.withScope(scope -> {
            scope.setLevel(level);
            scope.setTag("traceId", traceId);
            scope.setTag("http.method", req.getMethod());
            scope.setTag("http.path", req.getRequestURI());
            String query = req.getQueryString();
            if (query != null && !query.isBlank()) {
                scope.setExtra("http.query", query);
            }
            String userAgent = req.getHeader("User-Agent");
            if (userAgent != null) scope.setExtra("http.userAgent", userAgent);
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null) scope.setTag("http.x-forwarded-for", xff.split(",")[0].trim());
            Sentry.captureException(ex);
        });
    }
}
