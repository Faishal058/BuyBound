package com.buybound.common.exception;

import java.time.Instant;

/** Consistent API error body returned for every failure. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
  public static ApiError of(int status, String error, String message, String path) {
    return new ApiError(Instant.now(), status, error, message, path);
  }
}
