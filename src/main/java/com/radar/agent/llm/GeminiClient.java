package com.radar.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.radar.agent.Config;
import com.radar.agent.model.Job;
import com.radar.agent.util.Http;
import com.radar.agent.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeminiClient implements LlmClient {
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static long lastCallAt = 0;

    private final String apiKey;
    private final Config.GeminiConfig config;

    public GeminiClient(String apiKey, Config.GeminiConfig config) {
        this.apiKey = apiKey;
        this.config = config;
    }

    @Override
    public List<Job> rankJobs(String resume, List<Job> jobs, int maxResults) {
        if (jobs.isEmpty()) {
            return jobs;
        }

        List<Job> trimmed = jobs.size() > maxResults ? jobs.subList(0, maxResults) : jobs;
        String jobPayload = trimmed.stream()
            .map(job -> Map.of(
                "id", job.id,
                "company", safe(job.company),
                "title", safe(job.title),
                "postedAt", job.postedAt != null ? job.postedAt.toString() : "",
                "url", safe(job.url),
                "description", truncate(job.description, 1200)
            ))
            .map(Json::write)
            .collect(java.util.stream.Collectors.joining(",\n"));

        String system = "You are an expert technical recruiter. Rank software engineering jobs for a candidate with 1-4 years experience."
            + " Focus on best fit, growth potential, and skills match. Return strict JSON.";

        String user = "Resume:\n" + resume + "\n\nJobs (JSON lines):\n" + jobPayload + "\n\n"
            + "Return a JSON array where each item is {\"id\": string, \"score\": number 0-100, \"summary\": string}."
            + " Include only ids provided. Sort by score desc.";

        String response = callText(system, user, config.rankerTemperature);
        JsonNode node = Json.readTreeLenient(response);
        if (!node.isArray()) {
            return jobs;
        }

        List<Job> ranked = new ArrayList<>();
        for (JsonNode item : node) {
            String id = item.path("id").asText(null);
            if (id == null) {
                continue;
            }
            Job job = jobs.stream().filter(j -> id.equals(j.id)).findFirst().orElse(null);
            if (job == null) {
                continue;
            }
            job.score = item.path("score").asDouble(-1);
            job.summary = item.path("summary").asText(null);
            ranked.add(job);
        }
        return ranked.isEmpty() ? jobs : ranked;
    }

    @Override
    public String generateCoverLetter(String resume, Job job) {
        String system = "You are a helpful assistant that writes concise, tailored cover letters for software engineering roles.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Write a 200-300 word cover letter. Use a confident, professional tone."
            + " Mention 2-3 relevant skills or projects from the resume."
            + " Do not invent experience.";

        return callText(system, user, config.coverLetterTemperature);
    }

    @Override
    public List<String> generateCoverLetterVariants(String resume, Job job, int variants) {
        String system = "You write concise, tailored cover letters for software engineering roles.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Generate " + variants + " variants as a JSON array of strings."
            + " Each variant 180-260 words. Use distinct emphasis (impact, collaboration, ownership)."
            + " Do not invent experience.";

        String response = callText(system, user, config.coverLetterTemperature);
        JsonNode node = Json.readTreeLenient(response);
        List<String> letters = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    letters.add(item.asText());
                }
            }
        } else if (node.isTextual()) {
            letters.add(node.asText());
        }
        return letters;
    }

    @Override
    public List<Double> embedding(String text) {
        String model = config.embeddingModel == null ? "gemini-embedding-001" : config.embeddingModel;
        throttle();
        String body = Json.write(Map.of(
            "content", Map.of(
                "parts", List.of(Map.of("text", text))
            )
        ));

        String response = Http.postJson(BASE_URL + model + ":embedContent", apiKey, body, "x-goog-api-key");
        JsonNode root = Json.readTreeLenient(response);
        JsonNode values = root.path("embedding").path("values");
        List<Double> result = new ArrayList<>();
        if (values.isArray()) {
            for (JsonNode v : values) {
                result.add(v.asDouble());
            }
        }
        return result;
    }

    @Override
    public List<String> normalizeSkills(String resume, Job job) {
        String system = "Extract normalized technical skills.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Return JSON: {\"skills\": [\"Skill1\", \"Skill2\", ...]}."
            + "Normalize variants (React.js -> React, Node.js -> Node, JS -> JavaScript).";

        String response = callText(system, user, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(response);
        List<String> skills = new ArrayList<>();
        JsonNode arr = node.path("skills");
        if (arr.isArray()) {
            for (JsonNode item : arr) {
                if (item.isTextual()) {
                    skills.add(item.asText());
                }
            }
        }
        return skills;
    }

    @Override
    public QualityResult assessQuality(Job job) {
        String system = "You evaluate job descriptions for quality and clarity.";
        String user = "Job:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"score\": 0-100, \"notes\": \"short reasoning\"}."
            + "Penalize vague responsibilities, unclear tech stack, unrealistic expectations.";

        String response = callText(system, user, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(response);
        QualityResult result = new QualityResult();
        result.score = node.path("score").asDouble(-1);
        result.notes = node.path("notes").asText("");
        return result;
    }

    @Override
    public String companyBrief(Job job) {
        String system = "You write concise company briefs based only on the job description.";
        String user = "Job:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Description: " + truncate(job.description, 2200) + "\n\n"
            + "Write 5-7 bullet points. If info is missing, say \"Not specified in JD\"."
            + " Do not use external knowledge.";

        return callText(system, user, config.analysisTemperature);
    }

    @Override
    public GapResult resumeGap(String resume, Job job) {
        String system = "You identify resume gaps relative to a job description.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"gaps\": \"short paragraph\", \"suggestions\": \"3-5 bullets\"}."
            + "Only reference resume content. Do not invent experience.";

        String response = callText(system, user, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(response);
        GapResult result = new GapResult();
        result.gaps = node.path("gaps").asText("");
        result.suggestions = node.path("suggestions").asText("");
        return result;
    }

    @Override
    public String outreachMessage(String resume, Job job) {
        String system = "You write concise recruiter outreach messages for software roles.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Write a 6-8 sentence outreach message. Mention 1-2 relevant skills/projects."
            + " Do not invent experience.";

        return callText(system, user, config.analysisTemperature);
    }

    @Override
    public TriageResult triage(Job job, String resume) {
        String system = "You are a strict recruiter triage system.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"label\": \"high-fit|reach|low-fit|avoid\", \"reason\": \"1-2 sentences\"}."
            + "Base only on resume and JD. No external info.";

        String response = callText(system, user, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(response);
        TriageResult result = new TriageResult();
        result.label = node.path("label").asText("");
        result.reason = node.path("reason").asText("");
        return result;
    }

    @Override
    public AtsResult atsSim(String resume, Job job) {
        String system = "You simulate an ATS parser and reviewer.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"findings\": \"short paragraph\", \"suggestions\": \"4-6 bullets\"}."
            + "Do not invent experience. Focus on formatting, missing keywords, and alignment.";

        String response = callText(system, user, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(response);
        AtsResult result = new AtsResult();
        result.findings = node.path("findings").asText("");
        result.suggestions = node.path("suggestions").asText("");
        return result;
    }

    @Override
    public ResumeVariantResult resumeVariants(String resume, Job job) {
        String system = "You craft targeted resume bullet swaps without inventing experience.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"variantA\": \"4-6 bullets\", \"variantB\": \"4-6 bullets\"}."
            + "VariantA emphasizes impact; VariantB emphasizes systems/engineering depth.";

        String response = callText(system, user, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(response);
        ResumeVariantResult result = new ResumeVariantResult();
        result.variantA = node.path("variantA").asText("");
        result.variantB = node.path("variantB").asText("");
        return result;
    }

    @Override
    public String redFlags(Job job) {
        String system = "You flag potential red flags in job descriptions.";
        String user = "Job:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return 4-6 bullets. If none, say \"No obvious red flags found in JD\".";

        return callText(system, user, config.analysisTemperature);
    }

    @Override
    public String portfolioBlurb(String resume, Job job) {
        String system = "You create portfolio-ready project blurbs from the resume.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Write a 5-7 sentence blurb highlighting 1-2 relevant projects."
            + " Do not invent experience.";

        return callText(system, user, config.analysisTemperature);
    }

    @Override
    public MultiAgentResult multiAgentAssessment(String resume, Job job) {
        String advocate = callText(
            "You are an advocate for the candidate.",
            "Resume:\n" + resume + "\n\nJob:\n" + safe(job.title) + "\n" + truncate(job.description, 2000)
                + "\n\nWrite 4-6 bullets describing why the candidate is a strong fit. No inventions.",
            config.analysisTemperature
        );

        String skeptic = callText(
            "You are a skeptical recruiter.",
            "Resume:\n" + resume + "\n\nJob:\n" + safe(job.title) + "\n" + truncate(job.description, 2000)
                + "\n\nWrite 4-6 bullets describing fit concerns or risks. No inventions.",
            config.analysisTemperature
        );

        String judgePrompt = "Advocate:\n" + advocate + "\n\nSkeptic:\n" + skeptic + "\n\n"
            + "Return JSON: {\"score\": 0-100, \"notes\": \"short paragraph\"}. "
            + "Score reflects balanced assessment.";

        String judge = callText("You are a hiring committee judge.", judgePrompt, config.analysisTemperature);
        JsonNode node = Json.readTreeLenient(judge);
        MultiAgentResult result = new MultiAgentResult();
        result.score = node.path("score").asDouble(-1);
        result.notes = node.path("notes").asText("");
        return result;
    }

    private String callText(String system, String user, double temperature) {
        String model = config.model == null ? "gemini-2.5-flash" : config.model;
        throttle();
        String body = Json.write(Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", system))),
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
            "generationConfig", Map.of("temperature", temperature)
        ));

        String response = Http.postJson(BASE_URL + model + ":generateContent", apiKey, body, "x-goog-api-key");
        JsonNode root = Json.readTreeLenient(response);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && parts.size() > 0) {
                return parts.get(0).path("text").asText("");
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void throttle() {
        long delay = config.minDelayMs;
        if (delay <= 0) {
            return;
        }
        synchronized (GeminiClient.class) {
            long now = System.currentTimeMillis();
            long wait = lastCallAt + delay - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallAt = System.currentTimeMillis();
        }
    }
}
