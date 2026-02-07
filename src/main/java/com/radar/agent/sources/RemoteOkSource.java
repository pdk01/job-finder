package com.radar.agent.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.radar.agent.model.Job;
import com.radar.agent.util.Http;
import com.radar.agent.util.Json;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RemoteOkSource implements JobSource {
    private static final String API_URL = "https://remoteok.com/api";

    @Override
    public String name() {
        return "remoteok";
    }

    @Override
    public List<Job> fetch(Instant cutoff) {
        String body = Http.get(API_URL);
        JsonNode root = Json.readTree(body);
        List<Job> jobs = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (!node.has("id")) {
                    continue; // metadata row
                }
                Instant postedAt = parsePostedAt(node);
                if (postedAt == null || postedAt.isBefore(cutoff)) {
                    continue;
                }
                Job job = new Job();
                job.id = "remoteok-" + node.path("id").asText();
                job.source = name();
                job.company = node.path("company").asText("");
                job.title = node.path("position").asText("");
                job.url = node.path("url").asText("");
                job.description = node.path("description").asText("");
                job.postedAt = postedAt;
                jobs.add(job);
            }
        }
        return jobs;
    }

    private Instant parsePostedAt(JsonNode node) {
        if (node.has("date_epoch")) {
            return Instant.ofEpochSecond(node.path("date_epoch").asLong());
        }
        if (node.has("epoch")) {
            return Instant.ofEpochSecond(node.path("epoch").asLong());
        }
        String date = node.path("date").asText(null);
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(date).toInstant();
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(date + "T00:00:00+00:00").toInstant();
            } catch (Exception ignored2) {
                try {
                    return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).parse(date));
                } catch (Exception ignored3) {
                    return null;
                }
            }
        }
    }
}