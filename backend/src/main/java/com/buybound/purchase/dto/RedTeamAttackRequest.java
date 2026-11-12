package com.buybound.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RedTeamAttackRequest(
    UUID watchId,
    @NotBlank String attackType) {}

