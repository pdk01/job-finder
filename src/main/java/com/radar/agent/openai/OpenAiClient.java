package com.radar.agent.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.radar.agent.Config;
import com.radar.agent.llm.LlmClient;
import com.radar.agent.model.Job;
import com.radar.agent.util.Http;
import com.radar.agent.util.Json;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenAiClient implements LlmClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final Config.OpenAiConfig config;

    public OpenAiClient(String apiKey, Config.OpenAiConfig config) {
        this.apiKey = apiKey;
        this.config = config;
    }

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
                "postedAt", job.postedAt != null ? DateTimeFormatter.ISO_INSTANT.format(job.postedAt) : "",
                "url", safe(job.url),
                "description", truncate(job.description, 1200)
            ))
            .map(Json::write)
            .collect(Collectors.joining(",\n"));

        String system = "You are an expert technical recruiter. Rank software engineering jobs for a candidate with 1-4 years experience."
            + " Focus on best fit, growth potential, and skills match. Return strict JSON.";

        String user = "Resume:\n" + resume + "\n\nJobs (JSON lines):\n" + jobPayload + "\n\n"
            + "Return a JSON array where each item is {\"id\": string, \"score\": number 0-100, \"summary\": string}."
            + " Include only ids provided. Sort by score desc.";

        String response = callChat(system, user, config.rankerTemperature);
        JsonNode node = Json.readTree(response);
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

    public String generateCoverLetter(String resume, Job job) {
        String system = "You are a helpful assistant that writes concise, tailored cover letters for software engineering roles.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Write a 200-300 word cover letter. Use a confident, professional tone."
            + " Mention 2-3 relevant skills or projects from the resume."
            + " Do not invent experience.";

        return callChat(system, user, config.coverLetterTemperature);
    }

    public List<String> generateCoverLetterVariants(String resume, Job job, int variants) {
        String system = "You write concise, tailored cover letters for software engineering roles.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Generate " + variants + " variants as a JSON array of strings."
            + " Each variant 180-260 words. Use distinct emphasis (impact, collaboration, ownership)."
            + " Do not invent experience.";

        String response = callChat(system, user, config.coverLetterTemperature);
        JsonNode node = Json.readTree(response);
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

    public List<Double> embedding(String text) {
        String body = Json.write(Map.of(
            "model", config.embeddingModel,
            "input", text
        ));

        String response = Http.postJson("https://api.openai.com/v1/embeddings", apiKey, body);
        JsonNode root = Json.readTree(response);
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            JsonNode embedding = data.get(0).path("embedding");
            List<Double> values = new ArrayList<>();
            if (embedding.isArray()) {
                for (JsonNode v : embedding) {
                    values.add(v.asDouble());
                }
            }
            return values;
        }
        return List.of();
    }

    public List<String> normalizeSkills(String resume, Job job) {
        String system = "Extract normalized technical skills.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Return JSON: {\"skills\": [\"Skill1\", \"Skill2\", ...]}.\n"
            + "Normalize variants (React.js -> React, Node.js -> Node, JS -> JavaScript).";

        String response = callChat(system, user, config.analysisTemperature);
        JsonNode node = Json.readTree(response);
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

    public LlmClient.QualityResult assessQuality(Job job) {
        String system = "You evaluate job descriptions for quality and clarity.";
        String user = "Job:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"score\": 0-100, \"notes\": \"short reasoning\"}.\n"
            + "Penalize vague responsibilities, unclear tech stack, unrealistic expectations.";

        String response = callChat(system, user, config.analysisTemperature);
        JsonNode node = Json.readTree(response);
        LlmClient.QualityResult result = new LlmClient.QualityResult();
        result.score = node.path("score").asDouble(-1);
        result.notes = node.path("notes").asText("");
        return result;
    }

    public String companyBrief(Job job) {
        String system = "You write concise company briefs based only on the job description.";
        String user = "Job:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Description: " + truncate(job.description, 2200) + "\n\n"
            + "Write 5-7 bullet points. If info is missing, say \"Not specified in JD\"."
            + " Do not use external knowledge.";

        return callChat(system, user, config.analysisTemperature);
    }

    public LlmClient.GapResult resumeGap(String resume, Job job) {
        String system = "You identify resume gaps relative to a job description.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"gaps\": \"short paragraph\", \"suggestions\": \"3-5 bullets\"}.\n"
            + "Only reference resume content. Do not invent experience.";

        String response = callChat(system, user, config.analysisTemperature);
        JsonNode node = Json.readTree(response);
        LlmClient.GapResult result = new LlmClient.GapResult();
        result.gaps = node.path("gaps").asText("");
        result.suggestions = node.path("suggestions").asText("");
        return result;
    }

    public String outreachMessage(String resume, Job job) {
        String system = "You write concise recruiter outreach messages for software roles.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Write a 6-8 sentence outreach message. Mention 1-2 relevant skills/projects."
            + " Do not invent experience.";

        return callChat(system, user, config.analysisTemperature);
    }

    public LlmClient.TriageResult triage(Job job, String resume) {
        String system = "You are a strict recruiter triage system.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"label\": \"high-fit|reach|low-fit|avoid\", \"reason\": \"1-2 sentences\"}.\n"
            + "Base only on resume and JD. No external info.";

        String response = callChat(system, user, config.analysisTemperature);
        JsonNode node = Json.readTree(response);
        LlmClient.TriageResult result = new LlmClient.TriageResult();
        result.label = node.path("label").asText("");
        result.reason = node.path("reason").asText("");
        return result;
    }

    public LlmClient.AtsResult atsSim(String resume, Job job) {
        String system = "You simulate an ATS parser and reviewer.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"findings\": \"short paragraph\", \"suggestions\": \"4-6 bullets\"}.\n"
            + "Do not invent experience. Focus on formatting, missing keywords, and alignment.";

        String response = callChat(system, user, config.analysisTemperature);
        JsonNode node = Json.readTree(response);
        LlmClient.AtsResult result = new LlmClient.AtsResult();
        result.findings = node.path("findings").asText("");
        result.suggestions = node.path("suggestions").asText("");
        return result;
    }

    public LlmClient.ResumeVariantResult resumeVariants(String resume, Job job) {
        String system = "You craft targeted resume bullet swaps without inventing experience.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return JSON: {\"variantA\": \"4-6 bullets\", \"variantB\": \"4-6 bullets\"}.\n"
            + "VariantA emphasizes impact; VariantB emphasizes systems/engineering depth.";

        String response = callChat(system, user, config.analysisTemperature);
        JsonNode node = Json.readTree(response);
        LlmClient.ResumeVariantResult result = new LlmClient.ResumeVariantResult();
        result.variantA = node.path("variantA").asText("");
        result.variantB = node.path("variantB").asText("");
        return result;
    }

    public String redFlags(Job job) {
        String system = "You flag potential red flags in job descriptions.";
        String user = "Job:\n"
            + "Company: " + safe(job.company) + "\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2400) + "\n\n"
            + "Return 4-6 bullets. If none, say \"No obvious red flags found in JD\".";

        return callChat(system, user, config.analysisTemperature);
    }

    public String portfolioBlurb(String resume, Job job) {
        String system = "You create portfolio-ready project blurbs from the resume.";
        String user = "Resume:\n" + resume + "\n\nJob:\n"
            + "Title: " + safe(job.title) + "\n"
            + "Description: " + truncate(job.description, 2000) + "\n\n"
            + "Write a 5-7 sentence blurb highlighting 1-2 relevant projects."
            + " Do not invent experience.";

        return callChat(system, user, config.analysisTemperature);
    }

    public LlmClient.MultiAgentResult multiAgentAssessment(String resume, Job job) {
        String advocate = callChat(
            "You are an advocate for the candidate.",
            "Resume:\n" + resume + "\n\nJob:\n" + safe(job.title) + "\n" + truncate(job.description, 2000)
                + "\n\nWrite 4-6 bullets describing why the candidate is a strong fit. No inventions.",
            config.analysisTemperature
        );

        String skeptic = callChat(
            "You are a skeptical recruiter.",
            "Resume:\n" + resume + "\n\nJob:\n" + safe(job.title) + "\n" + truncate(job.description, 2000)
                + "\n\nWrite 4-6 bullets describing fit concerns or risks. No inventions.",
            config.analysisTemperature
        );

        String judgePrompt = "Advocate:\n" + advocate + "\n\nSkeptic:\n" + skeptic + "\n\n"
            + "Return JSON: {\"score\": 0-100, \"notes\": \"short paragraph\"}. "
            + "Score reflects balanced assessment.";

        String judge = callChat("You are a hiring committee judge.", judgePrompt, config.analysisTemperature);
        JsonNode node = Json.readTree(judge);
        LlmClient.MultiAgentResult result = new LlmClient.MultiAgentResult();
        result.score = node.path("score").asDouble(-1);
        result.notes = node.path("notes").asText("");
        return result;
    }

    private String callChat(String system, String user, double temperature) {
        String body = Json.write(Map.of(
            "model", config.model,
            "temperature", temperature,
            "messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
            )
        ));

        String response = Http.postJson(API_URL, apiKey, body);
        JsonNode root = Json.readTree(response);
        return root.path("choices").path(0).path("message").path("content").asText("");
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

    // result types provided by LlmClient
}
