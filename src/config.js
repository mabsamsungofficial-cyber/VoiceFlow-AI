export const CONFIG = {
  DEFAULT_GEMINI_API_KEY: import.meta.env.VITE_GEMINI_API_KEY || "",
  DEFAULT_MODEL: "gemini-3.1-flash-lite",
  DEFAULT_LANGUAGE_STYLE: "hinglish",

  AVAILABLE_MODELS: [
    {
      id: "gemini-3.1-flash-lite",
      name: "Gemini 3.1 Flash Lite",
      tag: "Fastest (~1s)"
    },
    {
      id: "gemini-3.5-flash",
      name: "Gemini 3.5 Flash",
      tag: "Standard (~2s)"
    },
    {
      id: "gemini-3.8-flash",
      name: "Gemini 3.8 Flash",
      tag: "Deep Reasoning"
    }
  ],

  OUTPUT_STYLES: [
    { id: "hinglish", label: "Hinglish (Roman)" },
    { id: "hindi", label: "Hindi (Devanagari)" },
    { id: "english", label: "English" }
  ]
};
