package com.radar.agent.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.radar.agent.model.Job;
import com.radar.agent.util.Http;
import com.radar.agent.util.Json;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class RemotiveSource implements JobSource {
    private static final String API_URL = "https://remotive.com/api/remote-jobs?search=software%20engineer";

    @Override
    public String name() {
        return "remotive";
    }

    @Override
    public List<Job> fetch(Instant cutoff) {
        String body = Http.get(API_URL);
        JsonNode root = Json.readTree(body);
        JsonNode jobsNode = root.path("jobs");
        List<Job> jobs = new ArrayList<>();
        if (jobsNode.isArray()) {
            for (JsonNode node : jobsNode) {
                String published = node.path("publication_date").asText(null);
                Instant postedAt = null;
                if (published != null && !published.isBlank()) {
                    try {
                        postedAt = OffsetDateTime.parse(published).toInstant();
                    } catch (Exception ignored) {
                        postedAt = null;
                    }
                }
                if (postedAt == null || postedAt.isBefore(cutoff)) {
                    continue;
                }
                Job job = new Job();
                job.id = "remotive-" + node.path("id").asText();
                job.source = name();
                job.company = node.path("company_name").asText("");
                job.title = node.path("title").asText("");
                job.url = node.path("url").asText("");
                job.description = node.path("description").asText("");
                job.postedAt = postedAt;
                jobs.add(job);
            }
        }
        return jobs;
    }
}
