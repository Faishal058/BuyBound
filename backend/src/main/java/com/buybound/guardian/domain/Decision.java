package com.buybound.guardian.domain;

/** Guardian verdict. Ordered by severity: BLOCK > HOLD > ALLOW. */
public enum Decision {
  ALLOW,
  HOLD,
  BLOCK
}
