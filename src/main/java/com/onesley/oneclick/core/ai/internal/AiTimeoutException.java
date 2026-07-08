package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Appel au provider LLM expiré (timeout) → {@code 504 Gateway Timeout}.
 * Mappée automatiquement par {@code GlobalExceptionHandler} (slug {@code ai-timeout}).
 */
public class AiTimeoutException extends ApiException {
    public AiTimeoutException(String message, Throwable cause) {
        super(HttpStatus.GATEWAY_TIMEOUT, message, cause);
    }
}
