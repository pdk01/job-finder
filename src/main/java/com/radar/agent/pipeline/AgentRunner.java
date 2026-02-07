package com.radar.agent.pipeline;

import com.radar.agent.Config;
import com.radar.agent.llm.LlmClient;
import com.radar.agent.llm.LlmFactory;
import com.radar.agent.model.Job;
import com.radar.agent.util.Env;
import com.radar.agent.util.Pdf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AgentRunner {
    private final SourcePipeline sourcePipeline = new SourcePipeline();
    private final FilterPipeline filterPipeline = new FilterPipeline();
    private final EnrichmentPipeline enrichmentPipeline = new EnrichmentPipeline();
    private final CoverLetterPipeline coverLetterPipeline = new CoverLetterPipeline();
    private final ContactPipeline contactPipeline = new ContactPipeline();
    private final OutputPipeline outputPipeline = new OutputPipeline();

    public void run(Config config) {
        Instant cutoff = Instant.now().minus(Duration.ofHours(config.lookbackHours));
        List<Job> jobs = sourcePipeline.fetch(config, cutoff);
        List<Job> scored = filterPipeline.filterAndScore(jobs, config);
        scored.sort(Comparator.comparingDouble((Job job) -> job.score).reversed());

        String openAiKey = Env.get("OPENAI_API_KEY");
        String geminiKey = Env.get("GEMINI_API_KEY");
        String resume = readResume(config.resumePath);
        LlmClient client = LlmFactory.create(config, openAiKey, geminiKey);
        boolean llmReady = client != null && resume != null && !resume.isBlank();

        if (llmReady) {
            scored = enrichmentPipeline.enrich(config, client, resume, scored);
        }

        List<Job> limited = scored.stream().limit(config.maxResults).collect(Collectors.toList());

        if (llmReady) {
            int enrichCount = 0;
            for (Job job : limited) {
                if (enrichCount >= config.maxEnrichments) {
                    break;
                }
                enrichmentPipeline.enrichJob(client, resume, job, config);
                enrichCount++;
            }
            coverLetterPipeline.generate(config, client, resume, limited);
        }

        contactPipeline.enrichContacts(config, limited);
        outputPipeline.write(Path.of(config.outputDir), limited);
    }

    private String readResume(String path) {
        try {
            if (path == null) {
                return null;
            }
            Path file = Path.of(path);
            if (!Files.exists(file)) {
                return null;
            }
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) {
                return Pdf.readText(file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Failed to read resume: " + e.getMessage());
            return null;
        }
    }
}