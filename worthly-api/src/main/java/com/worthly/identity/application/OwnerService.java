package com.worthly.identity.application;

import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.domain.Owner;
import com.worthly.shared.web.ApiException;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerService {

    private final AppUserRepository users;

    public OwnerService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Owner require(UUID id) {
        AppUserEntity entity = users.findById(id).orElseThrow(() -> ApiException.of(HttpStatus.UNAUTHORIZED, "unauthorized"));
        return toOwner(entity);
    }

    @Transactional
    public Owner updatePreferences(UUID id, String timezone, String currency, String name) {
        if (timezone == null && currency == null && name == null) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        AppUserEntity entity = users.findById(id).orElseThrow(() -> ApiException.of(HttpStatus.UNAUTHORIZED, "unauthorized"));
        if (timezone != null) {
            entity.setReportingTimezone(requireTimezone(timezone));
        }
        if (currency != null) {
            entity.setReportingCurrency(requireCurrency(currency));
        }
        if (name != null) {
            entity.setDisplayName(requireName(name));
        }
        return toOwner(entity);
    }

    public static String timezoneOrDefault(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Europe/Lisbon";
        }
        return requireTimezone(timezone.trim());
    }

    public static String currencyOrDefault(String currency) {
        if (currency == null || currency.isBlank()) {
            return "EUR";
        }
        return requireCurrency(currency.trim());
    }

    public static String requireTimezone(String timezone) {
        try {
            java.time.ZoneId.of(timezone);
            return timezone;
        } catch (Exception ex) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_timezone");
        }
    }

    public static String requireCurrency(String currency) {
        if (!currency.matches("^[A-Z]{3}$")) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_currency");
        }
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (Exception ex) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_currency");
        }
    }

    public static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 80) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        return normalized;
    }

    private static Owner toOwner(AppUserEntity entity) {
        return new Owner(
                entity.getId(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getReportingTimezone(),
                entity.getReportingCurrency(),
                entity.getStatus());
    }
}
