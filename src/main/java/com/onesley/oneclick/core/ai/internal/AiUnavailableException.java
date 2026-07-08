package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Provider LLM injoignable (serveur down, connexion refusée) → {@code 503 Service Unavailable}.
 * Mappée automatiquement par {@code GlobalExceptionHandler} (slug {@code ai-unavailable}).
 */
public class AiUnavailableException extends ApiException {
    public AiUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
