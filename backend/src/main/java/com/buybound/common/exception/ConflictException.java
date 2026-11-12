package com.buybound.common.exception;

/** Thrown when the request is valid but conflicts with current state. Maps to 409. */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
