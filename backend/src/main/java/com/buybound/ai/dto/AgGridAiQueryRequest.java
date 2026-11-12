package com.buybound.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AgGridAiQueryRequest(@NotBlank String query, String view) {}

