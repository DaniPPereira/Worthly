package com.worthly.categories.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class HeuristicCatalog {

    private final ObjectMapper objectMapper;
    private final List<Entry> entries = new ArrayList<>();

    public HeuristicCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws Exception {
        ClassPathResource resource = new ClassPathResource("categorization/merchant-heuristics.json");
        try (InputStream stream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(stream);
            for (JsonNode node : root) {
                entries.add(new Entry(node.path("key").asText(), node.path("code").asText()));
            }
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public record Entry(String key, String code) {}
}
