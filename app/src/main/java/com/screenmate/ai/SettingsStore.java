package com.screenmate.ai;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    private static final String PREFS = "screenmate_settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_VOICE_REPLY = "voice_reply";

    private final SharedPreferences prefs;

    SettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    ProviderConfig getProviderConfig() {
        String provider = prefs.getString(KEY_PROVIDER, ProviderConfig.PROVIDER_MOCK);
        String model = prefs.getString(KEY_MODEL, defaultModel(provider));
        String endpoint = prefs.getString(KEY_ENDPOINT, defaultEndpoint(provider));
        return new ProviderConfig(
                provider,
                prefs.getString(KEY_API_KEY, ""),
                model,
                endpoint
        );
    }

    void saveProviderConfig(ProviderConfig config) {
        prefs.edit()
                .putString(KEY_PROVIDER, config.provider)
                .putString(KEY_API_KEY, config.apiKey)
                .putString(KEY_MODEL, config.model)
                .putString(KEY_ENDPOINT, config.endpoint)
                .apply();
    }

    boolean voiceRepliesEnabled() {
        return prefs.getBoolean(KEY_VOICE_REPLY, true);
    }

    void setVoiceRepliesEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_REPLY, enabled).apply();
    }

    static String defaultModel(String provider) {
        if (ProviderConfig.PROVIDER_GEMINI.equals(provider)) {
            return "gemini-1.5-flash";
        }
        if (ProviderConfig.PROVIDER_OPENROUTER.equals(provider)) {
            return "google/gemini-2.0-flash-exp:free";
        }
        if (ProviderConfig.PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            return "gpt-4o-mini";
        }
        return "mock-assistant";
    }

    static String defaultEndpoint(String provider) {
        if (ProviderConfig.PROVIDER_GEMINI.equals(provider)) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }
        if (ProviderConfig.PROVIDER_OPENROUTER.equals(provider)) {
            return "https://openrouter.ai/api/v1/chat/completions";
        }
        if (ProviderConfig.PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            return "https://api.openai.com/v1/chat/completions";
        }
        return "";
    }
}
