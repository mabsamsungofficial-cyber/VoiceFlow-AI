export const CONFIG = {
  DEFAULT_GEMINI_API_KEY: import.meta.env.VITE_GEMINI_API_KEY || "",
  DEFAULT_MODEL: "gemini-flash-lite-latest",
  DEFAULT_LANGUAGE_STYLE: "hinglish",

  AVAILABLE_MODELS: [
    {
      id: "gemini-flash-lite-latest",
      name: "Gemini Flash Lite (Fastest)",
      tag: "Ultra-Fast (~1s) & Auto-Clean"
    },
    {
      id: "gemini-3.5-flash-lite",
      name: "Gemini 3.5 Flash Lite",
      tag: "Fast (~1.5s)"
    },
    {
      id: "gemini-3.5-flash",
      name: "Gemini 3.5 Flash",
      tag: "Standard (~2s)"
    }
  ],

  OUTPUT_STYLES: [
    { id: "hinglish", label: "Hinglish (Roman)" },
    { id: "hindi", label: "Hindi (Devanagari)" },
    { id: "english", label: "English" }
  ]
};
