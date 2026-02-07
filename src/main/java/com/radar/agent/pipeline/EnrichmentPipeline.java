package com.radar.agent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.radar.agent.Config;
import com.radar.agent.model.Job;
import com.radar.agent.llm.LlmClient;
import com.radar.agent.util.Json;
import com.radar.agent.util.Vector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EnrichmentPipeline {
    public List<Job> enrich(Config config, LlmClient client, String resume, List<Job> jobs) {
        if (resume == null || resume.isBlank() || client == null) {
            return jobs;
        }

        List<Job> scored = new ArrayList<>(jobs);

        if (config.openai.enableEmbeddings) {
            try {
                List<Job> limitedForEmbedding = scored.stream().limit(config.maxResults).collect(Collectors.toList());
                List<Double> resumeEmbedding = client.embedding(truncateForEmbedding(resume));
                for (Job job : limitedForEmbedding) {
                    List<Double> jobEmbedding = client.embedding(truncateForEmbedding(job.title + "\n" + job.description));
                    job.semanticScore = Vector.cosineSimilarity(resumeEmbedding, jobEmbedding);
                    if (job.score >= 0 && job.semanticScore >= 0) {
                        job.score = job.score + job.semanticScore * 50;
                    }
                }
            } catch (Exception e) {
                System.err.println("Embedding scoring failed: " + e.getMessage());
            }
        }

        applyLearningAdjustments(scored);

        try {
            scored = client.rankJobs(resume, scored, config.maxResults);
        } catch (Exception e) {
            System.err.println("LLM ranking failed, using heuristic order: " + e.getMessage());
        }

        return scored;
    }

    public void enrichJob(LlmClient client, String resume, Job job, Config config) {
        if (config.openai.enableNormalization) {
            try {
                job.normalizedSkills = client.normalizeSkills(resume, job);
            } catch (Exception e) {
                System.err.println("Skill normalization failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableQualityScore) {
            try {
                LlmClient.QualityResult quality = client.assessQuality(job);
                job.qualityScore = quality.score;
                job.qualityNotes = quality.notes;
                if (job.score >= 0 && job.qualityScore >= 0) {
                    job.score = job.score + job.qualityScore * 0.2;
                }
            } catch (Exception e) {
                System.err.println("Quality scoring failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableCompanyBrief) {
            try {
                job.companyBrief = client.companyBrief(job);
            } catch (Exception e) {
                System.err.println("Company brief failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableResumeGap) {
            try {
                LlmClient.GapResult gap = client.resumeGap(resume, job);
                job.resumeGaps = gap.gaps;
                job.resumeSuggestions = gap.suggestions;
            } catch (Exception e) {
                System.err.println("Resume gap analysis failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableOutreach) {
            try {
                job.outreachMessage = client.outreachMessage(resume, job);
            } catch (Exception e) {
                System.err.println("Outreach message failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableTriage) {
            try {
                LlmClient.TriageResult triage = client.triage(job, resume);
                job.triageLabel = triage.label;
                job.triageReason = triage.reason;
                if (job.score >= 0 && "high-fit".equalsIgnoreCase(job.triageLabel)) {
                    job.score += 8;
                } else if (job.score >= 0 && "reach".equalsIgnoreCase(job.triageLabel)) {
                    job.score += 3;
                } else if (job.score >= 0 && "low-fit".equalsIgnoreCase(job.triageLabel)) {
                    job.score -= 4;
                } else if (job.score >= 0 && "avoid".equalsIgnoreCase(job.triageLabel)) {
                    job.score -= 8;
                }
            } catch (Exception e) {
                System.err.println("Triage failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableAtsSim) {
            try {
                LlmClient.AtsResult ats = client.atsSim(resume, job);
                job.atsFindings = ats.findings;
                job.atsSuggestions = ats.suggestions;
            } catch (Exception e) {
                System.err.println("ATS simulation failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableResumeVariants) {
            try {
                LlmClient.ResumeVariantResult variants = client.resumeVariants(resume, job);
                job.resumeVariantA = variants.variantA;
                job.resumeVariantB = variants.variantB;
            } catch (Exception e) {
                System.err.println("Resume variants failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableRedFlags) {
            try {
                job.redFlags = client.redFlags(job);
            } catch (Exception e) {
                System.err.println("Red flags analysis failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enablePortfolioBlurb) {
            try {
                job.portfolioBlurb = client.portfolioBlurb(resume, job);
            } catch (Exception e) {
                System.err.println("Portfolio blurb failed for " + job.id + ": " + e.getMessage());
            }
        }

        if (config.openai.enableMultiAgent) {
            try {
                LlmClient.MultiAgentResult result = client.multiAgentAssessment(resume, job);
                job.multiAgentNotes = result.notes;
                if (job.score >= 0 && result.score >= 0) {
                    job.score += result.score * 0.15;
                }
            } catch (Exception e) {
                System.err.println("Multi-agent assessment failed for " + job.id + ": " + e.getMessage());
            }
        }
    }

    private void applyLearningAdjustments(List<Job> jobs) {
        Path outcomesPath = Path.of("outcomes.json");
        if (!Files.exists(outcomesPath)) {
            return;
        }
        try {
            String json = Files.readString(outcomesPath, StandardCharsets.UTF_8);
            JsonNode root = Json.readTree(json);
            List<String> applied = readStringArray(root.path("applied"));
            List<String> interview = readStringArray(root.path("interview"));
            List<String> rejected = readStringArray(root.path("rejected"));

            for (Job job : jobs) {
                double boost = 0;
                String key = job.url != null ? job.url : "";
                if (applied.contains(key)) {
                    boost += 5;
                }
                if (interview.contains(key)) {
                    boost += 12;
                }
                if (rejected.contains(key)) {
                    boost -= 6;
                }
                String company = job.company != null ? job.company.toLowerCase(Locale.ROOT) : "";
                if (!company.isBlank()) {
                    if (applied.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(company))) {
                        boost += 3;
                    }
                    if (interview.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(company))) {
                        boost += 7;
                    }
                    if (rejected.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(company))) {
                        boost -= 4;
                    }
                }
                if (boost != 0 && job.score >= 0) {
                    job.score += boost;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to apply outcomes learning: " + e.getMessage());
        }
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private String truncateForEmbedding(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 3500 ? trimmed : trimmed.substring(0, 3500);
    }
}
