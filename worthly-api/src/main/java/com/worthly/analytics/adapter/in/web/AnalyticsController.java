package com.worthly.analytics.adapter.in.web;

import com.worthly.analytics.application.AnalyticsService;
import com.worthly.analytics.application.MonthlyTotals;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public WealthSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        AnalyticsService.WealthSummary summary = analytics.summary(UUID.fromString(jwt.getSubject()));
        return new WealthSummaryResponse(
                summary.asOf(),
                summary.timezone(),
                summary.totalsByCurrency().stream()
                        .map(row -> new WealthCurrencyResponse(
                                row.currency(), row.liquidCash(), row.investmentValue(), row.netWorth()))
                        .toList());
    }

    @GetMapping("/monthly")
    public MonthlyAnalyticsResponse monthly(@AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
        AnalyticsService.MonthlyAnalytics result = analytics.monthly(UUID.fromString(jwt.getSubject()), month);
        return new MonthlyAnalyticsResponse(
                result.month(),
                result.timezone(),
                result.totalsByCurrency().stream().map(MonthlyCurrencyResponse::from).toList());
    }

    public record WealthSummaryResponse(
            Instant asOf, String timezone, List<WealthCurrencyResponse> totalsByCurrency) {}

    public record WealthCurrencyResponse(String currency, String liquidCash, String investmentValue, String netWorth) {}

    public record MonthlyAnalyticsResponse(
            String month, String timezone, List<MonthlyCurrencyResponse> totalsByCurrency) {}

    public record MonthlyCurrencyResponse(
            String currency,
            String income,
            String expenses,
            String invested,
            String savings,
            String savingsRate,
            String savingsRateReason) {
        static MonthlyCurrencyResponse from(MonthlyTotals.Row row) {
            return new MonthlyCurrencyResponse(
                    row.currency(),
                    row.income(),
                    row.expenses(),
                    row.invested(),
                    row.savings(),
                    row.savingsRate(),
                    row.savingsRateReason());
        }
    }
}
