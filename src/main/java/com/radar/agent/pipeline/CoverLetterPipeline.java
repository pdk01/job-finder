package com.radar.agent.pipeline;

import com.radar.agent.Config;
import com.radar.agent.model.Job;
import com.radar.agent.llm.LlmClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CoverLetterPipeline {
    public void generate(Config config, LlmClient client, String resume, List<Job> jobs) {
        if (client == null || resume == null || resume.isBlank()) {
            return;
        }
        Path coverDir = Path.of(config.outputDir).resolve("cover_letters");
        try {
            Files.createDirectories(coverDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create cover letter directory", e);
        }

        int count = 0;
        for (Job job : jobs) {
            if (count >= config.maxCoverLetters) {
                break;
            }
            try {
                String letter = client.generateCoverLetter(resume, job);
                String filename = job.id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".txt";
                Path path = coverDir.resolve(filename);
                Files.writeString(path, letter, StandardCharsets.UTF_8);
                job.coverLetterPath = "cover_letters/" + filename;
                count++;

                if (config.coverLetterVariants > 0) {
                    List<String> variants = client.generateCoverLetterVariants(resume, job, config.coverLetterVariants);
                    int idx = 1;
                    for (String variant : variants) {
                        String vname = job.id.replaceAll("[^a-zA-Z0-9_-]", "_") + "-v" + idx + ".txt";
                        Path vpath = coverDir.resolve(vname);
                        Files.writeString(vpath, variant, StandardCharsets.UTF_8);
                        job.coverLetterVariants.add("cover_letters/" + vname);
                        idx++;
                    }
                }
            } catch (Exception e) {
                System.err.println("Cover letter failed for " + job.id + ": " + e.getMessage());
            }
        }
    }
}
