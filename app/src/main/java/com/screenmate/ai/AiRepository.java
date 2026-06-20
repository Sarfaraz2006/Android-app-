package com.screenmate.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AiRepository {
    String ask(ProviderConfig config, String userMessage, String screenContext) throws Exception {
        if (config.isMock()) {
            return mockReply(userMessage, screenContext);
        }
        if (config.apiKey.isEmpty()) {
            return "API key missing hai. Settings me provider ki API key add karein ya Mock mode use karein.";
        }
        if (ProviderConfig.PROVIDER_GEMINI.equals(config.provider)) {
            return askGemini(config, userMessage, screenContext);
        }
        return askChatCompletions(config, userMessage, screenContext);
    }

    private String mockReply(String userMessage, String screenContext) {
        String lower = userMessage.toLowerCase();
        if (lower.contains("read") || lower.contains("screen") || lower.contains("kya dikh")) {
            return screenContext.isEmpty()
                    ? "Accessibility service abhi active nahi hai ya screen text available nahi hai."
                    : "Screen par mujhe ye text mila:\n" + screenContext;
        }
        if (lower.startsWith("click ") || lower.contains("par click")) {
            return "Mock mode: click command samajh gaya. Live click ke liye Accessibility enable karein aur command me visible button/text ka naam dein.";
        }
        return "Mock reply: " + userMessage + "\n\nProvider switch karne ke liye Settings khol sakte hain.";
    }

    private String askGemini(ProviderConfig config, String userMessage, String screenContext) throws Exception {
        String base = config.endpoint.isEmpty() ? SettingsStore.defaultEndpoint(config.provider) : config.endpoint;
        String endpoint = base + "/models/" + config.model + ":generateContent?key=" + config.apiKey;

        JSONObject payload = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt(userMessage, screenContext)));
        content.put("parts", parts);
        contents.put(content);
        payload.put("contents", contents);

        JSONObject response = postJson(endpoint, payload, null);
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return "Gemini response empty tha.";
        }
        JSONObject first = candidates.getJSONObject(0).optJSONObject("content");
        JSONArray responseParts = first == null ? null : first.optJSONArray("parts");
        if (responseParts == null || responseParts.length() == 0) {
            return "Gemini response text nahi mila.";
        }
        return responseParts.getJSONObject(0).optString("text", "Gemini response text nahi mila.");
    }

    private String askChatCompletions(ProviderConfig config, String userMessage, String screenContext) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", config.model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", "You are ScreenMate AI, an Android assistant. Help the user through chat and voice. Use screen context when available. If a click is needed, mention the exact visible text to click."));
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", prompt(userMessage, screenContext)));
        payload.put("messages", messages);

        JSONObject response = postJson(config.endpoint, payload, config.apiKey);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return "Provider response empty tha.";
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        return message == null ? "Provider response text nahi mila." : message.optString("content", "Provider response text nahi mila.");
    }

    private String prompt(String userMessage, String screenContext) {
        return "User message:\n" + userMessage + "\n\nVisible screen context:\n" + (screenContext.isEmpty() ? "(none)" : screenContext);
    }

    private JSONObject postJson(String endpoint, JSONObject payload, String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(40000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            connection.setRequestProperty("HTTP-Referer", "https://screenmate.local");
            connection.setRequestProperty("X-Title", "ScreenMate AI");
        }

        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("API error " + code + ": " + body);
        }
        return new JSONObject(body);
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
