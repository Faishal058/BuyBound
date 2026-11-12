package com.buybound.watch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buybound.common.exception.GlobalExceptionHandler;
import com.buybound.watch.controller.MandateController;
import com.buybound.watch.domain.MandateStatus;
import com.buybound.watch.dto.ActiveMandateTelemetryResponse;
import com.buybound.watch.dto.UpdateMandateRequest;
import com.buybound.watch.service.MandateService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MandateController.class)
@Import(GlobalExceptionHandler.class)
class MandateControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private MandateService service;

  @Test
  void activeTelemetryIncludesPerItemLimitAndBudget() throws Exception {
    UUID mandateId = UUID.randomUUID();
    UUID watchId = UUID.randomUUID();
    when(service.getActiveTelemetry()).thenReturn(new ActiveMandateTelemetryResponse(
        mandateId, watchId, MandateStatus.ACTIVE, "USD",
        new BigDecimal("500.00"), new BigDecimal("100.00"), new BigDecimal("400.00"),
        2, 3, false, null, null,
        new BigDecimal("61.00"), 1L, 0L, 1L, new BigDecimal("2.50"),
        new BigDecimal("260.00"), List.of("bestbuy.com", "macys.com")));

    mockMvc.perform(get("/api/mandates/active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.maxUnitPrice").value(260.00))
        .andExpect(jsonPath("$.remainingBudget").value(400.00));
  }

  @Test
  void restoreReactivatesMandate() throws Exception {
    UUID mandateId = UUID.randomUUID();
    when(service.restore(mandateId)).thenReturn(Map.of(
        "status", "ACTIVE",
        "restoredMandates", 1,
        "reactivatedWatches", 2,
        "message", "Automated buying re-enabled."));

    mockMvc.perform(post("/api/mandates/" + mandateId + "/restore"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.restoredMandates").value(1));
  }

  @Test
  void putUpdatesSpendingLimitsAndAllowedMerchants() throws Exception {
    UUID mandateId = UUID.randomUUID();
    UUID watchId = UUID.randomUUID();
    when(service.updateLimits(eq(mandateId), any(UpdateMandateRequest.class)))
        .thenReturn(new ActiveMandateTelemetryResponse(
            mandateId, watchId, MandateStatus.ACTIVE, "USD",
            new BigDecimal("3000.00"), BigDecimal.ZERO, new BigDecimal("3000.00"),
            0, 3, true, "buyer@example.com", "VAULT-1",
            BigDecimal.ZERO, 0L, 0L, 0L, BigDecimal.ZERO,
            new BigDecimal("1500.00"), List.of("bestbuy.com", "amazon.com")));

    mockMvc.perform(put("/api/mandates/" + mandateId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"maxUnitPrice":1500.00,"maxWeeklySpend":3000.00,"allowedMerchantIds":["bestbuy.com","amazon.com"]}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxUnitPrice").value(1500.00))
        .andExpect(jsonPath("$.maxWeeklySpend").value(3000.00));
  }
}

