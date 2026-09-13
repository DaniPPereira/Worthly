package com.worthly.investments.adapter.in.web;

import com.worthly.investments.application.InvestmentQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentController {

    private final InvestmentQueryService queryService;

    public InvestmentController(InvestmentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    public InvestmentSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        InvestmentQueryService.Summary summary = queryService.summary(UUID.fromString(jwt.getSubject()));
        return new InvestmentSummaryResponse(
                summary.totalsByCurrency().stream()
                        .map(row -> new InvestmentCurrencyResponse(row.currency(), row.cash(), row.portfolioValue()))
                        .toList(),
                summary.observedAt());
    }

    @GetMapping("/positions")
    public List<PositionResponse> positions(@AuthenticationPrincipal Jwt jwt) {
        return queryService.positions(UUID.fromString(jwt.getSubject())).stream()
                .map(position -> new PositionResponse(
                        position.instrumentKey(),
                        position.ticker(),
                        position.quantity(),
                        position.marketValue() == null
                                ? null
                                : new MoneyResponse(position.marketValue().amount(), position.marketValue().currency()),
                        position.observedAt()))
                .toList();
    }

    public record InvestmentSummaryResponse(List<InvestmentCurrencyResponse> totalsByCurrency, Instant observedAt) {}

    public record InvestmentCurrencyResponse(String currency, String cash, String portfolioValue) {}

    public record PositionResponse(
            String instrumentKey, String ticker, String quantity, MoneyResponse marketValue, Instant observedAt) {}

    public record MoneyResponse(String amount, String currency) {}
}
