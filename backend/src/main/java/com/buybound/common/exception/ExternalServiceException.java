package com.buybound.common.exception;

/** Raised when a live upstream external API (Channel3, LLM AI, or PayPal Sandbox) fails. */
public class ExternalServiceException extends RuntimeException {
  public ExternalServiceException(String message) {
    super(message);
  }

  public ExternalServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}

