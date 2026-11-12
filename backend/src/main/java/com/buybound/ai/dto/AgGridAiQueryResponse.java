package com.buybound.ai.dto;

import java.util.List;
import java.util.Map;

public record AgGridAiQueryResponse(
    Map<String, Object> filterModel,
    List<SortModelItem> sortModel,
    List<String> groupColumns,
    String insightSummary) {

  public record SortModelItem(String colId, String sort) {}
}

