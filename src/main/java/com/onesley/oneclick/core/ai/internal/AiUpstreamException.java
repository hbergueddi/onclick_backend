package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Réponse invalide du provider LLM (erreur HTTP upstream, modèle absent, réponse vide)
 * → {@code 502 Bad Gateway}. Mappée automatiquement par {@code GlobalExceptionHandler}
 * (slug {@code ai-upstream}).
 */
public class AiUpstreamException extends ApiException {
    public AiUpstreamException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public AiUpstreamException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
