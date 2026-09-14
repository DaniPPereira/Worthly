package com.worthly.connections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.worthly.AbstractIntegrationTest;
import com.worthly.support.OwnerAuthClient;
import java.time.Duration;
import java.time.Instant;
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
class EnableBankingIT extends AbstractIntegrationTest {

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
        stubDiscovery();
        stubAuth();
        stubSession("sess-1", "acc-uid-1");
        stubAccountPayloads("acc-uid-1");
    }

    @Test
    void banksEndpointFiltersToV1Choices() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/banks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode banks = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(banks).hasSize(2);
        assertThat(banks.get(0).get("name").asText()).isIn("Banco Santander Totta", "Revolut");
        assertThat(banks.get(1).get("name").asText()).isIn("Banco Santander Totta", "Revolut");
        assertThat(banks.toString()).doesNotContain("Millennium");
    }

    @Test
    void authorizeCallbackReplayAndIdempotentSync() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        String state = startAuthorization(token);
        MvcResult callback = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "test-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        String location = callback.getResponse().getHeader("Location");
        assertThat(location).contains("status=ok").contains("connectionId=");
        String connectionId =
                UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("connectionId");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "test-code")
                        .param("state", state))
                .andExpect(status().isBadRequest());

        MvcResult firstSync = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode first = objectMapper.readTree(firstSync.getResponse().getContentAsString());
        assertThat(first.get("status").asText()).isEqualTo("SUCCEEDED");
        int imported = first.get("importedCount").asInt();
        assertThat(imported).isGreaterThanOrEqualTo(3);

        MvcResult secondSync = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode second = objectMapper.readTree(secondSync.getResponse().getContentAsString());
        assertThat(second.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(second.get("importedCount").asInt()).isZero();

        MvcResult transactions = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode page = objectMapper.readTree(transactions.getResponse().getContentAsString());
        assertThat(page.get("total").asLong()).isEqualTo(3);
        assertThat(page.get("items").get(0).get("economicType").asText()).isEqualTo("EXPENSE");
        assertThat(page.get("items").get(0).get("merchant").asText()).isEqualTo("Merchant Test");
        JsonNode coffee = null;
        for (JsonNode item : page.get("items")) {
            if ("Cafe Test".equals(item.path("merchant").asText())) {
                coffee = item;
            }
        }
        assertThat(coffee).isNotNull();
        assertThat(coffee.get("economicType").asText()).isEqualTo("EXPENSE");
        assertThat(coffee.get("description").asText()).isEqualTo("Coffee");
        assertThat(coffee.get("location").asText()).contains("Lisboa");

        MvcResult accounts = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode accountList = objectMapper.readTree(accounts.getResponse().getContentAsString());
        assertThat(accountList).hasSize(1);
        assertThat(accountList.get(0).get("type").asText()).isEqualTo("CURRENT");
        assertThat(accountList.get(0).get("includedInLiquidCash").asBoolean()).isTrue();
        assertThat(accountList.get(0).get("maskedIdentifier").asText()).startsWith("****");
        String accountId = accountList.get(0).get("id").asText();

        MvcResult balances = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts/" + accountId + "/balances")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode balanceList = objectMapper.readTree(balances.getResponse().getContentAsString());
        assertThat(balanceList).hasSize(2);
        assertThat(balanceList.toString()).contains("\"usedForLiquidCash\":true");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/purge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/connections/" + connectionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void identificationHashKeepsStableLocalAccountId() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        String firstState = startAuthorization(token);
        MvcResult firstCallback = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "test-code")
                        .param("state", firstState))
                .andExpect(status().isFound())
                .andReturn();
        MvcResult accounts = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String originalId = objectMapper
                .readTree(accounts.getResponse().getContentAsString())
                .get(0)
                .get("id")
                .asText();

        ENABLE_BANKING.resetAll();
        stubDiscovery();
        stubAuth();
        stubSession("sess-2", "acc-uid-2");
        String secondState = startAuthorization(token);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "reauth-code")
                        .param("state", secondState))
                .andExpect(status().isFound());

        MvcResult after = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterList = objectMapper.readTree(after.getResponse().getContentAsString());
        assertThat(afterList).hasSize(1);
        assertThat(afterList.get(0).get("id").asText()).isEqualTo(originalId);
        assertThat(firstCallback.getResponse().getHeader("Location")).contains("connectionId=");
    }

    @Test
    void rateLimitSetsRetryAtLeastSixHours() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        String state = startAuthorization(token);
        MvcResult callback = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", "test-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        String connectionId = UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("connectionId");

        ENABLE_BANKING.resetAll();
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/sessions/sess-1")).willReturn(okJson(sessionJson("sess-1", "acc-uid-1"))));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/acc-uid-1/balances"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "21600")
                        .withBody("{\"error\":\"ASPSP_RATE_LIMIT_EXCEEDED\"}")));

        Instant before = Instant.now();
        MvcResult sync = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode body = objectMapper.readTree(sync.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("RATE_LIMITED");
        Instant retryAt = Instant.parse(body.get("nextRetryAt").asText());
        assertThat(retryAt).isAfterOrEqualTo(before.plus(Duration.ofHours(5)));
    }

    private String startAuthorization(String token) throws Exception {
        MvcResult authorize = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/enable-banking/authorize")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Banco Santander Totta\",\"country\":\"PT\",\"returnClient\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(authorize.getResponse().getContentAsString());
        String url = json.get("url").asText();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        return state;
    }

    private static void stubDiscovery() {
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/aspsps"))
                .withQueryParam("country", equalTo("PT"))
                .willReturn(okJson(
                        """
                        {"aspsps":[
                          {"name":"Banco Santander Totta","country":"PT","logo":"https://cdn.example.test/santander.png","maximum_consent_validity":7776000},
                          {"name":"Revolut","country":"PT","logo":"https://cdn.example.test/revolut.png","maximum_consent_validity":7776000},
                          {"name":"Millennium BCP","country":"PT","logo":"https://cdn.example.test/bcp.png","maximum_consent_validity":7776000}
                        ]}
                        """)));
    }

    private static void stubAuth() {
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/auth"))
                .willReturn(okJson("{\"url\":\"https://asp.example.test/start?state={{jsonPath request.body '$.state'}}\"}")));
    }

    private static void stubSession(String sessionId, String accountUid) {
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/sessions")).willReturn(okJson(sessionJson(sessionId, accountUid))));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/sessions/" + sessionId)).willReturn(okJson(sessionJson(sessionId, accountUid))));
        ENABLE_BANKING.stubFor(WireMock.delete(urlPathEqualTo("/sessions/" + sessionId))
                .willReturn(aResponse().withStatus(204)));
    }

    private static void stubAccountPayloads(String accountUid) {
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/" + accountUid + "/balances"))
                .willReturn(okJson(
                        """
                        {"balances":[
                          {"balance_type":"closingBooked","balance_amount":{"amount":"100.00","currency":"EUR"},"reference_date":"2026-09-13"},
                          {"balance_type":"interimAvailable","balance_amount":{"amount":"90.00","currency":"EUR"}}
                        ]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/" + accountUid + "/transactions"))
                .withQueryParam("continuation_key", equalTo("page-2"))
                .willReturn(okJson(
                        """
                        {"transactions":[
                          {"transaction_id":"tx-2","credit_debit_indicator":"CRDT","status":"BOOK",
                           "transaction_amount":{"amount":"2000.00","currency":"EUR"},
                           "booking_date":"2026-09-02","remittance_information":["Salary"],
                           "debtor":{"name":"Employer Test"}},
                          {"entry_reference":"pending-1","credit_debit_indicator":"DBIT","status":"PDNG",
                           "transaction_amount":{"amount":"5.00","currency":"EUR"},
                           "booking_date":"2026-09-03","remittance_information":["Hold"],
                           "creditor":{"name":"Merchant Test"}}
                        ]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/" + accountUid + "/transactions"))
                .withQueryParam("continuation_key", absent())
                .willReturn(okJson(
                        """
                        {"transactions":[
                          {"transaction_id":"tx-1","credit_debit_indicator":"DBIT","status":"BOOK",
                           "transaction_amount":{"amount":"12.50","currency":"EUR"},
                           "booking_date":"2026-09-01","remittance_information":["Coffee"],
                           "creditor":{"name":"Cafe Test","postal_address":{"town_name":"Lisboa","country":"PT"}}}
                        ],"continuation_key":"page-2"}
                        """)));
    }

    private static String sessionJson(String sessionId, String accountUid) {
        return """
                {"session_id":"%s","status":"AUTHORIZED","access":{"valid_until":"2027-01-01T00:00:00Z"},
                 "accounts":[{"uid":"%s","identification_hash":"hash-stable-001","currency":"EUR",
                 "name":"Main current","cash_account_type":"CACC",
                 "account_id":{"iban":"PT50000201231234567890154"}}]}
                """
                .formatted(sessionId, accountUid);
    }
}
