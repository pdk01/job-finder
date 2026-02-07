package com.radar.agent.llm;

import com.radar.agent.Config;
import com.radar.agent.openai.OpenAiClient;

public final class LlmFactory {
    private LlmFactory() {}

    public static LlmClient create(Config config, String openAiKey, String geminiKey) {
        String provider = config.llmProvider == null ? "openai" : config.llmProvider.toLowerCase();
        switch (provider) {
            case "gemini" -> {
                if (config.gemini != null && config.gemini.enabled && geminiKey != null && !geminiKey.isBlank()) {
                    return new GeminiClient(geminiKey, config.gemini);
                }
                return null;
            }
            case "openai" -> {
                if (config.openai != null && config.openai.enabled && openAiKey != null && !openAiKey.isBlank()) {
                    return new OpenAiClient(openAiKey, config.openai);
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }
}