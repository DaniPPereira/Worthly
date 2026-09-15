package com.worthly.banking;

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
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.support.OwnerAuthClient;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import com.worthly.sync.adapter.out.persistence.SyncRunRepository;
import com.worthly.sync.application.SyncScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
class BankingDomainIT extends AbstractIntegrationTest {

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

    @Autowired
    SyncScheduler syncScheduler;

    @Autowired
    ProviderConnectionRepository connections;

    @Autowired
    SyncRunRepository syncRuns;

    @BeforeEach
    void stubs() {
        ENABLE_BANKING.resetAll();
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/aspsps"))
                .withQueryParam("country", equalTo("PT"))
                .willReturn(okJson(
                        """
                        {"aspsps":[
                          {"name":"Banco Santander Totta","country":"PT","logo":"https://cdn.example.test/santander.png","maximum_consent_validity":7776000},
                          {"name":"Revolut","country":"PT","logo":"https://cdn.example.test/revolut.png","maximum_consent_validity":7776000}
                        ]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/auth"))
                .willReturn(okJson("{\"url\":\"https://asp.example.test/start?state={{jsonPath request.body '$.state'}}\"}")));
        stubSession("santander-code", "sess-santander", "acc-santander", "hash-santander", "Santander current");
        stubSession("revolut-code", "sess-revolut", "acc-revolut", "hash-revolut", "Revolut current");
        stubAccount("acc-santander", "tx-s1", "BOOK", "Phase2SantanderCoffee");
        stubAccount("acc-revolut", "tx-r1", "BOOK", "Phase2RevolutGroceries");
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
    void dualBankSyncFiltersCsvAndScheduledTick() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        UUID santander = connect(token, "Banco Santander Totta", "santander-code");
        UUID revolut = connect(token, "Revolut", "revolut-code");

        MvcResult santanderSync = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + santander + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode santanderSyncJson = objectMapper.readTree(santanderSync.getResponse().getContentAsString());
        assertThat(santanderSyncJson.get("status").asText())
                .withFailMessage(() -> santanderSyncJson.toString() + " unmatched=" + ENABLE_BANKING.findUnmatchedRequests())
                .isEqualTo("SUCCEEDED");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + revolut + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        MvcResult connectionsResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode connectionList = objectMapper.readTree(connectionsResult.getResponse().getContentAsString());
        assertThat(connectionList).hasSizeGreaterThanOrEqualTo(2);

        MvcResult accounts = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String santanderAccountId = null;
        for (JsonNode account : objectMapper.readTree(accounts.getResponse().getContentAsString())) {
            if ("Santander current".equals(account.get("displayName").asText())) {
                santanderAccountId = account.get("id").asText();
            }
        }
        assertThat(santanderAccountId).isNotBlank();

        MvcResult debit = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transactions")
                        .param("accountId", santanderAccountId)
                        .param("direction", "DEBIT")
                        .param("minAmount", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode filtered = objectMapper.readTree(debit.getResponse().getContentAsString());
        assertThat(filtered.get("total").asLong()).isEqualTo(1);
        assertThat(filtered.get("items").get(0).get("description").asText()).isEqualTo("Phase2SantanderCoffee");

        MvcResult csv = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/exports/transactions.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = csv.getResponse().getContentAsString();
        assertThat(csv.getResponse().getContentType()).contains("text/csv");
        assertThat(body).contains("id,accountId,reportingAt");
        assertThat(body).contains("Phase2SantanderCoffee");

        ProviderConnectionEntity entity = connections.findById(santander).orElseThrow();
        entity.setLastSuccessfulSyncAt(Instant.now().minus(Duration.ofHours(4)));
        connections.save(entity);
        syncScheduler.tick();
        SyncRunEntity scheduled = syncRuns
                .findByConnectionIdOrderByStartedAtDesc(santander, PageRequest.of(0, 5))
                .getContent()
                .stream()
                .filter(run -> "SCHEDULED".equals(run.getTriggerType()))
                .findFirst()
                .orElseThrow();
        assertThat(scheduled.getStatus()).isIn("SUCCEEDED", "RATE_LIMITED", "FAILED");
    }

    @Test
    void pendingToBookedDoesNotDuplicate() throws Exception {
        ENABLE_BANKING.resetAll();
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/aspsps"))
                .withQueryParam("country", equalTo("PT"))
                .willReturn(okJson(
                        """
                        {"aspsps":[{"name":"Banco Santander Totta","country":"PT","maximum_consent_validity":7776000}]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/auth"))
                .willReturn(okJson("{\"url\":\"https://asp.example.test/start?state={{jsonPath request.body '$.state'}}\"}")));
        stubSession("pending-code", "sess-pending", "acc-pending", "hash-pending", "Pending account");
        stubAccount("acc-pending", "tx-same", "PDNG", "Hold");

        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        UUID connectionId = connect(token, "Banco Santander Totta", "pending-code");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        stubAccount("acc-pending", "tx-same", "BOOK", "Hold");
        MvcResult second = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/" + connectionId + "/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode sync = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(sync.get("importedCount").asInt()).isZero();
        assertThat(sync.get("updatedCount").asInt()).isGreaterThanOrEqualTo(1);

        MvcResult accounts = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String accountId = null;
        for (JsonNode account : objectMapper.readTree(accounts.getResponse().getContentAsString())) {
            if ("Pending account".equals(account.get("displayName").asText())) {
                accountId = account.get("id").asText();
            }
        }
        assertThat(accountId).isNotBlank();

        MvcResult page = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/transactions")
                        .param("accountId", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(page.getResponse().getContentAsString());
        assertThat(json.get("total").asLong()).isEqualTo(1);
        assertThat(json.get("items").get(0).get("lifecycleStatus").asText()).isEqualTo("BOOKED");
    }

    @Test
    void reauthNotificationCanBeMarkedReadOncePerDay() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        UUID connectionId = connect(token, "Banco Santander Totta", "santander-code");
        ProviderConnectionEntity entity = connections.findById(connectionId).orElseThrow();
        entity.setStatus("REAUTH_REQUIRED");
        connections.save(entity);

        syncScheduler.tick();
        syncScheduler.tick();

        MvcResult listed = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode notifications = objectMapper.readTree(listed.getResponse().getContentAsString());
        JsonNode reauth = null;
        int reauthCount = 0;
        for (JsonNode node : notifications) {
            if ("CONNECTION_REAUTH_REQUIRED".equals(node.get("type").asText())) {
                reauth = node;
                reauthCount++;
            }
        }
        assertThat(reauthCount).isEqualTo(1);
        String id = reauth.get("id").asText();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/notifications/" + id + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        MvcResult after = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(after.getResponse().getContentAsString()).get(0).get("readAt").isNull())
                .isFalse();
    }

    @Test
    void reauthNotificationClearsWhenConnectionIsHealthyAgain() throws Exception {
        String token = OwnerAuthClient.accessToken(mockMvc, objectMapper);
        UUID connectionId = connect(token, "Banco Santander Totta", "santander-code");
        ProviderConnectionEntity entity = connections.findById(connectionId).orElseThrow();
        entity.setStatus("REAUTH_REQUIRED");
        connections.save(entity);
        syncScheduler.tick();

        entity = connections.findById(connectionId).orElseThrow();
        entity.setStatus("ACTIVE");
        connections.save(entity);

        MvcResult listed = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode notifications = objectMapper.readTree(listed.getResponse().getContentAsString());
        boolean sawReauth = false;
        for (JsonNode node : notifications) {
            if ("CONNECTION_REAUTH_REQUIRED".equals(node.get("type").asText())) {
                sawReauth = true;
                assertThat(node.get("readAt").isNull()).isFalse();
            }
        }
        assertThat(sawReauth).isTrue();
    }

    private UUID connect(String token, String bank, String code) throws Exception {
        MvcResult authorize = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/connections/enable-banking/authorize")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + bank + "\",\"country\":\"PT\",\"returnClient\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String url = objectMapper.readTree(authorize.getResponse().getContentAsString()).get("url").asText();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        MvcResult callback = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/connections/enable-banking/callback")
                        .param("code", code)
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        return UUID.fromString(UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build()
                .getQueryParams()
                .getFirst("connectionId"));
    }

    private static void stubSession(String code, String sessionId, String accountUid, String hash, String name) {
        String json = sessionJson(sessionId, accountUid, hash, name);
        ENABLE_BANKING.stubFor(WireMock.post(urlPathEqualTo("/sessions"))
                .withRequestBody(containing(code))
                .willReturn(okJson(json)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/sessions/" + sessionId)).willReturn(okJson(json)));
        ENABLE_BANKING.stubFor(WireMock.delete(urlPathEqualTo("/sessions/" + sessionId))
                .willReturn(aResponse().withStatus(204)));
    }

    private static void stubAccount(String accountUid, String txId, String status, String description) {
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/" + accountUid + "/balances"))
                .willReturn(okJson(
                        """
                        {"balances":[{"balance_type":"interimAvailable","balance_amount":{"amount":"50.00","currency":"EUR"}}]}
                        """)));
        ENABLE_BANKING.stubFor(WireMock.get(urlPathEqualTo("/accounts/" + accountUid + "/transactions"))
                .willReturn(okJson(
                        """
                        {"transactions":[{"transaction_id":"%s","credit_debit_indicator":"DBIT","status":"%s",
                          "transaction_amount":{"amount":"12.50","currency":"EUR"},"booking_date":"2026-09-01",
                          "remittance_information":["%s"],"creditor":{"name":"Cafe Test"}}]}
                        """
                                .formatted(txId, status, description))));
    }

    private static String sessionJson(String sessionId, String accountUid, String hash, String name) {
        return """
                {"session_id":"%s","status":"AUTHORIZED","access":{"valid_until":"2027-01-01T00:00:00Z"},
                 "accounts":[{"uid":"%s","identification_hash":"%s","currency":"EUR","name":"%s",
                 "cash_account_type":"CACC","account_id":{"iban":"PT50000201231234567890154"}}]}
                """
                .formatted(sessionId, accountUid, hash, name);
    }
}
