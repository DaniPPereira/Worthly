package com.worthly.categories.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleEntity;
import com.worthly.categories.adapter.out.persistence.CategoryEntity;
import com.worthly.categories.application.CategoryAdminService;
import com.worthly.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryAdminService admin;

    public CategoryController(CategoryAdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return admin.listCategories(UUID.fromString(jwt.getSubject())).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryCreateRequest request) {
        return CategoryResponse.from(
                admin.createCategory(UUID.fromString(jwt.getSubject()), request.label(), request.parentId()));
    }

    @PatchMapping("/categories/{categoryId}")
    public CategoryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryPatchRequest request) {
        return CategoryResponse.from(
                admin.updateCategory(
                        UUID.fromString(jwt.getSubject()), categoryId, request.label(), request.parentId()));
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        admin.deleteCategory(UUID.fromString(jwt.getSubject()), categoryId);
    }

    @GetMapping("/categorization-rules")
    public List<RuleResponse> listRules(@AuthenticationPrincipal Jwt jwt) {
        return admin.listRules(UUID.fromString(jwt.getSubject())).stream().map(RuleResponse::from).toList();
    }

    @PostMapping("/categorization-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse createRule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RuleCreateRequest request) {
        return RuleResponse.from(admin.createRule(
                UUID.fromString(jwt.getSubject()),
                request.priority(),
                request.field(),
                request.operator(),
                request.matchValue(),
                parseAmount(request.amountMin()),
                parseAmount(request.amountMax()),
                request.targetCategoryId(),
                request.enabled()));
    }

    @PatchMapping("/categorization-rules/{ruleId}")
    public RuleResponse updateRule(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ruleId, @RequestBody JsonNode body) {
        if (body == null || body.isEmpty()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        Integer priority = body.has("priority") ? body.get("priority").asInt() : null;
        return RuleResponse.from(admin.updateRule(
                UUID.fromString(jwt.getSubject()),
                ruleId,
                priority,
                textOrNull(body, "field"),
                textOrNull(body, "operator"),
                textOrNull(body, "matchValue"),
                parseAmount(textOrNull(body, "amountMin")),
                body.has("amountMin"),
                parseAmount(textOrNull(body, "amountMax")),
                body.has("amountMax"),
                body.has("targetCategoryId") && !body.get("targetCategoryId").isNull()
                        ? UUID.fromString(body.get("targetCategoryId").asText())
                        : null,
                body.has("enabled") ? body.get("enabled").asBoolean() : null));
    }

    @DeleteMapping("/categorization-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ruleId) {
        admin.deleteRule(UUID.fromString(jwt.getSubject()), ruleId);
    }

    private static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
    }

    private static String textOrNull(JsonNode body, String field) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        return body.get(field).asText();
    }

    public record CategoryResponse(UUID id, String code, String label, UUID parentId, boolean system) {
        static CategoryResponse from(CategoryEntity entity) {
            return new CategoryResponse(
                    entity.getId(), entity.getCode(), entity.getLabel(), entity.getParentId(), entity.isSystem());
        }
    }

    public record CategoryCreateRequest(@NotBlank @Size(max = 80) String label, UUID parentId) {}

    public record CategoryPatchRequest(@Size(max = 80) String label, UUID parentId) {}

    public record RuleResponse(
            UUID id,
            int priority,
            String field,
            String operator,
            String matchValue,
            String amountMin,
            String amountMax,
            UUID targetCategoryId,
            boolean enabled) {
        static RuleResponse from(CategorizationRuleEntity entity) {
            return new RuleResponse(
                    entity.getId(),
                    entity.getPriority(),
                    entity.getField(),
                    entity.getOperator(),
                    entity.getMatchValue(),
                    entity.getAmountMin() == null ? null : entity.getAmountMin().stripTrailingZeros().toPlainString(),
                    entity.getAmountMax() == null ? null : entity.getAmountMax().stripTrailingZeros().toPlainString(),
                    entity.getTargetCategoryId(),
                    entity.isEnabled());
        }
    }

    public record RuleCreateRequest(
            @Min(0) int priority,
            @NotBlank String field,
            @NotBlank String operator,
            @NotBlank @Size(max = 200) String matchValue,
            String amountMin,
            String amountMax,
            @NotNull UUID targetCategoryId,
            Boolean enabled) {}
}
