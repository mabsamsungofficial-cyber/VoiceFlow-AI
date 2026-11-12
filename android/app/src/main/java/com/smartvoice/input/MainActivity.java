package com.smartvoice.input;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.List;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FloatingOverlayPlugin.class);
        super.onCreate(savedInstanceState);

        // Pre-populate SharedPreferences with default key so background engine works out-of-the-box
        SharedPreferences prefs = getSharedPreferences(VoiceFlowGeminiEngine.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(VoiceFlowGeminiEngine.KEY_API_KEY) || prefs.getString(VoiceFlowGeminiEngine.KEY_API_KEY, "").isEmpty()) {
            prefs.edit().putString(VoiceFlowGeminiEngine.KEY_API_KEY, VoiceFlowGeminiEngine.DEFAULT_FALLBACK_KEY).apply();
        }

        // Ensure Microphone runtime permission is requested upfront
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1001);
            }
        }
    }

    @CapacitorPlugin(name = "FloatingOverlay")
    public static class FloatingOverlayPlugin extends Plugin {

        @PluginMethod
        public void checkPermission(PluginCall call) {
            boolean canDraw = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                canDraw = Settings.canDrawOverlays(getContext());
            }
            JSObject ret = new JSObject();
            ret.put("granted", canDraw);
            call.resolve(ret);
        }

        @PluginMethod
        public void requestPermission(PluginCall call) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getContext())) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            call.resolve();
        }

        @PluginMethod
        public void checkAccessibilityPermission(PluginCall call) {
            boolean isEnabled = VoiceFlowAccessibilityService.isRunning();

            if (!isEnabled) {
                AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
                if (am != null) {
                    List<AccessibilityServiceInfo> runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                    if (runningServices != null) {
                        for (AccessibilityServiceInfo service : runningServices) {
                            if (service.getId() != null && service.getId().contains(getContext().getPackageName())) {
                                isEnabled = true;
                                break;
                            }
                        }
                    }
                }
            }

            JSObject ret = new JSObject();
            ret.put("granted", isEnabled);
            call.resolve(ret);
        }

        @PluginMethod
        public void requestAccessibilityPermission(PluginCall call) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        }

        @PluginMethod
        public void syncSettings(PluginCall call) {
            String apiKey = call.getString("apiKey", "");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                apiKey = VoiceFlowGeminiEngine.DEFAULT_FALLBACK_KEY;
            }
            String model = call.getString("model", VoiceFlowGeminiEngine.DEFAULT_MODEL);
            if (model == null || model.contains("3.1") || model.contains("2.0") || model.contains("2.5") || model.contains("1.5")) {
                model = VoiceFlowGeminiEngine.DEFAULT_MODEL;
            }
            String languageStyle = call.getString("languageStyle", "hinglish");
            boolean autoCopy = call.getBoolean("autoCopy", true);

            SharedPreferences prefs = getContext().getSharedPreferences(VoiceFlowGeminiEngine.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(VoiceFlowGeminiEngine.KEY_API_KEY, apiKey);
            editor.putString(VoiceFlowGeminiEngine.KEY_MODEL, model);
            editor.putString(VoiceFlowGeminiEngine.KEY_LANGUAGE_STYLE, languageStyle);
            editor.putBoolean(VoiceFlowGeminiEngine.KEY_AUTO_COPY, autoCopy);
            editor.apply();

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        }

        @PluginMethod
        public void getServiceStatus(PluginCall call) {
            JSObject ret = new JSObject();
            ret.put("overlayActive", VoiceFlowAccessibilityService.isRunning());
            ret.put("accessibilityActive", VoiceFlowAccessibilityService.isRunning());
            call.resolve(ret);
        }

        @PluginMethod
        public void startService(PluginCall call) {
            // Background is managed automatically by VoiceFlowAccessibilityService
            call.resolve();
        }

        @PluginMethod
        public void stopService(PluginCall call) {
            call.resolve();
        }
    }
}
