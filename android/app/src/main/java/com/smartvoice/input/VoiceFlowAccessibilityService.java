package com.smartvoice.input;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;

public class VoiceFlowAccessibilityService extends AccessibilityService {
    private static final String TAG = "VoiceFlowA11y";
    private static VoiceFlowAccessibilityService sInstance = null;
    private static AccessibilityNodeInfo sFocusedNode = null;

    private WindowManager windowManager;
    private FrameLayout overlayContainer;
    private FlowPillView pillView;
    private WindowManager.LayoutParams params;

    private MediaRecorder mediaRecorder = null;
    private File currentAudioFile = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isViewAttached = false;
    private boolean isFieldActive = false;
    private boolean hasCustomPosition = false;
    private boolean isDockedOnLeft = false;
    private int customX = -1;
    private int customY = -1;

    private int screenWidth = 1080;
    private int screenHeight = 2400;
    private int pillWidthPx;
    private int pillHeightPx;
    private int touchSlopPx;
    private int marginPx;
    private int minY;
    private int maxY;

    private final Runnable autoHideRunnable = this::hidePillIfIdle;

    public static VoiceFlowAccessibilityService getInstance() {
        return sInstance;
    }

    public static boolean isRunning() {
        return sInstance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.i(TAG, "VoiceFlow Accessibility Service connected");

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        marginPx = (int) (12 * dm.density);
        pillWidthPx = (int) (86 * dm.density);
        pillHeightPx = (int) (44 * dm.density);
        touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();
        minY = (int) (70 * dm.density);
        maxY = screenHeight - pillHeightPx - (int) (90 * dm.density);

        mainHandler.post(this::initOverlayView);
    }

    private void initOverlayView() {
        if (isViewAttached || windowManager == null) return;

        params = new WindowManager.LayoutParams(
            pillWidthPx,
            pillHeightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = screenWidth - pillWidthPx - marginPx;
        params.y = screenHeight / 2;
        isDockedOnLeft = false;

        overlayContainer = new FrameLayout(this);
        pillView = new FlowPillView(this);
        pillView.setState(FlowPillView.PillState.READY);

        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        overlayContainer.addView(pillView, pillLp);

        // Initially COMPLETELY HIDDEN - Only appears when an eligible input field is active!
        overlayContainer.setVisibility(View.GONE);
        overlayContainer.setAlpha(0f);
        overlayContainer.setScaleX(0.75f);
        overlayContainer.setScaleY(0.75f);

        // Magnetic Dragging & Responsive Push-To-Talk / Tap Interaction
        overlayContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float downRawX, downRawY;
            private long downTime = 0;
            private boolean isDragging = false;
            private boolean isHoldToTalkActive = false;
            private final Runnable holdToTalkRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isDragging && pillView != null && pillView.getState() == FlowPillView.PillState.READY) {
                        isHoldToTalkActive = true;
                        startVoiceRecording();
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        isDragging = false;
                        isHoldToTalkActive = false;
                        mainHandler.removeCallbacks(autoHideRunnable);

                        // If currently READY, schedule Push-to-Talk (Hold-to-Talk) after 200ms
                        if (pillView != null && pillView.getState() == FlowPillView.PillState.READY) {
                            mainHandler.postDelayed(holdToTalkRunnable, 200);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        float dist = (float) Math.hypot(deltaX, deltaY);

                        if (!isDragging && dist > touchSlopPx) {
                            isDragging = true;
                            mainHandler.removeCallbacks(holdToTalkRunnable);
                            if (isHoldToTalkActive) {
                                isHoldToTalkActive = false;
                                releaseMediaRecorder();
                                resetPillDimensions(FlowPillView.PillState.READY);
                            }
                        }

                        if (isDragging) {
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;
                            // Clamp inside screen bounds while dragging
                            params.x = Math.max(marginPx, Math.min(params.x, screenWidth - pillWidthPx - marginPx));
                            params.y = Math.max(minY, Math.min(params.y, maxY));
                            try {
                                windowManager.updateViewLayout(overlayContainer, params);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(holdToTalkRunnable);
                        long duration = System.currentTimeMillis() - downTime;

                        if (isDragging) {
                            // Magnetic Snap to Left or Right Screen Edge
                            int pillCenterX = params.x + (pillWidthPx / 2);
                            if (pillCenterX < screenWidth / 2) {
                                isDockedOnLeft = true;
                                params.x = marginPx;
                            } else {
                                isDockedOnLeft = false;
                                params.x = screenWidth - pillWidthPx - marginPx;
                            }

                            params.y = Math.max(minY, Math.min(params.y, maxY));
                            hasCustomPosition = true;
                            customX = params.x;
                            customY = params.y;

                            try {
                                windowManager.updateViewLayout(overlayContainer, params);
                            } catch (Exception ignored) {}

                            vibrate(25);
                        } else if (isHoldToTalkActive) {
                            // Release after Push-to-Talk -> Stop and transcribe immediately!
                            isHoldToTalkActive = false;
                            if (pillView != null && pillView.getState() == FlowPillView.PillState.RECORDING) {
                                stopVoiceRecordingAndProcess();
                            }
                        } else if (duration < 350) {
                            // Quick Tap -> Toggle voice dictation (tap to start, tap to stop)
                            handlePillTap();
                        }

                        if (!isFieldActive && pillView != null && pillView.getState() == FlowPillView.PillState.READY) {
                            scheduleAutoHide(3000);
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(overlayContainer, params);
            isViewAttached = true;
            Log.i(TAG, "Floating Accessibility Overlay added (hidden by default)");
        } catch (Exception e) {
            Log.e(TAG, "Failed adding overlay view: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();
        AccessibilityNodeInfo source = event.getSource();

        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (source != null && isEligibleInputNode(source)) {
                updateFocusedNode(source);
                isFieldActive = true;
                mainHandler.removeCallbacks(autoHideRunnable);
                showPillAtFocusedField(source);
            } else if (source != null && !isEligibleInputNode(source)) {
                // Focus shifted to a non-eligible or search view
                isFieldActive = false;
                scheduleAutoHide(600);
            }
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null && isEligibleInputNode(focused)) {
                    updateFocusedNode(focused);
                    isFieldActive = true;
                    mainHandler.removeCallbacks(autoHideRunnable);
                    showPillAtFocusedField(focused);
                    return;
                }
            }
            // Window changed and no eligible input is active -> auto-hide
            isFieldActive = false;
            scheduleAutoHide(1000);
        }
    }

    /**
     * Whitelist: Chatting apps, Social Media, AI Bots, and Notes/Email only.
     * Explicitly blocks Settings, Play Store, YouTube, and all search bars!
     */
    private boolean isEligibleInputNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (!node.isEditable()) return false;

        CharSequence pkg = node.getPackageName();
        if (pkg == null) return false;
        String packageName = pkg.toString().toLowerCase();

        // 1. Explicitly Block Search/System apps
        if (packageName.contains("settings")
                || packageName.contains("vending") // Play Store
                || packageName.contains("youtube")
                || packageName.contains("googlequicksearchbox")
                || packageName.contains("launcher")
                || packageName.contains("systemui")) {
            return false;
        }

        // 2. Explicitly Block Search Fields across all apps
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String vLower = viewId.toLowerCase();
            if (vLower.contains("search") || vLower.contains("query") || vLower.contains("find")) {
                return false;
            }
        }

        CharSequence hint = node.getHintText();
        if (hint != null) {
            String hLower = hint.toString().toLowerCase();
            if (hLower.contains("search") || hLower.contains("khoj") || hLower.contains("खोज")) {
                return false;
            }
        }

        // 3. Match against Whitelisted App Categories
        return isWhitelistedPackage(packageName);
    }

    private boolean isWhitelistedPackage(String pkg) {
        // Chatting & Messaging
        if (pkg.contains("whatsapp")) return true;
        if (pkg.contains("telegram") || pkg.contains("challegram")) return true;
        if (pkg.contains("securesms")) return true; // Signal
        if (pkg.contains("messaging") || pkg.contains("mms")) return true; // SMS & Google Messages

        // Social Media
        if (pkg.contains("instagram")) return true;
        if (pkg.contains("katana") || pkg.contains("orca") || pkg.contains("facebook")) return true;
        if (pkg.contains("twitter") || pkg.contains("x.android")) return true;
        if (pkg.contains("linkedin")) return true;
        if (pkg.contains("snapchat")) return true;
        if (pkg.contains("discord")) return true;
        if (pkg.contains("reddit")) return true;
        if (pkg.contains("barcelona")) return true; // Threads

        // AI Chatbots
        if (pkg.contains("openai") || pkg.contains("chatgpt") || pkg.contains("claude")
                || pkg.contains("perplexity") || pkg.contains("copilot") || pkg.contains("poe")
                || pkg.contains("bard")) {
            return true;
        }

        // Notes & Communication
        if (pkg.contains("gm") || pkg.contains("outlook") || pkg.contains("keep")
                || pkg.contains("notion") || pkg.contains("slack") || pkg.contains("notes")
                || pkg.contains("docs.editors.docs")) {
            return true;
        }

        // VoiceFlow itself
        if (pkg.contains("smartvoice")) return true;

        return false;
    }

    /**
     * Shows the sleek pill right next to the active input field with Zero-Overflow boundary check
     */
    private void showPillAtFocusedField(AccessibilityNodeInfo node) {
        if (overlayContainer == null || !isViewAttached) return;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        mainHandler.post(() -> {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            pillWidthPx = (int) (86 * dm.density);
            pillHeightPx = (int) (44 * dm.density);
            params.width = pillWidthPx;
            params.height = pillHeightPx;

            if (!hasCustomPosition && bounds.width() > 0 && bounds.height() > 0) {
                // By default anchor to the right edge, aligned vertically with input field
                isDockedOnLeft = false;
                params.x = screenWidth - pillWidthPx - marginPx;
                params.y = bounds.centerY() - (pillHeightPx / 2);
                params.y = Math.max(minY, Math.min(params.y, maxY));
            } else if (hasCustomPosition && customX >= 0) {
                if (isDockedOnLeft) {
                    params.x = marginPx;
                } else {
                    params.x = screenWidth - pillWidthPx - marginPx;
                }
                params.y = Math.max(minY, Math.min(customY, maxY));
            }

            try {
                windowManager.updateViewLayout(overlayContainer, params);
            } catch (Exception ignored) {}

            // Smooth luxury spring entrance
            if (overlayContainer.getVisibility() != View.VISIBLE) {
                overlayContainer.setVisibility(View.VISIBLE);
                overlayContainer.animate()
                    .alpha(1f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
            } else {
                overlayContainer.animate().alpha(1f).setDuration(150).start();
            }

            if (pillView != null && pillView.getState() != FlowPillView.PillState.RECORDING
                    && pillView.getState() != FlowPillView.PillState.PROCESSING) {
                pillView.setState(FlowPillView.PillState.READY);
            }
        });
    }

    /**
     * Recalculates position dynamically when pill expands, ensuring it NEVER overflows the screen
     */
    private void updatePillWidth(int targetWidthPx) {
        pillWidthPx = targetWidthPx;
        params.width = pillWidthPx;

        if (isDockedOnLeft) {
            params.x = marginPx;
        } else {
            // Anchor to right edge so expansion goes inward to the left, NEVER off-screen!
            params.x = screenWidth - pillWidthPx - marginPx;
        }

        params.y = Math.max(minY, Math.min(params.y, maxY));
        try {
            windowManager.updateViewLayout(overlayContainer, params);
        } catch (Exception ignored) {}
    }

    private void scheduleAutoHide(long delayMs) {
        mainHandler.removeCallbacks(autoHideRunnable);
        mainHandler.postDelayed(autoHideRunnable, delayMs);
    }

    private void hidePillIfIdle() {
        if (overlayContainer == null || !isViewAttached) return;
        if (pillView != null && (pillView.getState() == FlowPillView.PillState.RECORDING || pillView.getState() == FlowPillView.PillState.PROCESSING)) {
            return;
        }

        overlayContainer.animate()
            .alpha(0f)
            .scaleX(0.75f)
            .scaleY(0.75f)
            .setDuration(200)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isFieldActive && overlayContainer != null) {
                        overlayContainer.setVisibility(View.GONE);
                    }
                }
            })
            .start();
    }

    private synchronized void updateFocusedNode(AccessibilityNodeInfo node) {
        if (sFocusedNode != null && !sFocusedNode.equals(node)) {
            try {
                sFocusedNode.recycle();
            } catch (Exception ignored) {}
        }
        sFocusedNode = node;
    }

    private void handlePillTap() {
        if (pillView == null) return;

        switch (pillView.getState()) {
            case READY:
                startVoiceRecording();
                break;
            case RECORDING:
                stopVoiceRecordingAndProcess();
                break;
            case PROCESSING:
                Toast.makeText(this, "Polishing your dictation...", Toast.LENGTH_SHORT).show();
                break;
            case SUCCESS:
                pillView.setState(FlowPillView.PillState.READY);
                break;
        }
    }

    private void startVoiceRecording() {
        try {
            currentAudioFile = new File(getCacheDir(), "flow_dictation.m4a");
            if (currentAudioFile.exists()) {
                currentAudioFile.delete();
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            // Ultra-optimized 16 kHz voice audio (4x smaller & faster upload!)
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setAudioEncodingBitRate(24000);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setOutputFile(currentAudioFile.getAbsolutePath());

            mediaRecorder.prepare();
            mediaRecorder.start();

            // Expand pill safely with zero overflow
            DisplayMetrics dm = getResources().getDisplayMetrics();
            updatePillWidth((int) (112 * dm.density));

            pillView.setState(FlowPillView.PillState.RECORDING);
            vibrate(35);
        } catch (Exception e) {
            Log.e(TAG, "Audio recording failed to start: " + e.getMessage());
            Toast.makeText(this, "Microphone permission required.", Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
            resetPillDimensions(FlowPillView.PillState.READY);
        }
    }

    private void stopVoiceRecordingAndProcess() {
        vibrate(35);

        // Update to processing state safely with zero overflow
        DisplayMetrics dm = getResources().getDisplayMetrics();
        updatePillWidth((int) (118 * dm.density));

        pillView.setState(FlowPillView.PillState.PROCESSING);
        releaseMediaRecorder();

        if (currentAudioFile == null || !currentAudioFile.exists() || currentAudioFile.length() == 0) {
            Toast.makeText(this, "No voice detected.", Toast.LENGTH_SHORT).show();
            resetPillDimensions(FlowPillView.PillState.READY);
            return;
        }

        // Send to optimized Gemini engine
        VoiceFlowGeminiEngine.transcribeAudioFile(this, currentAudioFile, new VoiceFlowGeminiEngine.TranscriptionCallback() {
            @Override
            public void onSuccess(String resultText) {
                mainHandler.post(() -> {
                    // Type clean text into WhatsApp without placeholders
                    boolean typed = insertTextIntoCurrentField(VoiceFlowAccessibilityService.this, resultText);
                    vibrateSuccessPattern();

                    pillView.setState(FlowPillView.PillState.SUCCESS);

                    if (!typed) {
                        Toast.makeText(VoiceFlowAccessibilityService.this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }

                    mainHandler.postDelayed(() -> {
                        resetPillDimensions(FlowPillView.PillState.READY);
                        if (!isFieldActive) {
                            scheduleAutoHide(800);
                        }
                    }, 1200);
                });
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> {
                    Toast.makeText(VoiceFlowAccessibilityService.this, errorMessage, Toast.LENGTH_SHORT).show();
                    vibrate(100);
                    resetPillDimensions(FlowPillView.PillState.READY);
                });
            }
        });
    }

    private void resetPillDimensions(FlowPillView.PillState targetState) {
        if (overlayContainer == null || pillView == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        updatePillWidth((int) (86 * dm.density));
        pillView.setState(targetState);
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {}
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
    }

    /**
     * Checks whether an existing text in a node is merely a placeholder / hint like "Message" or "मैसेज"
     */
    private static boolean isPlaceholderOrHint(CharSequence text, CharSequence hint) {
        if (text == null || text.length() == 0) return true;
        String s = text.toString().trim();
        if (s.isEmpty()) return true;

        if (hint != null) {
            String h = hint.toString().trim();
            if (!h.isEmpty() && s.equalsIgnoreCase(h)) return true;
        }

        String lower = s.toLowerCase();
        return lower.equals("message") || lower.equals("type a message") || lower.equals("मैसेज")
                || lower.equals("संदेश") || lower.equals("enter message") || lower.equals("write a message")
                || lower.equals("chat") || lower.equals("send a message") || lower.equals("reply");
    }

    public static boolean insertTextIntoCurrentField(Context context, String textToInsert) {
        if (textToInsert == null || textToInsert.trim().isEmpty()) {
            return false;
        }

        final String cleanText = textToInsert.trim();

        // 1. Auto-Copy to Clipboard ONLY if enabled in settings
        SharedPreferences prefs = context.getSharedPreferences(VoiceFlowGeminiEngine.PREFS_NAME, Context.MODE_PRIVATE);
        boolean shouldAutoCopy = prefs.getBoolean(VoiceFlowGeminiEngine.KEY_AUTO_COPY, true);

        if (shouldAutoCopy) {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("VoiceFlow", cleanText);
                    clipboard.setPrimaryClip(clip);
                }
            } catch (Exception ignored) {}
        }

        // 2. Direct injection into active focused node without prepending placeholder text
        if (sInstance != null) {
            try {
                AccessibilityNodeInfo target = sFocusedNode;
                if (target == null) {
                    AccessibilityNodeInfo root = sInstance.getRootInActiveWindow();
                    if (root != null) {
                        target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    }
                }

                if (target != null) {
                    CharSequence existing = target.getText();
                    CharSequence hint = target.getHintText();

                    String finalText;
                    // Filter out placeholder hints like "Message" or "मैसेज"
                    if (isPlaceholderOrHint(existing, hint)) {
                        finalText = cleanText;
                    } else {
                        finalText = existing.toString() + " " + cleanText;
                    }

                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText);
                    boolean directSuccess = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

                    if (directSuccess) return true;

                    // Fallback to PASTE
                    return target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Insertion error: " + e.getMessage());
            }
        }

        return false;
    }

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception ignored) {}
    }

    private void vibrateSuccessPattern() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] pattern = {0, 25, 45, 25};
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(50);
                }
            }
        } catch (Exception ignored) {}
    }

    private void openMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "VoiceFlow Accessibility Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        sFocusedNode = null;
        releaseMediaRecorder();
        if (overlayContainer != null && windowManager != null && isViewAttached) {
            try {
                windowManager.removeView(overlayContainer);
            } catch (Exception ignored) {}
            isViewAttached = false;
        }
        Log.i(TAG, "VoiceFlow Accessibility Service destroyed");
    }
}
