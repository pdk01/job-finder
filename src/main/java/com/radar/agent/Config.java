package com.radar.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    @JsonProperty("outputDir")
    public String outputDir = "site";

    @JsonProperty("resumePath")
    public String resumePath = "resume.txt";

    @JsonProperty("lookbackHours")
    public int lookbackHours = 24;

    @JsonProperty("experienceMinYears")
    public int experienceMinYears = 1;

    @JsonProperty("experienceMaxYears")
    public int experienceMaxYears = 4;

    @JsonProperty("maxResults")
    public int maxResults = 60;

    @JsonProperty("maxCoverLetters")
    public int maxCoverLetters = 10;

    @JsonProperty("coverLetterVariants")
    public int coverLetterVariants = 2;

    @JsonProperty("maxEnrichments")
    public int maxEnrichments = 10;

    @JsonProperty("maxContactLookups")
    public int maxContactLookups = 8;

    @JsonProperty("includeKeywords")
    public List<String> includeKeywords = new ArrayList<>(List.of(
        "software engineer",
        "software developer",
        "sde",
        "swe",
        "backend",
        "front end",
        "frontend",
        "full stack",
        "full-stack",
        "platform",
        "mobile",
        "android",
        "ios"
    ));

    @JsonProperty("excludeKeywords")
    public List<String> excludeKeywords = new ArrayList<>(List.of(
        "senior",
        "staff",
        "principal",
        "lead",
        "manager",
        "director",
        "architect"
    ));

    @JsonProperty("sources")
    public List<SourceConfig> sources = new ArrayList<>(List.of(
        new SourceConfig("remotive"),
        new SourceConfig("remoteok"),
        new SourceConfig("adzuna")
    ));

    @JsonProperty("openai")
    public OpenAiConfig openai = new OpenAiConfig();

    @JsonProperty("llmProvider")
    public String llmProvider = "openai";

    @JsonProperty("gemini")
    public GeminiConfig gemini = new GeminiConfig();

    @JsonProperty("adzuna")
    public AdzunaConfig adzuna = new AdzunaConfig();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceConfig {
        @JsonProperty("type")
        public String type;

        public SourceConfig() {}

        public SourceConfig(String type) {
            this.type = type;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiConfig {
        @JsonProperty("enabled")
        public boolean enabled = true;

        @JsonProperty("model")
        public String model = "gpt-4.1-mini";

        @JsonProperty("embeddingModel")
        public String embeddingModel = "text-embedding-3-small";

        @JsonProperty("rankerTemperature")
        public double rankerTemperature = 0.2;

        @JsonProperty("coverLetterTemperature")
        public double coverLetterTemperature = 0.7;

        @JsonProperty("analysisTemperature")
        public double analysisTemperature = 0.3;

        @JsonProperty("enableEmbeddings")
        public boolean enableEmbeddings = true;

        @JsonProperty("enableNormalization")
        public boolean enableNormalization = true;

        @JsonProperty("enableQualityScore")
        public boolean enableQualityScore = true;

        @JsonProperty("enableCompanyBrief")
        public boolean enableCompanyBrief = true;

        @JsonProperty("enableResumeGap")
        public boolean enableResumeGap = true;

        @JsonProperty("enableOutreach")
        public boolean enableOutreach = true;

        @JsonProperty("enableTriage")
        public boolean enableTriage = true;

        @JsonProperty("enableAtsSim")
        public boolean enableAtsSim = true;

        @JsonProperty("enableResumeVariants")
        public boolean enableResumeVariants = true;

        @JsonProperty("enablePortfolioBlurb")
        public boolean enablePortfolioBlurb = true;

        @JsonProperty("enableMultiAgent")
        public boolean enableMultiAgent = true;

        @JsonProperty("enableRedFlags")
        public boolean enableRedFlags = true;

        @JsonProperty("enableContactDiscovery")
        public boolean enableContactDiscovery = true;

        @JsonProperty("enablePublicContactSearch")
        public boolean enablePublicContactSearch = true;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdzunaConfig {
        @JsonProperty("enabled")
        public boolean enabled = true;

        @JsonProperty("appId")
        public String appId = com.radar.agent.util.Env.get("ADZUNA_APP_ID");

        @JsonProperty("appKey")
        public String appKey = com.radar.agent.util.Env.get("ADZUNA_APP_KEY");

        @JsonProperty("country")
        public String country = "in";

        @JsonProperty("query")
        public String query = "software engineer";

        @JsonProperty("resultsPerPage")
        public int resultsPerPage = 50;

        @JsonProperty("pages")
        public int pages = 2;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiConfig {
        @JsonProperty("enabled")
        public boolean enabled = true;

        @JsonProperty("model")
        public String model = "gemini-2.5-flash";

        @JsonProperty("embeddingModel")
        public String embeddingModel = "gemini-embedding-001";

        @JsonProperty("rankerTemperature")
        public double rankerTemperature = 0.2;

        @JsonProperty("coverLetterTemperature")
        public double coverLetterTemperature = 0.7;

        @JsonProperty("analysisTemperature")
        public double analysisTemperature = 0.3;

        @JsonProperty("minDelayMs")
        public long minDelayMs = 12000;
    }
}
