import React, { useState, useRef, useEffect } from 'react';
import {
  Mic,
  Settings,
  Copy,
  Check,
  X,
  History,
  Trash2,
  Share2,
  Edit3,
  Eye,
  EyeOff,
  RefreshCw,
  Sliders,
  Sparkles
} from 'lucide-react';
import { CONFIG } from './config';
import { getSystemInstruction, getUserPromptForAudio, buildPromptExpertPrompt } from './promptHelper';

// Detect audio mimeType across devices
const getSupportedMimeType = () => {
  if (typeof MediaRecorder === 'undefined') return '';
  const types = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/aac', 'audio/ogg'];
  for (const t of types) {
    if (MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(t)) return t;
  }
  return '';
};

// Blob to Base64
const blobToBase64 = (blob) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
};

export default function App() {
  const [status, setStatus] = useState('idle'); // 'idle' | 'listening' | 'processing'
  const [outputText, setOutputText] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [copied, setCopied] = useState(false);
  const [isEditing, setIsEditing] = useState(false);

  // Model & Preferences (Fast 3.1 Flash Lite default)
  const [selectedModel, setSelectedModel] = useState(() => {
    const stored = localStorage.getItem('smart_voice_model');
    if (!stored || stored.includes('2.0') || stored.includes('2.5') || stored.includes('1.5') || stored.includes('3.8')) {
      try { localStorage.setItem('smart_voice_model', 'gemini-3.1-flash-lite'); } catch (e) {}
      return 'gemini-3.1-flash-lite';
    }
    return stored;
  });

  const [languageStyle, setLanguageStyle] = useState(() => {
    return localStorage.getItem('smart_voice_language_style') || CONFIG.DEFAULT_LANGUAGE_STYLE || 'hinglish';
  });

  const [autoCopy, setAutoCopy] = useState(() => {
    const val = localStorage.getItem('smart_voice_auto_copy');
    return val !== null ? val === 'true' : true;
  });

  // API Key
  const [customApiKey, setCustomApiKey] = useState(() => {
    const stored = localStorage.getItem('smart_voice_api_key');
    if (stored !== null && stored.trim() !== '') return stored;
    return CONFIG.DEFAULT_GEMINI_API_KEY || '';
  });
  const [showApiKeyText, setShowApiKeyText] = useState(false);
  const [keySavedToast, setKeySavedToast] = useState(false);

  // History & Drawers
  const [showHistory, setShowHistory] = useState(false);
  const [historyCopiedId, setHistoryCopiedId] = useState(null);
  const [convertingPromptId, setConvertingPromptId] = useState(null);
  const [selectedHistoryItem, setSelectedHistoryItem] = useState(null); // active item for detail & prompt modal
  const [detailCopied, setDetailCopied] = useState(false);
  const [promptCopied, setPromptCopied] = useState(false);
  const [promptError, setPromptError] = useState('');
  const [draftHistory, setDraftHistory] = useState(() => {
    try {
      const saved = localStorage.getItem('smart_voice_drafts');
      return saved ? JSON.parse(saved) : [];
    } catch (e) {
      return [];
    }
  });

  const [showSettings, setShowSettings] = useState(false);
  const [overlayEnabled, setOverlayEnabled] = useState(() => {
    return localStorage.getItem('smart_voice_overlay_enabled') === 'true';
  });
  const [overlayPos, setOverlayPos] = useState({ x: 24, y: 120 });

  // Refs
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const streamRef = useRef(null);
  const waveCanvasRef = useRef(null);
  const analyserRef = useRef(null);
  const audioLevelRef = useRef(0.2);
  const draggingRef = useRef(false);
  const dragOffsetRef = useRef({ x: 0, y: 0 });
  const movedRef = useRef(false);

  // Persistence
  useEffect(() => {
    try { localStorage.setItem('smart_voice_model', selectedModel); } catch (e) {}
  }, [selectedModel]);

  useEffect(() => {
    try { localStorage.setItem('smart_voice_language_style', languageStyle); } catch (e) {}
  }, [languageStyle]);

  useEffect(() => {
    try { localStorage.setItem('smart_voice_auto_copy', String(autoCopy)); } catch (e) {}
  }, [autoCopy]);

  useEffect(() => {
    try { localStorage.setItem('smart_voice_overlay_enabled', String(overlayEnabled)); } catch (e) {}
  }, [overlayEnabled]);

  // Windows Electron Global Hotkey Integration (Ctrl+Shift+Space / Alt+V)
  useEffect(() => {
    if (window.electronAPI && window.electronAPI.onHotkeyToggleMic) {
      window.electronAPI.onHotkeyToggleMic(() => {
        toggleVoiceAction();
      });
    }
  }, []);

  // Android System-Wide Floating Service Toggle Handler
  const handleToggleOverlay = async () => {
    const nextState = !overlayEnabled;
    setOverlayEnabled(nextState);

    // If running in Capacitor Android Native App
    if (window.Capacitor?.Plugins?.FloatingOverlay) {
      try {
        if (nextState) {
          const perm = await window.Capacitor.Plugins.FloatingOverlay.checkPermission();
          if (!perm.granted) {
            await window.Capacitor.Plugins.FloatingOverlay.requestPermission();
          }
          await window.Capacitor.Plugins.FloatingOverlay.startService();
        } else {
          await window.Capacitor.Plugins.FloatingOverlay.stopService();
        }
      } catch (err) {
        console.warn('Native overlay service notice:', err);
      }
    }
  };

  const saveDraftToHistory = (text) => {
    if (!text || !text.trim()) return;
    const now = new Date();
    const formattedTime = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const newEntry = {
      id: Date.now(),
      text: text.trim(),
      timestamp: formattedTime
    };
    setDraftHistory((prev) => {
      const updated = [newEntry, ...prev].slice(0, 50);
      try { localStorage.setItem('smart_voice_drafts', JSON.stringify(updated)); } catch (e) {}
      return updated;
    });
  };

  const deleteHistoryItem = (id) => {
    setDraftHistory((prev) => {
      const updated = prev.filter((item) => item.id !== id);
      try { localStorage.setItem('smart_voice_drafts', JSON.stringify(updated)); } catch (e) {}
      return updated;
    });
  };

  const clearAllHistory = () => {
    setDraftHistory([]);
    try { localStorage.removeItem('smart_voice_drafts'); } catch (e) {}
  };

  // AI Prompt Converter (Takes spoken draft and converts into a structured AI Prompt)
  const handleConvertToPrompt = async (item, forceRegenerate = false) => {
    if (item.aiPrompt && !forceRegenerate) {
      return;
    }

    const effectiveApiKey = (customApiKey || CONFIG.DEFAULT_GEMINI_API_KEY || '').trim();
    if (!effectiveApiKey) {
      setPromptError('API key missing. Enter key in Settings.');
      return;
    }

    setConvertingPromptId(item.id);
    setPromptError('');

    try {
      const promptInstruction = buildPromptExpertPrompt(item.text);
      const payload = {
        contents: [
          {
            parts: [{ text: promptInstruction }]
          }
        ],
        generationConfig: {
          temperature: 0.2,
          maxOutputTokens: 1000
        }
      };

      // Primary: gemini-3.1-flash-lite (Proven fastest text generation)
      let modelToUse = 'gemini-3.1-flash-lite';
      let apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelToUse}:generateContent?key=${effectiveApiKey}`;

      let response = await fetch(apiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      let result = await response.json();

      // If primary model encounters an issue, fallback to gemini-3.5-flash
      if (!response.ok || result?.error) {
        console.warn('Fallback prompt engine to gemini-3.5-flash');
        modelToUse = 'gemini-3.5-flash';
        apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelToUse}:generateContent?key=${effectiveApiKey}`;
        response = await fetch(apiUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        result = await response.json();
      }

      if (result?.error) {
        throw new Error(result.error.message || 'Prompt generation failed');
      }

      const rawPrompt = result?.candidates?.[0]?.content?.parts?.[0]?.text;
      const cleanPrompt = rawPrompt ? rawPrompt.trim().replace(/^["']|["']$/g, '') : '';

      if (cleanPrompt) {
        setDraftHistory((prev) => {
          const updated = prev.map((d) => (d.id === item.id ? { ...d, aiPrompt: cleanPrompt } : d));
          try { localStorage.setItem('smart_voice_drafts', JSON.stringify(updated)); } catch (e) {}
          return updated;
        });

        setSelectedHistoryItem((prev) => (prev && prev.id === item.id ? { ...prev, aiPrompt: cleanPrompt } : prev));
      } else {
        setPromptError('AI prompt could not be generated. Please retry.');
      }
    } catch (err) {
      console.error('Failed to convert to prompt:', err);
      setPromptError(err?.message || 'Connection error while creating prompt. Please try again.');
    } finally {
      setConvertingPromptId(null);
    }
  };

  const handleSaveApiKey = () => {
    try {
      localStorage.setItem('smart_voice_api_key', customApiKey.trim());
      setKeySavedToast(true);
      setTimeout(() => setKeySavedToast(false), 2000);
    } catch (e) {}
  };

  const handleClearApiKey = () => {
    try {
      localStorage.removeItem('smart_voice_api_key');
      setCustomApiKey(CONFIG.DEFAULT_GEMINI_API_KEY || '');
    } catch (e) {}
  };

  const handleCopy = (text = outputText) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const handleShare = async () => {
    if (!outputText) return;
    if (navigator.share) {
      try {
        await navigator.share({ text: outputText });
      } catch (err) {}
    } else {
      window.open(`https://wa.me/?text=${encodeURIComponent(outputText)}`, '_blank');
    }
  };

  // Visualizer Analyser Setup
  const setupAudioVisualizer = (stream) => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      const ctx = new AudioCtx();
      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.82;
      source.connect(analyser);
      analyserRef.current = { ctx, analyser, dataArray: new Uint8Array(analyser.frequencyBinCount) };
    } catch (e) {}
  };

  const cleanupVisualizer = () => {
    try { analyserRef.current?.ctx?.close(); } catch (e) {}
    analyserRef.current = null;
    audioLevelRef.current = 0.2;
  };

  // Rich, Dynamic, Multi-Layer 3D Ribbon Waveform Canvas (Full Presence & Glow)
  useEffect(() => {
    const canvas = waveCanvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animationFrameId;
    let phase = 0;

    const renderWave = () => {
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      if (canvas.width !== rect.width * dpr || canvas.height !== rect.height * dpr) {
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
      }
      ctx.save();
      ctx.scale(dpr, dpr);

      const width = rect.width;
      const height = rect.height;
      const centerY = height / 2;

      ctx.clearRect(0, 0, width, height);

      // Amplitude interpolation
      let targetLevel = 0.25;
      if (status === 'listening' && analyserRef.current) {
        const { analyser, dataArray } = analyserRef.current;
        analyser.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < 35; i++) sum += dataArray[i];
        const avg = sum / 35 / 255;
        targetLevel = 0.4 + Math.min(1.2, avg * 2.5);
      } else if (status === 'processing') {
        targetLevel = 0.55 + Math.sin(phase * 3.5) * 0.2;
      }

      audioLevelRef.current += (targetLevel - audioLevelRef.current) * 0.08;
      const ampScale = audioLevelRef.current;

      const speed = status === 'listening' ? 0.05 : status === 'processing' ? 0.075 : 0.022;
      phase += speed;

      // Layered fluid 3D organic ribbons (Float seamlessly with zero rectangular box background)
      const layers = [
        { amp: 38, freq: 0.014, speedMult: 1.0, alpha: 0.35, color1: '#16534e', color2: '#3eb8ab' },
        { amp: 28, freq: 0.020, speedMult: -1.25, alpha: 0.52, color1: '#21746c', color2: '#57dfd2' },
        { amp: 20, freq: 0.026, speedMult: 1.6, alpha: 0.72, color1: '#36a99e', color2: '#8bfbf0' },
        { amp: 12, freq: 0.034, speedMult: -2.0, alpha: 0.95, color1: '#4fe6d8', color2: '#ffffff' }
      ];

      ctx.globalCompositeOperation = 'screen';

      layers.forEach((layer, idx) => {
        const grad = ctx.createLinearGradient(0, centerY, width, centerY);
        grad.addColorStop(0, 'rgba(25, 80, 75, 0)');
        grad.addColorStop(0.2, layer.color1);
        grad.addColorStop(0.5, layer.color2);
        grad.addColorStop(0.8, layer.color1);
        grad.addColorStop(1, 'rgba(25, 80, 75, 0)');

        ctx.beginPath();
        // Top undulating wave
        for (let x = 0; x <= width; x += 3) {
          const normX = x / width;
          const envelope = Math.pow(Math.sin(normX * Math.PI), 1.6);
          const wave1 = Math.sin(x * layer.freq + phase * layer.speedMult);
          const wave2 = Math.cos(x * layer.freq * 0.65 - phase * 0.8 + idx);
          const yOffset = (wave1 * 0.65 + wave2 * 0.35) * layer.amp * ampScale * envelope;
          const y = centerY + yOffset;
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }

        // Bottom mirrored organic wave (creates thick 3D fluid ribbon)
        for (let x = width; x >= 0; x -= 3) {
          const normX = x / width;
          const envelope = Math.pow(Math.sin(normX * Math.PI), 1.6);
          const wave1 = Math.sin(x * layer.freq - phase * layer.speedMult * 0.7 + 1.2);
          const wave2 = Math.sin(x * layer.freq * 0.5 + phase * 1.1);
          const yOffset = (wave1 * 0.6 + wave2 * 0.4) * (layer.amp * 0.78) * ampScale * envelope;
          const y = centerY - yOffset;
          ctx.lineTo(x, y);
        }
        ctx.closePath();

        ctx.fillStyle = grad;
        ctx.globalAlpha = layer.alpha;
        ctx.fill();

        if (idx >= 2) {
          ctx.shadowColor = '#4fe6d8';
          ctx.shadowBlur = idx === 3 ? 14 : 6;
          ctx.strokeStyle = grad;
          ctx.lineWidth = idx === 3 ? 1.8 : 1.2;
          ctx.stroke();
          ctx.shadowBlur = 0;
        }
      });

      ctx.restore();
      animationFrameId = requestAnimationFrame(renderWave);
    };

    renderWave();
    return () => cancelAnimationFrame(animationFrameId);
  }, [status]);

  // Ultra-Fast Audio Request
  const executeGeminiAudioRequest = async (modelToUse, base64Audio, recordedMimeType, apiKey) => {
    const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelToUse}:generateContent?key=${apiKey}`;
    const payload = {
      systemInstruction: {
        parts: [{ text: getSystemInstruction(languageStyle) }]
      },
      contents: [
        {
          role: 'user',
          parts: [
            { text: getUserPromptForAudio() },
            {
              inlineData: {
                mimeType: recordedMimeType,
                data: base64Audio
              }
            }
          ]
        }
      ],
      generationConfig: {
        temperature: 0.1,
        maxOutputTokens: 1000
      }
    };

    const response = await fetch(apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const result = await response.json();
    return { ok: response.ok, status: response.status, result };
  };

  const startListening = async () => {
    try {
      setErrorMsg('');
      const effectiveApiKey = (customApiKey || CONFIG.DEFAULT_GEMINI_API_KEY || '').trim();
      if (!effectiveApiKey) {
        setErrorMsg('API Key missing. Enter key in Settings.');
        setShowSettings(true);
        return;
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      streamRef.current = stream;
      setupAudioVisualizer(stream);
      audioChunksRef.current = [];

      const chosenMimeType = getSupportedMimeType();
      const mediaRecorder = chosenMimeType
        ? new MediaRecorder(stream, { mimeType: chosenMimeType })
        : new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;

      mediaRecorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      mediaRecorder.onstop = async () => {
        setStatus('processing');
        cleanupVisualizer();
        const recordedMimeType = mediaRecorder.mimeType || 'audio/webm';
        const audioBlob = new Blob(audioChunksRef.current, { type: recordedMimeType });
        stream.getTracks().forEach((track) => track.stop());

        try {
          const base64Audio = await blobToBase64(audioBlob);

          let activeModel = selectedModel;
          let res = await executeGeminiAudioRequest(activeModel, base64Audio, recordedMimeType, effectiveApiKey);

          if (!res.ok || res.result?.error) {
            const fallbackChain = ['gemini-3.1-flash-lite', 'gemini-3.5-flash'];
            const fallbackModel = fallbackChain.find((m) => m !== activeModel) || 'gemini-3.5-flash';
            res = await executeGeminiAudioRequest(fallbackModel, base64Audio, recordedMimeType, effectiveApiKey);
          }

          if (res.result?.error) {
            throw new Error(res.result.error.message || 'API error');
          }

          const rawText = res.result?.candidates?.[0]?.content?.parts?.[0]?.text;
          const cleanText = rawText ? rawText.trim().replace(/^["']|["']$/g, '') : '';

          if (cleanText) {
            setOutputText(cleanText);
            saveDraftToHistory(cleanText);
            if (autoCopy) handleCopy(cleanText);

            // Windows Electron Global Auto-Paste into focused field
            if (window.electronAPI && window.electronAPI.autoPasteText) {
              window.electronAPI.autoPasteText(cleanText);
            }
          } else {
            setErrorMsg('No speech detected.');
          }
        } catch (err) {
          setErrorMsg(err?.message || 'Connection error.');
        } finally {
          setStatus('idle');
        }
      };

      mediaRecorder.start();
      setStatus('listening');
    } catch (err) {
      setErrorMsg('Microphone permission required.');
      setStatus('idle');
    }
  };

  const stopListening = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
  };

  const toggleVoiceAction = () => {
    if (status === 'processing') return;
    if (status === 'idle') startListening();
    else if (status === 'listening') stopListening();
  };

  // Overlay Handlers
  const handleOverlayPointerDown = (e) => {
    draggingRef.current = true;
    movedRef.current = false;
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    dragOffsetRef.current = { x: clientX - overlayPos.x, y: clientY - overlayPos.y };
  };

  useEffect(() => {
    const handlePointerMove = (e) => {
      if (!draggingRef.current) return;
      const clientX = e.touches ? e.touches[0].clientX : e.clientX;
      const clientY = e.touches ? e.touches[0].clientY : e.clientY;
      const newX = Math.max(12, Math.min(window.innerWidth - 64, clientX - dragOffsetRef.current.x));
      const newY = Math.max(12, Math.min(window.innerHeight - 64, clientY - dragOffsetRef.current.y));
      if (Math.abs(newX - overlayPos.x) > 5 || Math.abs(newY - overlayPos.y) > 5) {
        movedRef.current = true;
      }
      setOverlayPos({ x: newX, y: newY });
    };

    const handlePointerUp = () => {
      draggingRef.current = false;
    };

    window.addEventListener('mousemove', handlePointerMove);
    window.addEventListener('mouseup', handlePointerUp);
    window.addEventListener('touchmove', handlePointerMove);
    window.addEventListener('touchend', handlePointerUp);
    return () => {
      window.removeEventListener('mousemove', handlePointerMove);
      window.removeEventListener('mouseup', handlePointerUp);
      window.removeEventListener('touchmove', handlePointerMove);
      window.removeEventListener('touchend', handlePointerUp);
    };
  }, [overlayPos]);

  return (
    <div className="h-[100dvh] w-screen bg-[#0e1115] flex items-center justify-center font-sans select-none overflow-hidden text-white relative">
      {/* Ambient background glow on desktop */}
      <div className="hidden md:block absolute w-[500px] h-[500px] rounded-full bg-[#3eb8ab]/10 blur-[120px] pointer-events-none" />

      {/* Main Container - Seamless Unified Atmosphere (Zero Box Cutoff) */}
      <div className="w-full h-full md:max-w-[420px] md:h-[92vh] md:max-h-[860px] md:rounded-[36px] bg-[#121519] [background:radial-gradient(ellipse_120%_75%_at_50%_35%,rgba(62,184,171,0.08)_0%,transparent_70%),#121519] md:border md:border-white/10 md:shadow-[0_20px_60px_rgba(0,0,0,0.8),0_0_40px_rgba(62,184,171,0.08)] flex flex-col justify-between px-6 pt-7 pb-6 relative overflow-hidden">
        
        {/* Top Header */}
        <div className="flex items-center justify-between z-20">
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-[#4fe6d8] shadow-[0_0_8px_#4fe6d8]" />
            <span className="font-semibold text-[15px] tracking-wider text-white">VoiceFlow</span>
          </div>

          {/* Clean API Live/Dead Status Symbol (Green = Live, Red = Dead) */}
          <button
            onClick={() => setShowSettings(true)}
            title={(customApiKey || CONFIG.DEFAULT_GEMINI_API_KEY || '').trim() && !errorMsg ? "API Live & Connected" : "API Dead / Missing"}
            className="w-7 h-7 rounded-full bg-white/[0.04] hover:bg-white/[0.08] border border-white/10 flex items-center justify-center transition"
          >
            <span
              className={`w-2 h-2 rounded-full transition-all ${
                (customApiKey || CONFIG.DEFAULT_GEMINI_API_KEY || '').trim() && !errorMsg
                  ? 'bg-emerald-400 shadow-[0_0_8px_#34d399]'
                  : 'bg-rose-500 shadow-[0_0_8px_#f43f5e] animate-pulse'
              }`}
            />
          </button>
        </div>

        {/* Status Line */}
        <div className="text-center mt-1 h-5 flex items-center justify-center">
          {status === 'listening' && (
            <span className="text-xs text-red-400 font-medium tracking-wide animate-pulse">Listening</span>
          )}
          {status === 'processing' && (
            <span className="text-xs text-[#4fe6d8] font-medium tracking-wide">Transcribing</span>
          )}
        </div>

        {/* Floating Ribbon Waveform Visualizer Canvas (Seamlessly floats on background) */}
        <div className="relative flex-1 flex items-center justify-center -mx-6 my-auto">
          <canvas
            ref={waveCanvasRef}
            onClick={toggleVoiceAction}
            className="w-full h-52 cursor-pointer relative z-10"
            title={status === 'listening' ? 'Stop' : 'Speak'}
          />
        </div>

        {/* Output Text Box / Card */}
        <div className="min-h-[145px] flex flex-col justify-center px-1 mb-2 relative z-10">
          {errorMsg ? (
            <div className="bg-red-950/40 border border-red-500/30 text-red-400 text-xs p-3.5 rounded-2xl flex items-center justify-between">
              <span>{errorMsg}</span>
              <button onClick={() => setErrorMsg('')} className="text-red-400/70 hover:text-red-400">
                <X className="w-4 h-4" />
              </button>
            </div>
          ) : outputText ? (
            <div className="bg-white/[0.04] backdrop-blur-xl border border-white/10 rounded-2xl p-4 space-y-3 shadow-lg">
              {isEditing ? (
                <textarea
                  value={outputText}
                  onChange={(e) => setOutputText(e.target.value)}
                  className="w-full bg-black/40 border border-white/15 rounded-xl p-2.5 text-sm text-white focus:outline-none focus:border-[#4fe6d8] resize-none h-24"
                  autoFocus
                />
              ) : (
                <p className="text-[14.5px] leading-relaxed text-white/95 max-h-[110px] overflow-y-auto pr-1">
                  {outputText}
                </p>
              )}

              {/* Clean Action Buttons */}
              <div className="flex items-center justify-between pt-1 border-t border-white/10">
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => handleCopy()}
                    className={`inline-flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-full font-medium transition ${
                      copied
                        ? 'bg-[#4fe6d8] text-[#0e1115]'
                        : 'bg-white/10 hover:bg-white/15 text-white'
                    }`}
                  >
                    {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copied ? 'Copied' : 'Copy'}</span>
                  </button>

                  <button
                    onClick={handleShare}
                    className="inline-flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-full bg-white/[0.06] hover:bg-white/10 text-white/80 transition"
                  >
                    <Share2 className="w-3.5 h-3.5" />
                    <span>Share</span>
                  </button>

                  <button
                    onClick={() => setIsEditing(!isEditing)}
                    className={`p-1.5 rounded-full transition ${
                      isEditing ? 'bg-[#4fe6d8]/20 text-[#4fe6d8]' : 'text-white/50 hover:text-white'
                    }`}
                    title="Edit"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                </div>

                <button
                  onClick={() => { setOutputText(''); setIsEditing(false); }}
                  className="text-xs text-white/40 hover:text-red-400 transition px-2"
                >
                  Clear
                </button>
              </div>
            </div>
          ) : (
            <div className="text-center py-4">
              <span className="text-xs text-white/35">Tap mic to speak</span>
            </div>
          )}
        </div>

        {/* Bottom Control Bar */}
        <div className="flex items-center justify-between px-3 pt-2 relative z-10">
          {/* History Drawer Button */}
          <button
            onClick={() => setShowHistory(true)}
            className="w-11 h-11 rounded-2xl flex items-center justify-center text-white/50 hover:text-white bg-white/[0.04] hover:bg-white/[0.08] border border-white/5 transition"
            title="History"
          >
            <History className="w-5 h-5 stroke-[1.8]" />
          </button>

          {/* Premium Glowing Action Orb */}
          <div className="relative flex items-center justify-center">
            <div
              className={`absolute w-16 h-16 rounded-full bg-[#4fe6d8]/35 blur-xl transition-all duration-500 ${
                status === 'listening' ? 'scale-150 opacity-100 bg-red-500/40' : 'scale-100 opacity-60'
              }`}
            />
            <button
              onClick={toggleVoiceAction}
              className={`relative z-10 w-16 h-16 rounded-full flex items-center justify-center transition-all duration-300 shadow-[0_0_35px_rgba(79,230,216,0.65)] active:scale-95 ${
                status === 'listening'
                  ? 'bg-red-500 text-white scale-110 shadow-[0_0_35px_rgba(239,68,68,0.7)] animate-pulse'
                  : status === 'processing'
                  ? 'bg-[#36a99e] text-white animate-spin'
                  : 'bg-gradient-to-tr from-[#1b5e57] to-[#4fe6d8] text-[#0b1c1a] hover:scale-105'
              }`}
            >
              {status === 'processing' ? (
                <RefreshCw className="w-7 h-7 stroke-[2.2]" />
              ) : (
                <Mic className="w-7 h-7 stroke-[2.2]" />
              )}
            </button>
          </div>

          {/* Settings Button */}
          <button
            onClick={() => setShowSettings(true)}
            className="w-11 h-11 rounded-2xl flex items-center justify-center text-white/50 hover:text-white bg-white/[0.04] hover:bg-white/[0.08] border border-white/5 transition"
            title="Settings"
          >
            <Settings className="w-5 h-5 stroke-[1.8]" />
          </button>
        </div>

        {/* History Drawer */}
        {showHistory && (
          <div className="absolute inset-0 bg-[#12161b]/98 backdrop-blur-2xl z-50 p-6 flex flex-col justify-between">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <span className="text-sm font-semibold text-white">History</span>
              <div className="flex items-center gap-2">
                {draftHistory.length > 0 && (
                  <button onClick={clearAllHistory} className="text-xs text-red-400 hover:underline">
                    Clear All
                  </button>
                )}
                <button
                  onClick={() => setShowHistory(false)}
                  className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-white/60 hover:text-white"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto my-3 space-y-2.5 pr-1">
              {draftHistory.length === 0 ? (
                <div className="h-full flex items-center justify-center text-xs text-white/30">
                  No drafts saved.
                </div>
              ) : (
                draftHistory.map((item) => (
                  <div
                    key={item.id}
                    onClick={() => { setSelectedHistoryItem(item); setPromptError(''); }}
                    className="bg-white/[0.04] border border-white/10 rounded-2xl p-3 space-y-1.5 cursor-pointer hover:border-white/20 transition"
                  >
                    <div className="flex items-center justify-between text-[11px] text-white/40">
                      <span>{item.timestamp}</span>
                      <div className="flex items-center gap-2.5" onClick={(e) => e.stopPropagation()}>
                        {/* Quick AI Prompt Trigger */}
                        <button
                          onClick={() => {
                            setSelectedHistoryItem(item);
                            if (!item.aiPrompt) handleConvertToPrompt(item);
                          }}
                          disabled={convertingPromptId === item.id}
                          className={`p-1 rounded-md transition hover:scale-110 ${
                            convertingPromptId === item.id
                              ? 'text-[#4fe6d8] animate-spin'
                              : item.aiPrompt
                              ? 'text-[#4fe6d8]'
                              : 'text-white/40 hover:text-[#4fe6d8]'
                          }`}
                          title={item.aiPrompt ? 'View Prompt' : 'Generate Prompt'}
                        >
                          {convertingPromptId === item.id ? (
                            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                          ) : (
                            <Sparkles className="w-3.5 h-3.5" />
                          )}
                        </button>

                        <button
                          onClick={() => {
                            navigator.clipboard.writeText(item.text);
                            setHistoryCopiedId(item.id);
                            setTimeout(() => setHistoryCopiedId(null), 1200);
                          }}
                          className="hover:text-white p-1 text-white/45"
                          title="Copy Draft"
                        >
                          {historyCopiedId === item.id ? (
                            <Check className="w-3.5 h-3.5 text-[#4fe6d8]" />
                          ) : (
                            <Copy className="w-3.5 h-3.5" />
                          )}
                        </button>
                        <button
                          onClick={() => deleteHistoryItem(item.id)}
                          className="hover:text-red-400 p-1 text-white/45"
                          title="Delete Draft"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                    <p className="text-xs text-white/85 line-clamp-2">{item.text}</p>
                    {item.aiPrompt && (
                      <div className="text-[10px] text-[#4fe6d8]/80 flex items-center gap-1 font-medium pt-0.5">
                        <Sparkles className="w-2.5 h-2.5" />
                        <span>Prompt Ready (Tap to open)</span>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>

            <button
              onClick={() => setShowHistory(false)}
              className="w-full py-3 rounded-2xl bg-white/10 text-xs font-medium text-white hover:bg-white/15 transition"
            >
              Close
            </button>
          </div>
        )}

        {/* Draft Detail & Prompt Generator Modal (Opens when user taps any history draft) */}
        {selectedHistoryItem && (
          <div className="absolute inset-0 bg-[#0e1115]/95 backdrop-blur-2xl z-50 p-6 flex flex-col justify-between overflow-y-auto animate-fadeIn">
            <div className="space-y-4">
              {/* Header */}
              <div className="flex items-center justify-between border-b border-white/10 pb-3">
                <div className="flex items-center gap-2">
                  <div className="w-2 h-2 rounded-full bg-[#4fe6d8]" />
                  <span className="text-xs font-semibold text-white/90">Draft Details</span>
                  <span className="text-white/30">•</span>
                  <span className="text-[11px] text-white/40">{selectedHistoryItem.timestamp}</span>
                </div>
                <button
                  onClick={() => { setSelectedHistoryItem(null); setDetailCopied(false); setPromptCopied(false); }}
                  className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-white/60 hover:text-white"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* 1. Main Spoken Text Box (Always present) */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] text-white/40 uppercase tracking-wider font-medium">Main Spoken Text</span>
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(selectedHistoryItem.text);
                      setDetailCopied(true);
                      setTimeout(() => setDetailCopied(false), 1500);
                    }}
                    className="text-[11px] text-white/60 hover:text-white flex items-center gap-1 transition"
                  >
                    {detailCopied ? <Check className="w-3 h-3 text-[#4fe6d8]" /> : <Copy className="w-3 h-3 text-white/40" />}
                    <span>{detailCopied ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>

                <div className="bg-white/[0.04] border border-white/10 rounded-2xl p-4 max-h-[140px] overflow-y-auto">
                  <p className="text-[13.5px] text-white/95 leading-relaxed font-normal">
                    {selectedHistoryItem.text}
                  </p>
                </div>
              </div>

              {/* 2. Generate Prompt Action Button */}
              <div className="pt-1 space-y-2">
                {promptError && (
                  <div className="text-xs text-red-400 bg-red-950/50 border border-red-500/30 p-3 rounded-xl flex items-center justify-between">
                    <span>{promptError}</span>
                    <button onClick={() => setPromptError('')} className="text-red-400/70 hover:text-red-400">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
                <button
                  onClick={() => handleConvertToPrompt(selectedHistoryItem, !!selectedHistoryItem.aiPrompt)}
                  disabled={convertingPromptId === selectedHistoryItem.id}
                  className={`w-full py-3 rounded-xl font-medium text-xs transition flex items-center justify-center gap-2 border ${
                    selectedHistoryItem.aiPrompt
                      ? 'bg-white/[0.06] border-white/15 text-white/80 hover:bg-white/10'
                      : 'bg-[#4fe6d8]/15 border-[#4fe6d8]/40 text-[#4fe6d8] hover:bg-[#4fe6d8]/25 shadow-lg shadow-[#4fe6d8]/10'
                  }`}
                >
                  {convertingPromptId === selectedHistoryItem.id ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin text-[#4fe6d8]" />
                      <span>Generating Structured AI Prompt...</span>
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>{selectedHistoryItem.aiPrompt ? 'Regenerate AI Prompt' : 'Generate AI Prompt'}</span>
                    </>
                  )}
                </button>
              </div>

              {/* 3. Generated AI Prompt Box (Only appears AFTER prompt is generated) */}
              {selectedHistoryItem.aiPrompt && (
                <div className="space-y-2 pt-1 animate-fadeIn">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] text-[#4fe6d8] uppercase tracking-wider font-semibold flex items-center gap-1">
                      <Sparkles className="w-3 h-3" />
                      <span>Generated AI Prompt</span>
                    </span>
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText(selectedHistoryItem.aiPrompt);
                        setPromptCopied(true);
                        setTimeout(() => setPromptCopied(false), 1500);
                      }}
                      className="text-[11px] text-[#4fe6d8] hover:underline flex items-center gap-1"
                    >
                      {promptCopied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                      <span>{promptCopied ? 'Copied Prompt' : 'Copy Prompt'}</span>
                    </button>
                  </div>

                  <div className="bg-black/50 border border-[#4fe6d8]/30 rounded-2xl p-3.5 max-h-[170px] overflow-y-auto">
                    <p className="text-xs text-white/95 leading-relaxed font-mono whitespace-pre-wrap select-text">
                      {selectedHistoryItem.aiPrompt}
                    </p>
                  </div>

                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(selectedHistoryItem.aiPrompt);
                      setPromptCopied(true);
                      setTimeout(() => setPromptCopied(false), 1500);
                    }}
                    className={`w-full py-3 rounded-xl font-semibold text-xs transition flex items-center justify-center gap-2 ${
                      promptCopied
                        ? 'bg-[#238636] text-white'
                        : 'bg-[#4fe6d8] text-[#0e1115] hover:opacity-95 shadow-md shadow-[#4fe6d8]/20'
                    }`}
                  >
                    {promptCopied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    <span>{promptCopied ? 'Copied AI Prompt!' : 'Copy AI Prompt'}</span>
                  </button>
                </div>
              )}
            </div>

            {/* Bottom Controls */}
            <div className="pt-4 border-t border-white/10 flex items-center justify-between gap-2">
              <button
                onClick={() => {
                  setOutputText(selectedHistoryItem.text);
                  setSelectedHistoryItem(null);
                  setShowHistory(false);
                }}
                className="text-xs text-white/60 hover:text-white px-3 py-2 rounded-xl bg-white/5 hover:bg-white/10 transition"
              >
                Use in Main Screen
              </button>

              <button
                onClick={() => { setSelectedHistoryItem(null); setDetailCopied(false); setPromptCopied(false); }}
                className="text-xs font-medium text-white px-5 py-2 rounded-xl bg-white/10 hover:bg-white/15 transition"
              >
                Back to History
              </button>
            </div>
          </div>
        )}

        {/* Settings Modal */}
        {showSettings && (
          <div className="absolute inset-0 bg-[#12161b]/98 backdrop-blur-2xl z-50 p-6 flex flex-col justify-between overflow-y-auto">
            <div className="space-y-4">
              <div className="flex items-center justify-between border-b border-white/10 pb-4">
                <span className="text-sm font-semibold text-white">Settings</span>
                <button
                  onClick={() => setShowSettings(false)}
                  className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-white/60 hover:text-white"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Model */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-white/70">Model</label>
                <div className="space-y-1.5">
                  {CONFIG.AVAILABLE_MODELS.map((m) => {
                    const isSelected = selectedModel === m.id;
                    return (
                      <div
                        key={m.id}
                        onClick={() => setSelectedModel(m.id)}
                        className={`p-3 rounded-xl border text-xs cursor-pointer flex items-center justify-between transition ${
                          isSelected
                            ? 'bg-[#4fe6d8]/15 border-[#4fe6d8] text-white'
                            : 'bg-white/[0.03] border-white/10 text-white/60 hover:border-white/20'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-white/90">{m.name}</span>
                          <span className="text-[10px] text-white/40">{m.tag}</span>
                        </div>
                        {isSelected && <Check className="w-4 h-4 text-[#4fe6d8]" />}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Language Style */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-white/70">Language</label>
                <div className="grid grid-cols-3 gap-1.5 text-xs">
                  {CONFIG.OUTPUT_STYLES.map((s) => {
                    const isSelected = languageStyle === s.id;
                    return (
                      <button
                        key={s.id}
                        onClick={() => setLanguageStyle(s.id)}
                        className={`py-2 px-2 rounded-xl border text-center transition ${
                          isSelected
                            ? 'bg-[#4fe6d8]/15 border-[#4fe6d8] text-white'
                            : 'bg-white/[0.03] border-white/10 text-white/60 hover:border-white/20'
                        }`}
                      >
                        {s.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* API Key */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-white/70">Gemini API Key</label>
                <div className="relative flex items-center">
                  <input
                    type={showApiKeyText ? 'text' : 'password'}
                    value={customApiKey}
                    onChange={(e) => setCustomApiKey(e.target.value)}
                    placeholder="Enter API key"
                    className="w-full bg-black/40 border border-white/15 rounded-xl px-3 pr-9 py-2.5 text-xs text-white focus:outline-none focus:border-[#4fe6d8]"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKeyText(!showApiKeyText)}
                    className="absolute right-3 text-white/40 hover:text-white"
                  >
                    {showApiKeyText ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                <div className="flex items-center justify-between pt-1">
                  <button
                    onClick={handleSaveApiKey}
                    className="text-xs px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/15 text-white transition"
                  >
                    {keySavedToast ? 'Saved' : 'Save'}
                  </button>
                  {customApiKey && (
                    <button onClick={handleClearApiKey} className="text-xs text-red-400 hover:underline">
                      Clear
                    </button>
                  )}
                </div>
              </div>

              {/* Preferences */}
              <div className="space-y-2.5 pt-2 border-t border-white/10">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-white/80">Auto-copy to clipboard</span>
                  <button
                    onClick={() => setAutoCopy(!autoCopy)}
                    className={`w-10 h-5 rounded-full p-0.5 flex items-center transition ${
                      autoCopy ? 'bg-[#4fe6d8] justify-end' : 'bg-white/20 justify-start'
                    }`}
                  >
                    <span className="w-4 h-4 rounded-full bg-[#14181d] shadow-sm" />
                  </button>
                </div>

                <div className="flex items-center justify-between">
                  <span className="text-xs text-white/80">Floating mic overlay</span>
                  <button
                    onClick={handleToggleOverlay}
                    className={`w-10 h-5 rounded-full p-0.5 flex items-center transition ${
                      overlayEnabled ? 'bg-[#4fe6d8] justify-end' : 'bg-white/20 justify-start'
                    }`}
                  >
                    <span className="w-4 h-4 rounded-full bg-[#14181d] shadow-sm" />
                  </button>
                </div>
              </div>
            </div>

            <button
              onClick={() => setShowSettings(false)}
              className="w-full mt-4 py-3 rounded-2xl bg-[#4fe6d8] text-[#0e1115] font-semibold text-xs hover:opacity-95 transition shadow-lg shadow-[#4fe6d8]/25"
            >
              Done
            </button>
          </div>
        )}
      </div>

      {/* Floating Overlay Button */}
      {overlayEnabled && (
        <div
          style={{
            position: 'fixed',
            left: `${overlayPos.x}px`,
            top: `${overlayPos.y}px`,
            zIndex: 9999,
            touchAction: 'none'
          }}
          onMouseDown={handleOverlayPointerDown}
          onTouchStart={handleOverlayPointerDown}
        >
          <button
            onClick={() => { if (!movedRef.current) toggleVoiceAction(); }}
            className={`w-14 h-14 rounded-full flex items-center justify-center shadow-[0_0_25px_rgba(79,230,216,0.5)] border-2 border-white/60 transition active:scale-95 ${
              status === 'listening'
                ? 'bg-red-500 text-white animate-pulse'
                : status === 'processing'
                ? 'bg-[#36a99e] text-white animate-spin'
                : 'bg-gradient-to-tr from-[#1b5e57] to-[#4fe6d8] text-[#0b1c1a]'
            }`}
          >
            <Mic className="w-6 h-6 stroke-[2]" />
          </button>
        </div>
      )}
    </div>
  );
}
