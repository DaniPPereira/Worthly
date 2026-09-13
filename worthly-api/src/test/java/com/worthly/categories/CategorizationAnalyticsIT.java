package com.worthly.categories;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.worthly.AbstractIntegrationTest;
import com.worthly.support.OwnerAuthClient;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
class CategorizationAnalyticsIT extends AbstractIntegrationTest {

    private static final WireMockServer ENABLE_BANKING =
            new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        ENABLE_BANKING.start();
    }

    @DynamicPropertySource
    static void enableBankingProperties(DynamicPropertyRegistry registry) {
        registry.add("worthly.enable-banking.base-url", ENABLE_BANKING::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        ENABLE_BANKING.stop();
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void stubs() {
        ENABLE_BANKING.resetAll();
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/aspsps"))
                .withQueryParam("country", equalTo("PT"))
                .willReturn(okJson(
                        """
                        {"aspsps":[
                          {"name":"Banco Santander Totta","country":"PT","maximum_consent_validity":7776000}
                        ]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/auth"))
                .willReturn(okJson("{\"url\":\"https://asp.example.test/start?state={{jsonPath request.body '$.state'}}\"}")));
        stubSession();
        ENABLE_BANKING.stubFor(WireMock.get(urlPathMatching("/sessions/.*"))
                .atPriority(10)
                .willReturn(okJson("{\"session_id\":\"fallback\",\"status\":\"AUTHORIZED\",\"accounts\":[]}")));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathMatching("/accounts/.*/balances"))
                .atPriority(10)
                .willReturn(okJson("{\"balances\":[]}")));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathMatching("/accounts/.*/transactions"))
                .atPriority(10)
                .willReturn(okJson("{\"transactions\":[]}")));
    }

    @Test
    void taxonomyRulesTransfersAndAnalytics() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        MvcResult categories = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode categoryList = objectMapper.readTree(categories.getResponse().getContentAsString());
        assertThat(categoryList.size()).isGreaterThanOrEqualTo(20);
        String uncategorizedId = code(categoryList, "uncategorized");
        String groceriesId = code(categoryList, "expense.groceries");
        String housingId = code(categoryList, "expense.housing");
        assertThat(uncategorizedId).isEqualTo("a1000000-0000-4000-8000-000000000040");

        UUID connectionId = connect(token);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        JsonNode netflix = transactionByMerchant(token, "Phase3Netflix");
        assertThat(netflix.get("economicType").asText()).isEqualTo("EXPENSE");
        assertThat(netflix.get("categoryId").asText()).isEqualTo(code(categoryList, "expense.subscriptions"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/categorization-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"priority":0,"field":"MERCHANT","operator":"CONTAINS","matchValue":"Phase3RuleShop",
                                 "targetCategoryId":"%s"}
                                """
                                        .formatted(groceriesId)))
                .andExpect(status().isCreated());
        JsonNode ruled = transactionByMerchant(token, "Phase3RuleShop");
        assertThat(ruled.get("categoryId").asText()).isEqualTo(groceriesId);
        assertThat(ruled.get("economicType").asText()).isEqualTo("EXPENSE");

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/transactions/" + netflix.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + housingId + "\",\"notes\":\"manual override\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());
        JsonNode stillManual = transactionByMerchant(token, "Phase3Netflix");
        assertThat(stillManual.get("categoryId").asText()).isEqualTo(housingId);
        assertThat(stillManual.get("notes").asText()).isEqualTo("manual override");

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/transactions/" + stillManual.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":null}"))
                .andExpect(status().isOk());
        JsonNode restored = transactionByMerchant(token, "Phase3Netflix");
        assertThat(restored.get("categoryId").asText()).isEqualTo(code(categoryList, "expense.subscriptions"));

        JsonNode matches = objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transfer-matches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode linked = null;
        JsonNode suggested = null;
        for (JsonNode match : matches) {
            if ("LINKED".equals(match.get("status").asText()) && "AUTO".equals(match.get("method").asText())) {
                linked = match;
            }
            if ("SUGGESTED".equals(match.get("status").asText())) {
                suggested = match;
            }
        }
        assertThat(linked).isNotNull();
        assertThat(suggested).isNotNull();
        JsonNode debit = transactionByMerchant(token, "Phase3OwnedTransfer", "DEBIT");
        assertThat(debit.get("economicType").asText()).isEqualTo("INTERNAL_TRANSFER");
        assertThat(debit.get("transferMatchId").asText()).isEqualTo(linked.get("id").asText());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/transfer-matches/" + suggested.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        JsonNode afterReject = objectMapper.readTree(mockMvc.perform(
                        MockMvcRequestBuilders.get("/api/v1/transfer-matches").param("status", "SUGGESTED")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(afterReject).isEmpty();

        MvcResult monthly = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/analytics/monthly")
                        .param("month", "2026-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode monthJson = objectMapper.readTree(monthly.getResponse().getContentAsString());
        assertThat(monthJson.get("timezone").asText()).isEqualTo("Europe/Lisbon");
        JsonNode eur = monthJson.get("totalsByCurrency").get(0);
        assertThat(eur.get("currency").asText()).isEqualTo("EUR");
        assertThat(eur.get("income").asText()).isEqualTo("0.00");
        assertThat(eur.get("expenses").asText()).isEqualTo("30.99");
        assertThat(eur.get("savings").asText()).isEqualTo("-30.99");
        assertThat(eur.get("savingsRate").isNull()).isTrue();
        assertThat(eur.get("savingsRateReason").asText()).isEqualTo("NO_POSITIVE_INCOME");

        MvcResult summary = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/analytics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode wealth = objectMapper.readTree(summary.getResponse().getContentAsString());
        assertThat(wealth.get("timezone").asText()).isEqualTo("Europe/Lisbon");
        assertThat(wealth.get("totalsByCurrency").get(0).get("investmentValue").asText()).isEqualTo("0.00");

        MvcResult csv = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/exports/transactions.csv")
                        .param("q", "Phase3Netflix")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(csv.getResponse().getContentAsString()).contains("expense.subscriptions");
    }

    private JsonNode transactionByMerchant(String token, String merchant) throws Exception {
        return transactionByMerchant(token, merchant, null);
    }

    private JsonNode transactionByMerchant(String token, String merchant, String direction) throws Exception {
        var request = MockMvcRequestBuilders.get("/api/v1/transactions")
                .param("q", merchant)
                .header("Authorization", "Bearer " + token);
        if (direction != null) {
            request = request.param("direction", direction);
        }
        MvcResult page = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(page.getResponse().getContentAsString()).get("items");
        assertThat(items).isNotEmpty();
        return items.get(0);
    }

    private static String code(JsonNode categories, String code) {
        for (JsonNode category : categories) {
            if (code.equals(category.path("code").asText())) {
                return category.get("id").asText();
            }
        }
        throw new AssertionError("missing category " + code);
    }

    private UUID connect(String token) throws Exception {
        MvcResult authorize = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/enable-banking/authorize")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Banco Santander Totta\",\"country\":\"PT\",\"returnClient\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String url = objectMapper.readTree(authorize.getResponse().getContentAsString()).get("url").asText();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        MvcResult callback = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "phase3-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        return UUID.fromString(UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("connectionId"));
    }

    private static void stubSession() {
        String session =
                """
                {"session_id":"sess-phase3","status":"AUTHORIZED","access":{"valid_until":"2027-01-01T00:00:00Z"},
                 "accounts":[
                   {"uid":"acc-phase3-a","identification_hash":"hash-phase3-a","currency":"EUR","name":"Phase3 Current A",
                    "cash_account_type":"CACC","account_id":{"iban":"PT50000201231234567890111"}},
                   {"uid":"acc-phase3-b","identification_hash":"hash-phase3-b","currency":"EUR","name":"Phase3 Current B",
                    "cash_account_type":"CACC","account_id":{"iban":"PT50000201231234567890122"}}
                 ]}
                """;
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/sessions"))
                .withRequestBody(containing("phase3-code"))
                .willReturn(okJson(session)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/sessions/sess-phase3")).willReturn(okJson(session)));
        ENABLE_BANKING.stubFor(WireMock.delete(urlPathEqualTo("/sessions/sess-phase3"))
                .willReturn(aResponse().withStatus(204)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-phase3-a/balances"))
                .willReturn(okJson(
                        """
                        {"balances":[{"balance_type":"interimAvailable","balance_amount":{"amount":"80.00","currency":"EUR"}}]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-phase3-b/balances"))
                .willReturn(okJson(
                        """
                        {"balances":[{"balance_type":"closingBooked","balance_amount":{"amount":"20.00","currency":"EUR"}}]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-phase3-a/transactions"))
                .willReturn(okJson(
                        """
                        {"transactions":[
                          {"transaction_id":"p3-netflix","credit_debit_indicator":"DBIT","status":"BOOK",
                           "transaction_amount":{"amount":"9.99","currency":"EUR"},"booking_date":"2026-01-10",
                           "remittance_information":["Subscription"],"creditor":{"name":"Phase3Netflix"}},
                          {"transaction_id":"p3-rule","credit_debit_indicator":"DBIT","status":"BOOK",
                           "transaction_amount":{"amount":"21.00","currency":"EUR"},"booking_date":"2026-01-11",
                           "remittance_information":["Shop"],"creditor":{"name":"Phase3RuleShop"}},
                          {"transaction_id":"p3-debit-transfer","credit_debit_indicator":"DBIT","status":"BOOK",
                           "transaction_amount":{"amount":"100.00","currency":"EUR"},"booking_date":"2026-01-12",
                           "remittance_information":["Internal transfer to own account"],"creditor":{"name":"Phase3OwnedTransfer"}},
                          {"transaction_id":"p3-suggest-debit","credit_debit_indicator":"DBIT","status":"BOOK",
                           "transaction_amount":{"amount":"33.00","currency":"EUR"},"booking_date":"2026-01-13",
                           "remittance_information":["Misc"],"creditor":{"name":"Phase3SuggestDebit"}}
                        ]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-phase3-b/transactions"))
                .willReturn(okJson(
                        """
                        {"transactions":[
                          {"transaction_id":"p3-credit-transfer","credit_debit_indicator":"CRDT","status":"BOOK",
                           "transaction_amount":{"amount":"100.00","currency":"EUR"},"booking_date":"2026-01-12",
                           "remittance_information":["Internal transfer from own account"],"debtor":{"name":"Phase3OwnedTransfer"}},
                          {"transaction_id":"p3-suggest-credit","credit_debit_indicator":"CRDT","status":"BOOK",
                           "transaction_amount":{"amount":"33.00","currency":"EUR"},"booking_date":"2026-01-13",
                           "remittance_information":["Misc"],"debtor":{"name":"Phase3SuggestCredit"}}
                        ]}
                        """)));
    }
}
