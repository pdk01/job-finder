package com.radar.agent.pipeline;

import com.radar.agent.Config;
import com.radar.agent.model.Job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilterPipeline {
    private static final Pattern YEARS_RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*(years|yrs)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEARS_SINGLE = Pattern.compile("(\\d+)(?:\\+)?\\s*(years|yrs)", Pattern.CASE_INSENSITIVE);

    public List<Job> filterAndScore(List<Job> jobs, Config config) {
        List<Job> filtered = jobs.stream()
            .filter(job -> matchesKeywords(job, config))
            .filter(job -> matchesExperience(job, config))
            .collect(Collectors.toList());

        List<Job> deduped = dedupe(filtered);
        return scoreHeuristic(deduped, config);
    }

    private boolean matchesKeywords(Job job, Config config) {
        String haystack = (job.title + " " + job.description).toLowerCase(Locale.ROOT);
        boolean include = config.includeKeywords.stream().anyMatch(k -> haystack.contains(k.toLowerCase(Locale.ROOT)));
        boolean exclude = config.excludeKeywords.stream().anyMatch(k -> haystack.contains(k.toLowerCase(Locale.ROOT)));
        return include && !exclude;
    }

    private boolean matchesExperience(Job job, Config config) {
        String text = (job.title + " " + job.description).toLowerCase(Locale.ROOT);
        Integer min = extractMinYears(text);
        if (min == null) {
            return true;
        }
        return min >= config.experienceMinYears && min <= config.experienceMaxYears;
    }

    private Integer extractMinYears(String text) {
        Matcher range = YEARS_RANGE.matcher(text);
        if (range.find()) {
            return Integer.parseInt(range.group(1));
        }
        Matcher single = YEARS_SINGLE.matcher(text);
        if (single.find()) {
            return Integer.parseInt(single.group(1));
        }
        return null;
    }

    private List<Job> dedupe(List<Job> jobs) {
        Map<String, Job> seen = new HashMap<>();
        for (Job job : jobs) {
            String key = job.url != null && !job.url.isBlank() ? job.url : (job.company + "|" + job.title);
            if (!seen.containsKey(key)) {
                seen.put(key, job);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private List<Job> scoreHeuristic(List<Job> jobs, Config config) {
        for (Job job : jobs) {
            double score = 0;
            String text = (job.title + " " + job.description).toLowerCase(Locale.ROOT);
            for (String keyword : config.includeKeywords) {
                if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score += 5;
                }
            }
            for (String keyword : config.excludeKeywords) {
                if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score -= 8;
                }
            }
            Integer years = extractMinYears(text);
            if (years != null) {
                score += 10 - Math.abs(years - ((config.experienceMinYears + config.experienceMaxYears) / 2.0)) * 2;
            }
            job.score = score;
        }
        return jobs;
    }
}