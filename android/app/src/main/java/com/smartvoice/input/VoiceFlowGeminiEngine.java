package com.smartvoice.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VoiceFlowGeminiEngine {
    private static final String TAG = "VoiceFlowGemini";
    public static final String PREFS_NAME = "VoiceFlowPrefs";
    public static final String KEY_API_KEY = "gemini_api_key";
    public static final String KEY_MODEL = "gemini_model";
    public static final String KEY_LANGUAGE_STYLE = "language_style";
    public static final String KEY_AUTO_COPY = "auto_copy_enabled";

    // Fallback key (Empty by default; synced securely from .env.local or UI Settings)
    public static final String DEFAULT_FALLBACK_KEY = "";

    public static final String DEFAULT_MODEL = "gemini-flash-lite-latest";
    public static final String FALLBACK_MODEL = "gemini-3.5-flash-lite";

    static {
        // Enable persistent HTTP keep-alive connection pooling
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "5");
    }

    public interface TranscriptionCallback {
        void onSuccess(String resultText);
        void onError(String errorMessage);
    }

    /**
     * Ultra-fast concise system instructions for lowest Time-To-First-Token (TTFT)
     * and aggressive removal of speech fillers (hmm, haan, um, uh, matlab, actually, pauses).
     */
    public static String getSystemPrompt(String style) {
        if ("hindi".equalsIgnoreCase(style)) {
            return "Voice dictation engine. Output strictly in natural Devanagari Hindi (हिंदी). Aggressively remove all verbal fillers, hesitation sounds and pauses (hmm, haan, um, uh, aa, matlab, actually, like). Resolve mid-speech corrections to the final intended meaning. Output ONLY the raw polished transcript. No quotes, explanations or extra words.";
        } else if ("english".equalsIgnoreCase(style)) {
            return "Voice dictation engine. Output strictly in polished English. Aggressively remove all verbal fillers, hesitation sounds and pauses (hmm, ha, um, uh, like, you know, actually, basically). Resolve mid-speech corrections to the final intended meaning. Output ONLY the raw polished transcript. No quotes, explanations or extra words.";
        } else {
            // Default: Conversational Roman Hinglish (WhatsApp chatting style)
            return "Voice dictation engine. Output strictly in natural conversational Roman Hinglish (English alphabet for Hindi, e.g. 'kal subah 10 baje milte hain na'). Aggressively remove all verbal fillers, hesitation sounds and pauses (hmm, haan, ha, um, uh, aa, matlab, actually, like). Resolve mid-speech corrections to the final intended meaning. Output ONLY the raw polished transcript. No quotes, explanations or extra words.";
        }
    }

    public static void transcribeAudioFile(Context context, File audioFile, TranscriptionCallback callback) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String apiKey = prefs.getString(KEY_API_KEY, "");
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = DEFAULT_FALLBACK_KEY;
                }

                String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL);
                if (model == null || model.trim().isEmpty() || model.contains("3.1") || model.contains("2.0") || model.contains("2.5") || model.contains("1.5")) {
                    model = DEFAULT_MODEL;
                }

                String style = prefs.getString(KEY_LANGUAGE_STYLE, "hinglish");

                if (!audioFile.exists() || audioFile.length() == 0) {
                    callback.onError("Audio recording was empty. Please try speaking again.");
                    return;
                }

                // Fast read into buffer
                byte[] audioBytes = new byte[(int) audioFile.length()];
                try (FileInputStream fis = new FileInputStream(audioFile)) {
                    int offset = 0;
                    while (offset < audioBytes.length) {
                        int read = fis.read(audioBytes, offset, audioBytes.length - offset);
                        if (read == -1) break;
                        offset += read;
                    }
                }

                String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);
                String systemPrompt = getSystemPrompt(style);

                long startTime = System.currentTimeMillis();

                // Ultra-Fast Call with primary model (gemini-flash-lite-latest: ~1s)
                String resultText = executeFastGeminiCall(apiKey, model, base64Audio, systemPrompt);

                // Fallback to gemini-3.5-flash-lite if primary had an issue
                if (resultText == null || resultText.trim().isEmpty()) {
                    Log.w(TAG, "Primary model failed or empty, retrying with fallback: " + FALLBACK_MODEL);
                    resultText = executeFastGeminiCall(apiKey, FALLBACK_MODEL, base64Audio, systemPrompt);
                }

                long duration = System.currentTimeMillis() - startTime;
                Log.i(TAG, "Transcription completed in " + duration + "ms with model: " + model);

                if (resultText != null && !resultText.trim().isEmpty()) {
                    String clean = cleanResponse(resultText);
                    callback.onSuccess(clean);
                } else {
                    callback.onError("Could not hear speech clearly. Please try again.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed: " + e.getMessage(), e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Network error.");
            }
        }).start();
    }

    private static String executeFastGeminiCall(String apiKey, String model, String base64Audio, String systemPrompt) {
        HttpURLConnection conn = null;
        try {
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey.trim();
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            // Optimized lightweight JSON payload
            JSONObject root = new JSONObject();

            JSONObject sysInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", systemPrompt);
            sysParts.put(sysPart);
            sysInstruction.put("parts", sysParts);
            root.put("systemInstruction", sysInstruction);

            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("role", "user");

            JSONArray parts = new JSONArray();
            JSONObject audioPart = new JSONObject();
            JSONObject inlineData = new JSONObject();
            inlineData.put("mimeType", "audio/mp4");
            inlineData.put("data", base64Audio);
            audioPart.put("inlineData", inlineData);
            parts.put(audioPart);

            content.put("parts", parts);
            contents.put(content);
            root.put("contents", contents);

            // Greedy decoding: temperature 0.0 + maxOutputTokens 200 for fastest completion
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.0);
            genConfig.put("maxOutputTokens", 200);
            root.put("generationConfig", genConfig);

            byte[] postData = root.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(postData.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            if (responseCode >= 200 && responseCode < 300) {
                JSONObject responseJson = new JSONObject(sb.toString());
                JSONArray candidates = responseJson.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject firstCand = candidates.getJSONObject(0);
                    JSONObject contentObj = firstCand.optJSONObject("content");
                    if (contentObj != null) {
                        JSONArray candParts = contentObj.optJSONArray("parts");
                        if (candParts != null && candParts.length() > 0) {
                            return candParts.getJSONObject(0).optString("text", "");
                        }
                    }
                }
            } else {
                Log.e(TAG, "Gemini API error code: " + responseCode + " " + sb.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Gemini call exception: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String cleanResponse(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        // Remove surrounding quotes, backticks or asterisks
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")) || (text.startsWith("`") && text.endsWith("`"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        // Strip any stray conversational or AI artifact prefixes
        text = text.replaceFirst("^(?i)(message|transcript|output|text|spoken\\s*text|dictation|result|संदेश|मैसेज)[:\\s-]+", "").trim();
        return text;
    }
}
