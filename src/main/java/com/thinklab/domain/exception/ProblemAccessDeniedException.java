package com.thinklab.domain.exception;

/**
 * Domain Exception: a {@code REQUESTER} tried to use the problem service (ADR-032). Problems are the IT staff's root-cause work; a
 * requester has no part in it, so every route refuses them.
 *
 * <p>RFC 7807 mapping: HTTP 403 Forbidden.
 */
public class ProblemAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-PRB-00403";

    public ProblemAccessDeniedException(String operation) {
        super(ERROR_CODE, "A requester cannot " + operation + ": problems are handled by IT staff.");
    }
}
