package com.radar.agent.llm;

import com.radar.agent.model.Job;

import java.util.List;

public interface LlmClient {
    List<Job> rankJobs(String resume, List<Job> jobs, int maxResults);
    String generateCoverLetter(String resume, Job job);
    List<String> generateCoverLetterVariants(String resume, Job job, int variants);
    List<Double> embedding(String text);
    List<String> normalizeSkills(String resume, Job job);
    QualityResult assessQuality(Job job);
    String companyBrief(Job job);
    GapResult resumeGap(String resume, Job job);
    String outreachMessage(String resume, Job job);
    TriageResult triage(Job job, String resume);
    AtsResult atsSim(String resume, Job job);
    ResumeVariantResult resumeVariants(String resume, Job job);
    String redFlags(Job job);
    String portfolioBlurb(String resume, Job job);
    MultiAgentResult multiAgentAssessment(String resume, Job job);

    class QualityResult {
        public double score = -1;
        public String notes = "";
    }

    class GapResult {
        public String gaps = "";
        public String suggestions = "";
    }

    class TriageResult {
        public String label = "";
        public String reason = "";
    }

    class AtsResult {
        public String findings = "";
        public String suggestions = "";
    }

    class ResumeVariantResult {
        public String variantA = "";
        public String variantB = "";
    }

    class MultiAgentResult {
        public double score = -1;
        public String notes = "";
    }
}