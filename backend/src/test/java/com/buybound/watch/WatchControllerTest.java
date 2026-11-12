package com.buybound.watch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buybound.common.exception.GlobalExceptionHandler;
import com.buybound.common.exception.ResourceNotFoundException;
import com.buybound.watch.controller.WatchController;
import com.buybound.watch.domain.WatchStatus;
import com.buybound.watch.dto.WatchResponse;
import com.buybound.watch.service.WatchService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WatchController.class)
@Import(GlobalExceptionHandler.class)
class WatchControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private WatchService service;

  private WatchResponse sampleWatch() {
    return new WatchResponse(UUID.randomUUID(), "Sony WH-1000XM5", null, "SKU-1", "black",
        "merchant-a", UUID.randomUUID(), 1, "USD", new BigDecimal("300.00"),
        WatchStatus.ACTIVE, Instant.now(), Instant.now());
  }

  @Test
  void createWatchReturns201() throws Exception {
    when(service.create(any())).thenReturn(sampleWatch());
    mockMvc.perform(post("/api/watches")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"productName":"Sony WH-1000XM5","merchantId":"merchant-a","currency":"USD",
                 "targetPrice":"300.00","maxUnitPrice":"350.00","maxTotal":"350.00","maxQuantity":1}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productName").value("Sony WH-1000XM5"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void createWatchRejectsInvalidCurrency() throws Exception {
    mockMvc.perform(post("/api/watches")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"productName":"Sony WH-1000XM5","merchantId":"merchant-a","currency":"US",
                 "targetPrice":"300.00","maxUnitPrice":"350.00","maxTotal":"350.00","maxQuantity":1}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void createWatchRejectsMissingProductName() throws Exception {
    mockMvc.perform(post("/api/watches")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"merchantId":"merchant-a","currency":"USD",
                 "targetPrice":"300.00","maxUnitPrice":"350.00","maxTotal":"350.00","maxQuantity":1}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listWatches() throws Exception {
    when(service.list()).thenReturn(List.of(sampleWatch()));
    mockMvc.perform(get("/api/watches"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productName").value("Sony WH-1000XM5"));
  }

  @Test
  void getMissingWatchReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.get(id)).thenThrow(new ResourceNotFoundException("Watch not found: " + id));
    mockMvc.perform(get("/api/watches/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").exists());
  }
}
