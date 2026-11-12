package com.buybound.common.exception;

/**
 * Thrown when shopper login or PayPal Sandbox credential verification fails.
 */
public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String message) {
    super(message);
  }
}

