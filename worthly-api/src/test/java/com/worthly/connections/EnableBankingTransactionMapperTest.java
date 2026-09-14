package com.worthly.connections;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.connections.adapter.out.enablebanking.EnableBankingTransactionMapper;
import com.worthly.connections.application.EnableBankingModels;
import org.junit.jupiter.api.Test;

class EnableBankingTransactionMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void debitUsesCreditorNameAddressAndJoinsRemittance() throws Exception {
        EnableBankingModels.ProviderTransaction tx = EnableBankingTransactionMapper.from(mapper.readTree(
                """
                {
                  "transaction_id": "tx-1",
                  "credit_debit_indicator": "DBIT",
                  "status": "BOOK",
                  "transaction_amount": {"amount": "12.50", "currency": "EUR"},
                  "booking_date": "2026-09-01",
                  "remittance_information": ["COMPRA CONTINENTE", "BOM SUCESSO"],
                  "additional_information": "POS 1234",
                  "creditor": {
                    "name": "Continente",
                    "postal_address": {
                      "address_line": ["Av. da Boavista 123"],
                      "town_name": "Porto",
                      "country": "PT"
                    }
                  },
                  "debtor": {"name": "Owner"}
                }
                """));
        assertThat(tx.counterparty()).isEqualTo("Continente");
        assertThat(tx.description()).isEqualTo("COMPRA CONTINENTE · BOM SUCESSO · POS 1234");
        assertThat(tx.location()).isEqualTo("Av. da Boavista 123, Porto, PT");
        assertThat(tx.creditDebitIndicator()).isEqualTo("DBIT");
    }

    @Test
    void creditUsesDebtorNameAndFallsBackToRemittanceWhenNameMissing() throws Exception {
        EnableBankingModels.ProviderTransaction tx = EnableBankingTransactionMapper.from(mapper.readTree(
                """
                {
                  "credit_debit_indicator": "CRDT",
                  "status": "BOOK",
                  "transaction_amount": {"amount": "2000.00", "currency": "EUR"},
                  "booking_date": "2026-09-02",
                  "remittance_information": ["Salary"],
                  "debtor": {"name": "Employer Test"}
                }
                """));
        assertThat(tx.counterparty()).isEqualTo("Employer Test");
        assertThat(tx.description()).isEqualTo("Salary");
        assertThat(tx.location()).isNull();
    }

    @Test
    void missingCounterpartyUsesFirstRemittanceLine() throws Exception {
        EnableBankingModels.ProviderTransaction tx = EnableBankingTransactionMapper.from(mapper.readTree(
                """
                {
                  "credit_debit_indicator": "DBIT",
                  "status": "BOOK",
                  "transaction_amount": {"amount": "9.99", "currency": "EUR"},
                  "booking_date": "2026-09-03",
                  "remittance_information": ["PINGO DOCE LISBOA"]
                }
                """));
        assertThat(tx.counterparty()).isEqualTo("PINGO DOCE LISBOA");
        assertThat(tx.description()).isEqualTo("PINGO DOCE LISBOA");
    }
}
