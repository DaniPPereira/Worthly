package com.worthly.investments;

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
class Trading212IT extends AbstractIntegrationTest {

    private static final WireMockServer T212 = new WireMockServer(options().dynamicPort());
    private static final WireMockServer ENABLE_BANKING =
            new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        T212.start();
        ENABLE_BANKING.start();
        stubTrading212();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("worthly.trading-212.base-url", () -> T212.baseUrl() + "/api/v0");
        registry.add("worthly.trading-212.api-key", () -> "test-t212-key");
        registry.add("worthly.trading-212.api-secret", () -> "test-t212-secret");
        registry.add("worthly.enable-banking.base-url", ENABLE_BANKING::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        T212.stop();
        ENABLE_BANKING.stop();
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void stubs() {
        T212.resetAll();
        stubTrading212();
        ENABLE_BANKING.resetAll();
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/aspsps"))
                .withQueryParam("country", equalTo("PT"))
                .willReturn(okJson(
                        """
                        {"aspsps":[{"name":"Banco Santander Totta","country":"PT","maximum_consent_validity":7776000}]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/auth"))
                .willReturn(okJson("{\"url\":\"https://asp.example.test/start?state={{jsonPath request.body '$.state'}}\"}")));
        String session =
                """
                {"session_id":"sess-t212-bank","status":"AUTHORIZED","access":{"valid_until":"2027-01-01T00:00:00Z"},
                 "accounts":[{"uid":"acc-t212-bank","identification_hash":"hash-t212-bank","currency":"EUR",
                 "name":"Phase4 Bank","cash_account_type":"CACC","account_id":{"iban":"PT50000201231234567890333"}}]}
                """;
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/sessions"))
                .withRequestBody(containing("t212-bank-code"))
                .willReturn(okJson(session)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/sessions/sess-t212-bank")).willReturn(okJson(session)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-t212-bank/balances"))
                .willReturn(okJson(
                        """
                        {"balances":[{"balance_type":"interimAvailable","balance_amount":{"amount":"40.00","currency":"EUR"}}]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-t212-bank/transactions"))
                .willReturn(okJson(
                        """
                        {"transactions":[{"transaction_id":"p4-fund","credit_debit_indicator":"DBIT","status":"BOOK",
                          "transaction_amount":{"amount":"50.00","currency":"EUR"},"booking_date":"2026-02-01",
                          "remittance_information":["Trading 212 deposit"],"creditor":{"name":"Trading 212"}}]}
                        """)));
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
    void appBackedSyncSummaryPositionsAndFundingMatch() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/trading-212")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"test-t212-key\",\"apiSecret\":\"test-t212-secret\"}"))
                .andExpect(status().isOk());
        JsonNode connections = objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String t212Id = null;
        for (JsonNode connection : connections) {
            if ("TRADING_212".equals(connection.get("provider").asText())) {
                t212Id = connection.get("id").asText();
                assertThat(connection.get("status").asText()).isEqualTo("ACTIVE");
            }
        }
        assertThat(t212Id).isNotBlank();

        MvcResult sync = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + t212Id + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(objectMapper.readTree(sync.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo("SUCCEEDED");

        MvcResult summary = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/investments/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode investment = objectMapper.readTree(summary.getResponse().getContentAsString());
        assertThat(investment.get("totalsByCurrency").get(0).get("currency").asText()).isEqualTo("EUR");
        assertThat(investment.get("totalsByCurrency").get(0).get("cash").asText()).isEqualTo("25.50");
        assertThat(investment.get("totalsByCurrency").get(0).get("portfolioValue").asText()).isEqualTo("1100.00");

        MvcResult positions = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/investments/positions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode positionList = objectMapper.readTree(positions.getResponse().getContentAsString());
        assertThat(positionList).hasSize(1);
        assertThat(positionList.get(0).get("instrumentKey").asText()).isEqualTo("VWCE_EQ");
        assertThat(positionList.toString()).doesNotContain("__cash__");

        MvcResult accounts = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(accounts.getResponse().getContentAsString()).toString())
                .doesNotContain("BROKERAGE");

        UUID bank = connectBank(token);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + bank + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        JsonNode matches = objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transfer-matches")
                        .param("status", "LINKED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(matches).isNotEmpty();

        JsonNode bankTx = objectMapper
                .readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transactions")
                                .param("q", "Trading 212 deposit")
                                .param("direction", "DEBIT")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("items")
                .get(0);
        assertThat(bankTx.get("economicType").asText()).isEqualTo("INVESTMENT_FUNDING");

        MvcResult wealth = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/analytics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode eur = objectMapper.readTree(wealth.getResponse().getContentAsString()).get("totalsByCurrency").get(0);
        assertThat(eur.get("investmentValue").asText()).isEqualTo("1125.50");

        T212.stubFor(WireMock.get(urlPathEqualTo("/api/v0/equity/account/summary"))
                .willReturn(aResponse().withStatus(401)));
        MvcResult rejected = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/trading-212")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"test-t212-key\",\"apiSecret\":\"test-t212-secret\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(rejected.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo("ERROR");
    }

    private UUID connectBank(String token) throws Exception {
        MvcResult authorize = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/enable-banking/authorize")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Banco Santander Totta\",\"country\":\"PT\",\"returnClient\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String url = objectMapper.readTree(authorize.getResponse().getContentAsString()).get("url").asText();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        MvcResult callback = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "t212-bank-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        return UUID.fromString(UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("connectionId"));
    }

    private static void stubTrading212() {
        T212.stubFor(WireMock.get(urlPathEqualTo("/api/v0/equity/account/summary"))
                .willReturn(okJson(
                        """
                        {"id":212001,"currency":"EUR",
                         "cash":{"availableToTrade":25.5,"inPies":0,"reservedForOrders":0},
                         "investments":{"currentValue":1100},"totalValue":1125.5}
                        """)));
        T212.stubFor(WireMock.get(urlPathEqualTo("/api/v0/equity/positions"))
                .willReturn(okJson(
                        """
                        [{"ticker":"VWCE_EQ","quantity":10,"averagePrice":100,"currentPrice":110,
                          "instrument":{"ticker":"VWCE_EQ","currency":"EUR"},
                          "walletImpact":{"currency":"EUR","currentValue":1100}}]
                        """)));
        T212.stubFor(WireMock.get(urlPathEqualTo("/api/v0/equity/history/transactions"))
                .willReturn(okJson(
                        """
                        {"items":[{"amount":50,"currency":"EUR","dateTime":"2026-02-01T10:00:00Z",
                          "reference":"dep-1","type":"DEPOSIT"}],"nextPagePath":null}
                        """)));
        T212.stubFor(WireMock.get(urlPathEqualTo("/api/v0/equity/history/dividends"))
                .willReturn(okJson(
                        """
                        {"items":[{"amount":1.25,"currency":"EUR","paidOn":"2026-02-02T00:00:00Z",
                          "reference":"div-1","ticker":"VWCE_EQ","type":"ORDINARY"}],"nextPagePath":null}
                        """)));
    }
}
