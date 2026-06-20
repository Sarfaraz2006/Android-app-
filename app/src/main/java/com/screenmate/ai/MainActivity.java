package com.screenmate.ai;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 700;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AiRepository aiRepository = new AiRepository();

    private SettingsStore settingsStore;
    private LinearLayout root;
    private LinearLayout messages;
    private EditText input;
    private TextView status;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean showingSettings = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsStore = new SettingsStore(this);
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
        buildChatUi();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    private void buildChatUi() {
        showingSettings = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 250));
        setContentView(root);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(12), dp(16), dp(8));
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("ScreenMate AI");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setTypeface(null, 1);
        topBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button settingsButton = smallButton("Settings");
        settingsButton.setOnClickListener(v -> buildSettingsUi());
        topBar.addView(settingsButton);

        status = new TextView(this);
        status.setPadding(dp(16), 0, dp(16), dp(8));
        status.setTextColor(Color.rgb(71, 85, 105));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        updateStatus();

        ScrollView scrollView = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(8), dp(12), dp(8));
        scrollView.addView(messages);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        addMessage("Assistant", "Namaste. Main chat, voice, screen read aur accessibility click commands ke liye ready hoon. Development ke liye Mock provider active hai.");

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(12), dp(4), dp(12), dp(4));
        actions.setGravity(Gravity.CENTER);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        Button mic = smallButton("Voice");
        mic.setOnClickListener(v -> startVoiceInput());
        actions.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));

        Button readScreen = smallButton("Read Screen");
        readScreen.setOnClickListener(v -> sendMessage("Read the current screen and summarize it."));
        actions.addView(readScreen, new LinearLayout.LayoutParams(0, -2, 1));

        Button accessibility = smallButton("Accessibility");
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        actions.addView(accessibility, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setPadding(dp(12), dp(8), dp(12), dp(12));
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        input = new EditText(this);
        input.setHint("Message or command, e.g. click Allow");
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button send = smallButton("Send");
        send.setOnClickListener(v -> sendMessage(input.getText().toString()));
        composer.addView(send);
    }

    private void buildSettingsUi() {
        showingSettings = true;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(247, 248, 250));
        setContentView(root);

        ProviderConfig config = settingsStore.getProviderConfig();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(22);
        title.setTypeface(null, 1);
        title.setTextColor(Color.rgb(15, 23, 42));
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button back = smallButton("Back");
        back.setOnClickListener(v -> buildChatUi());
        top.addView(back);

        TextView providerLabel = label("Provider");
        root.addView(providerLabel);
        Spinner provider = new Spinner(this);
        String[] providers = {
                ProviderConfig.PROVIDER_MOCK,
                ProviderConfig.PROVIDER_GEMINI,
                ProviderConfig.PROVIDER_OPENROUTER,
                ProviderConfig.PROVIDER_OPENAI_COMPATIBLE
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providers);
        provider.setAdapter(adapter);
        provider.setSelection(indexOf(providers, config.provider));
        root.addView(provider, new LinearLayout.LayoutParams(-1, -2));

        EditText model = field("Model", config.model, false);
        EditText endpoint = field("Endpoint", config.endpoint, false);
        EditText apiKey = field("API key", config.apiKey, true);

        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = providers[position];
                if (model.getText().toString().trim().isEmpty() || model.getText().toString().equals(config.model)) {
                    model.setText(SettingsStore.defaultModel(selected));
                }
                if (endpoint.getText().toString().trim().isEmpty() || endpoint.getText().toString().equals(config.endpoint)) {
                    endpoint.setText(SettingsStore.defaultEndpoint(selected));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        CheckBox voiceReplies = new CheckBox(this);
        voiceReplies.setText("Speak assistant replies");
        voiceReplies.setTextSize(16);
        voiceReplies.setChecked(settingsStore.voiceRepliesEnabled());
        root.addView(voiceReplies, new LinearLayout.LayoutParams(-1, -2));

        Button save = smallButton("Save Settings");
        save.setOnClickListener(v -> {
            settingsStore.saveProviderConfig(new ProviderConfig(
                    provider.getSelectedItem().toString(),
                    apiKey.getText().toString(),
                    model.getText().toString(),
                    endpoint.getText().toString()
            ));
            settingsStore.setVoiceRepliesEnabled(voiceReplies.isChecked());
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            buildChatUi();
        });
        root.addView(save, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("Mock mode needs no key. Gemini, OpenRouter, and OpenAI-compatible providers call their REST APIs directly from this prototype. For production, move API calls behind your backend or add stronger encrypted local storage.");
        note.setTextColor(Color.rgb(71, 85, 105));
        note.setTextSize(14);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);
    }

    private EditText field(String label, String value, boolean password) {
        root.addView(label(label));
        EditText field = new EditText(this);
        field.setText(value);
        field.setSingleLine(true);
        field.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(Color.rgb(51, 65, 85));
        label.setPadding(0, dp(14), 0, dp(4));
        return label;
    }

    private void sendMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            return;
        }
        if (input != null) {
            input.setText("");
            hideKeyboard(input);
        }

        addMessage("You", message);
        String clickTarget = parseClickTarget(message);
        if (clickTarget != null) {
            boolean clicked = AssistantAccessibilityService.clickText(clickTarget);
            String reply = clicked
                    ? "Clicked: " + clickTarget
                    : "Mujhe '" + clickTarget + "' visible accessibility tree me clickable item ke roop me nahi mila.";
            addAssistantReply(reply);
            return;
        }

        String screenContext = AssistantAccessibilityService.getVisibleText();
        addMessage("Assistant", "Thinking...");
        int thinkingIndex = messages.getChildCount() - 1;

        executor.execute(() -> {
            String reply;
            try {
                reply = aiRepository.ask(settingsStore.getProviderConfig(), message, screenContext);
            } catch (Exception error) {
                reply = "Request failed: " + error.getMessage();
            }
            String finalReply = reply;
            runOnUiThread(() -> replaceMessage(thinkingIndex, "Assistant", finalReply));
        });
    }

    private String parseClickTarget(String message) {
        String lower = message.toLowerCase(Locale.US).trim();
        if (lower.startsWith("click ")) {
            return message.substring(6).trim();
        }
        String marker = "par click";
        int index = lower.indexOf(marker);
        if (index > 0) {
            return message.substring(0, index).trim();
        }
        return null;
    }

    private void addAssistantReply(String reply) {
        addMessage("Assistant", reply);
        speak(reply);
    }

    private void addMessage(String author, String text) {
        TextView bubble = new TextView(this);
        bubble.setText(author + "\n" + text);
        bubble.setTextSize(15);
        bubble.setTextColor(Color.rgb(15, 23, 42));
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubble.setBackgroundColor("You".equals(author) ? Color.rgb(219, 234, 254) : Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        messages.addView(bubble, params);
    }

    private void replaceMessage(int index, String author, String text) {
        if (index < 0 || index >= messages.getChildCount()) {
            addMessage(author, text);
            speak(text);
            return;
        }
        TextView bubble = (TextView) messages.getChildAt(index);
        bubble.setText(author + "\n" + text);
        speak(text);
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition available nahi hai", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to ScreenMate");
        try {
            startActivityForResult(intent, 900);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Voice input app missing hai", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 900 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                sendMessage(results.get(0));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!showingSettings && status != null) {
            updateStatus();
        }
    }

    private void updateStatus() {
        ProviderConfig config = settingsStore.getProviderConfig();
        String accessibility = AssistantAccessibilityService.isRunning() ? "Accessibility on" : "Accessibility off";
        status.setText(config.provider + " / " + config.model + " / " + accessibility);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void speak(String text) {
        if (settingsStore.voiceRepliesEnabled() && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "screenmate-reply");
        }
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
