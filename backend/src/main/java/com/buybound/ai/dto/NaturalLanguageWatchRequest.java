package com.buybound.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record NaturalLanguageWatchRequest(@NotBlank String prompt) {}

