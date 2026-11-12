package com.buybound.guardian;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buybound.common.exception.GlobalExceptionHandler;
import com.buybound.guardian.controller.GuardianController;
import com.buybound.guardian.service.GuardianEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GuardianController.class)
@Import({GlobalExceptionHandler.class, GuardianEvaluator.class})
class GuardianControllerTest {
  @Autowired
  private MockMvc mockMvc;

  private static final String BASE_POLICY = """
      "mandateId":"mandate-1","version":1,"currency":"USD",
      "maxUnitPrice":"500.00","maxTotal":"500.00","maxQuantity":2,
      "allowedMerchantIds":["merchant-a"],"maxPurchasesPerDay":3,
      "offerMaxAgeSeconds":3600,"newMerchantBehavior":"HOLD",
      "policyVersion":"1.0","mandateStatus":"ACTIVE"
      """;

  @Test
  void evaluatesAllowThroughHttp() throws Exception {
    mockMvc.perform(post("/api/guardian/evaluations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"policy":{%s},
                 "offer":{"merchantId":"merchant-a","canonicalSku":"SKU-1","quantity":1,
                          "currency":"USD","unitPrice":"300.00","totalPrice":"300.00",
                          "observedAt":"2030-01-01T00:00:00Z","evidenceHash":"h1"},
                 "context":{"purchasesToday":0}}
                """.formatted(BASE_POLICY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("ALLOW"))
        .andExpect(jsonPath("$.reasonCodes[0]").value("WITHIN_POLICY"))
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  void evaluatesBlockThroughHttp() throws Exception {
    mockMvc.perform(post("/api/guardian/evaluations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"policy":{%s},
                 "offer":{"merchantId":"merchant-a","canonicalSku":"SKU-1","quantity":1,
                          "currency":"USD","unitPrice":"900.00","totalPrice":"900.00",
                          "observedAt":"2030-01-01T00:00:00Z","evidenceHash":"h1"},
                 "context":{"purchasesToday":0}}
                """.formatted(BASE_POLICY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("BLOCK"));
  }

  @Test
  void rejectsInvalidRequest() throws Exception {
    mockMvc.perform(post("/api/guardian/evaluations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"policy":{%s}}
                """.formatted(BASE_POLICY)))
        .andExpect(status().isBadRequest());
  }
}
