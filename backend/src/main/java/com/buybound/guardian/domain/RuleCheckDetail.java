package com.buybound.guardian.domain;

/** Step-by-step deterministic safety rule proof emitted by GuardianEvaluator. */
public record RuleCheckDetail(
    String ruleName,
    boolean passed,
    String expectedThreshold,
    String actualValue,
    String reasonCode) {}

