package com.screenmate.ai;

final class ProviderConfig {
    static final String PROVIDER_MOCK = "Mock";
    static final String PROVIDER_GEMINI = "Gemini";
    static final String PROVIDER_OPENROUTER = "OpenRouter";
    static final String PROVIDER_OPENAI_COMPATIBLE = "OpenAI Compatible";

    final String provider;
    final String apiKey;
    final String model;
    final String endpoint;

    ProviderConfig(String provider, String apiKey, String model, String endpoint) {
        this.provider = provider;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    boolean isMock() {
        return PROVIDER_MOCK.equals(provider);
    }
}
