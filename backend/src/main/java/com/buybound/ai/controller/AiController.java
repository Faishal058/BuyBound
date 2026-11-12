package com.buybound.ai.controller;

import com.buybound.ai.dto.AgGridAiQueryRequest;
import com.buybound.ai.dto.AgGridAiQueryResponse;
import com.buybound.ai.dto.NaturalLanguageWatchRequest;
import com.buybound.ai.dto.ParsedWatchBlueprintResponse;
import com.buybound.ai.service.AiShoppingAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final AiShoppingAgentService aiService;

  public AiController(AiShoppingAgentService aiService) {
    this.aiService = aiService;
  }

  @PostMapping("/parse-watch")
  public ParsedWatchBlueprintResponse parseWatch(@Valid @RequestBody NaturalLanguageWatchRequest request) {
    return aiService.parseWatch(request.prompt());
  }

  @PostMapping("/grid-query")
  public AgGridAiQueryResponse gridQuery(@Valid @RequestBody AgGridAiQueryRequest request) {
    return aiService.translateGridQuery(request.query(), request.view());
  }
}

