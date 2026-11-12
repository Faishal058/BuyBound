package com.buybound.ai;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.buybound.ai.service.AiShoppingAgentService;
import com.buybound.common.exception.ExternalServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiShoppingAgentServiceTest {

  @Test
  void failsClosedWhenApiKeyMissing() {
    AiShoppingAgentService service = new AiShoppingAgentService(
        new ObjectMapper(), "groq", "", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1");
    assertThrows(ExternalServiceException.class,
        () -> service.parseWatch("Buy Sony WH-1000XM5 under $250"));
    assertThrows(ExternalServiceException.class,
        () -> service.translateGridQuery("Show blocked attacks", "offers"));
  }
}

