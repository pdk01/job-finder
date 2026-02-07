package com.radar.agent.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.radar.agent.Config;
import com.radar.agent.model.Job;
import com.radar.agent.util.Http;
import com.radar.agent.util.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AdzunaSource implements JobSource {
    private final Config.AdzunaConfig config;

    public AdzunaSource(Config.AdzunaConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "adzuna";
    }

    @Override
    public List<Job> fetch(Instant cutoff) {
        if (config == null || !config.enabled || missingKeys()) {
            return List.of();
        }

        List<Job> jobs = new ArrayList<>();
        int pages = Math.max(1, config.pages);
        for (int page = 1; page <= pages; page++) {
            String url = buildUrl(page);
            String body = Http.get(url);
            JsonNode root = Json.readTree(body);
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode node : results) {
                    Instant postedAt = parseDate(node.path("created").asText(null));
                    if (postedAt == null || postedAt.isBefore(cutoff)) {
                        continue;
                    }
                    Job job = new Job();
                    job.id = "adzuna-" + node.path("id").asText();
                    job.source = name();
                    job.company = node.path("company").path("display_name").asText("");
                    job.title = node.path("title").asText("");
                    job.url = node.path("redirect_url").asText("");
                    job.description = node.path("description").asText("");
                    job.postedAt = postedAt;
                    jobs.add(job);
                }
            }
        }
        return jobs;
    }

    private boolean missingKeys() {
        return config.appId == null || config.appId.isBlank() || config.appKey == null || config.appKey.isBlank();
    }

    private String buildUrl(int page) {
        String country = config.country == null || config.country.isBlank() ? "in" : config.country;
        String what = config.query == null || config.query.isBlank() ? "software engineer" : config.query;
        String encoded = URLEncoder.encode(what, StandardCharsets.UTF_8);
        int resultsPerPage = config.resultsPerPage <= 0 ? 50 : config.resultsPerPage;
        return "https://api.adzuna.com/v1/api/jobs/" + country + "/search/" + page
            + "?app_id=" + config.appId
            + "&app_key=" + config.appKey
            + "&results_per_page=" + resultsPerPage
            + "&what=" + encoded
            + "&content-type=application/json";
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}