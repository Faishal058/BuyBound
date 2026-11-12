package com.buybound.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record Channel3LookupRequest(@NotBlank String url) {}

