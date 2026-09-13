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
    public Owner updatePreferences(UUID id, String timezone, String currency) {
        if (timezone == null && currency == null) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        AppUserEntity entity = users.findById(id).orElseThrow(() -> ApiException.of(HttpStatus.UNAUTHORIZED, "unauthorized"));
        if (timezone != null) {
            try {
                java.time.ZoneId.of(timezone);
            } catch (Exception ex) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_timezone");
            }
            entity.setReportingTimezone(timezone);
        }
        if (currency != null) {
            if (!currency.matches("^[A-Z]{3}$")) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_currency");
            }
            try {
                Currency.getInstance(currency);
            } catch (Exception ex) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_currency");
            }
            entity.setReportingCurrency(currency);
        }
        return toOwner(entity);
    }

    private static Owner toOwner(AppUserEntity entity) {
        return new Owner(
                entity.getId(),
                entity.getEmail(),
                entity.getReportingTimezone(),
                entity.getReportingCurrency(),
                entity.getStatus());
    }
}
