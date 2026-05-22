package com.onesley.oneclick.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du {@link GlobalExceptionHandler} — on instancie le handler
 * directement et on lui passe des exceptions forgées pour couvrir chaque branche :
 * mapping de statut, heuristiques (DataIntegrity unique/FK/null), formatage des
 * champs de validation, et le chemin {@code captureToSentry} (5xx/500) avec ou
 * sans en-têtes optionnels (query / User-Agent / X-Forwarded-For).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest req(String query, String userAgent, String xff) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        lenient().when(r.getRequestURI()).thenReturn("/api/x");
        lenient().when(r.getMethod()).thenReturn("POST");
        lenient().when(r.getQueryString()).thenReturn(query);
        lenient().when(r.getHeader("User-Agent")).thenReturn(userAgent);
        lenient().when(r.getHeader("X-Forwarded-For")).thenReturn(xff);
        return r;
    }
    private HttpServletRequest req() { return req(null, null, null); }

    private static void assertProblemEnveloppe(ProblemDetail body, String slugFragment, int status) {
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(status);
        assertThat(body.getType().toString()).contains(slugFragment);
        assertThat(body.getInstance().toString()).isEqualTo("/api/x");
        assertThat(body.getProperties()).containsKey("traceId").containsKey("timestamp");
    }

    // ── ApiException : 4xx (silencieux) vs 5xx (log + Sentry) ─────────────────

    @Test
    void handleApi_4xx_mapsStatusAndCamelCaseSlug() {
        ResponseEntity<ProblemDetail> re = handler.handleApi(
            new NotFoundException("Restaurant", UUID.randomUUID()), req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertProblemEnveloppe(re.getBody(), "not-found", 404); // slug() : NotFoundException -> "not-found"
    }

    @Test
    void handleApi_5xx_logsAndCapturesSentry_withAllRequestExtras() {
        // status 5xx + query/UA/X-Forwarded-For présents → branches "true" de captureToSentry + xff.split
        ResponseEntity<ProblemDetail> re = handler.handleApi(
            new ApiException(HttpStatus.BAD_GATEWAY, "upstream down"),
            req("x=1&y=2", "JUnit-Agent", "9.9.9.9, 1.1.1.1"));
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertProblemEnveloppe(re.getBody(), "api", 502);
    }

    // ── Validation Bean : message null → "invalid" + dédup (premier gagne) ────

    @Test
    void handleValidation_nullDefaultMessageBecomesInvalid_andFirstWinsOnDuplicate() {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "dto");
        br.addError(new FieldError("dto", "email", "must not be blank"));
        br.addError(new FieldError("dto", "phone", null));               // null → "invalid"
        br.addError(new FieldError("dto", "email", "second email error")); // doublon → premier gagne
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(br);

        ResponseEntity<ProblemDetail> re = handler.handleValidation(ex, req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) re.getBody().getProperties().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("email", "must not be blank").containsEntry("phone", "invalid");
    }

    @Test
    void handleMissingParam_400_mentionsParameterName() {
        ResponseEntity<ProblemDetail> re = handler.handleMissingParam(
            new MissingServletRequestParameterException("page", "int"), req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(re.getBody().getDetail()).contains("page");
    }

    // ── Type mismatch : requiredType null → "?", sinon simpleName ─────────────

    @Test
    void handleTypeMismatch_nullRequiredType_usesQuestionMark() {
        MethodArgumentTypeMismatchException ex =
            new MethodArgumentTypeMismatchException("abc", null, "id", null, new RuntimeException());
        ResponseEntity<ProblemDetail> re = handler.handleTypeMismatch(ex, req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(re.getBody().getDetail()).contains("'id'").contains("expected ?");
    }

    @Test
    void handleTypeMismatch_knownRequiredType_usesSimpleName() {
        MethodArgumentTypeMismatchException ex =
            new MethodArgumentTypeMismatchException("zzz", UUID.class, "userId", null, new RuntimeException());
        ResponseEntity<ProblemDetail> re = handler.handleTypeMismatch(ex, req());
        assertThat(re.getBody().getDetail()).contains("expected UUID");
    }

    @Test
    void handleNotReadable_400() {
        ResponseEntity<ProblemDetail> re = handler.handleNotReadable(
            mock(HttpMessageNotReadableException.class), req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(re.getBody().getType().toString()).contains("malformed-json");
    }

    // ── Sécurité ──────────────────────────────────────────────────────────────

    @Test
    void handleAuth_401() {
        ResponseEntity<ProblemDetail> re = handler.handleAuth(new BadCredentialsException("bad"), req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(re.getBody().getType().toString()).contains("unauthorized");
    }

    @Test
    void handleAccessDenied_403() {
        ResponseEntity<ProblemDetail> re = handler.handleAccessDenied(new AccessDeniedException("nope"), req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(re.getBody().getType().toString()).contains("forbidden");
    }

    @Test
    void handleNoResource_404_includesPath() {
        // Spring 7 : constructeur non trivial → on mocke et on stub getResourcePath()
        NoResourceFoundException ex = mock(NoResourceFoundException.class);
        when(ex.getResourcePath()).thenReturn("/api/missing");
        ResponseEntity<ProblemDetail> re = handler.handleNoResource(ex, req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(re.getBody().getDetail()).contains("/api/missing");
    }

    // ── DataIntegrity : unique/FK → 409 ; sinon (ou message null) → 400 ───────

    @Test
    void handleDataIntegrity_unique_409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "wrap", new RuntimeException("duplicate key value violates UNIQUE constraint \"users_email_key\""));
        ResponseEntity<ProblemDetail> re = handler.handleDataIntegrity(ex, req());
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) re.getBody().getProperties().get("cause")).containsIgnoringCase("unique");
    }

    @Test
    void handleDataIntegrity_foreignKey_409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "wrap", new RuntimeException("violates FOREIGN KEY constraint \"fk_x\""));
        assertThat(handler.handleDataIntegrity(ex, req()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleDataIntegrity_otherCause_400() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "wrap", new RuntimeException("null value in column \"name\" violates not-null constraint"));
        assertThat(handler.handleDataIntegrity(ex, req()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleDataIntegrity_nullCauseMessage_400() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("wrap", new RuntimeException());
        assertThat(handler.handleDataIntegrity(ex, req()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Fallback 500 : captureToSentry sans en-têtes (branches "false") ───────

    @Test
    void handleAny_500_noRequestExtras() {
        // query/UA/xff tous null → branches "false"/court-circuit de captureToSentry
        ResponseEntity<ProblemDetail> re = handler.handleAny(new RuntimeException("boom"), req(null, null, null));
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertProblemEnveloppe(re.getBody(), "internal-error", 500);
    }

    @Test
    void handleAny_500_blankQuery_singleXff() {
        // query non-null mais blank → branche !isBlank "false" ; UA + xff (sans virgule) présents
        ResponseEntity<ProblemDetail> re = handler.handleAny(
            new IllegalStateException("kaput"), req("   ", "agent", "8.8.8.8"));
        assertThat(re.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
