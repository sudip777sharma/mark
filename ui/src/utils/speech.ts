/**
 * Web Speech API wrapper for real browser Speech Synthesis (TTS) and Speech Recognition (STT).
 */
export const speechService = {
  speak(text: string, onStart?: () => void, onEnd?: () => void) {
    if (!('speechSynthesis' in window)) {
      console.warn('Speech synthesis not supported in this browser.');
      if (onEnd) onEnd();
      return;
    }

    try {
      window.speechSynthesis.cancel();

      // Clean text of markdown and code formatting for natural voice articulation
      const cleanText = text
        .replace(/```[\s\S]*?```/g, '')
        .replace(/`[^`]*`/g, '')
        .replace(/[*_~#]/g, '')
        .replace(/https?:\/\/\S+/g, 'link')
        .replace(/\(.*?\)/g, '')
        .replace(/\{.*?\}/g, '')
        .trim();

      if (!cleanText) {
        if (onEnd) onEnd();
        return;
      }

      // Truncate spoken text to avoid excessively long speech
      const spokenSummary = cleanText.length > 250 ? cleanText.substring(0, 250) + '...' : cleanText;

      const utterance = new SpeechSynthesisUtterance(spokenSummary);
      utterance.rate = 1.05;
      utterance.pitch = 1.0;

      const voices = window.speechSynthesis.getVoices();
      const preferredVoice =
        voices.find(
          (v) =>
            (v.name.includes('Natural') ||
              v.name.includes('Google') ||
              v.name.includes('Samantha') ||
              v.name.includes('David') ||
              v.name.includes('Mark')) &&
            v.lang.startsWith('en')
        ) || voices.find((v) => v.lang.startsWith('en'));

      if (preferredVoice) utterance.voice = preferredVoice;

      if (onStart) utterance.onstart = onStart;
      utterance.onend = () => {
        if (onEnd) onEnd();
      };
      utterance.onerror = (e) => {
        console.warn('Speech synthesis error:', e);
        if (onEnd) onEnd();
      };

      window.speechSynthesis.speak(utterance);
    } catch (err) {
      console.warn('Speech synthesis exception:', err);
      if (onEnd) onEnd();
    }
  },

  stopSpeaking() {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  },

  createRecognizer(
    onResult: (transcript: string, isFinal: boolean) => void,
    onEnd?: () => void,
    onError?: (err: any) => void
  ): any | null {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      console.warn('Web Speech Recognition not supported in this browser.');
      return null;
    }

    try {
      const recognizer = new SpeechRecognition();
      recognizer.continuous = false;
      recognizer.interimResults = true;
      recognizer.lang = 'en-US';

      recognizer.onresult = (event: any) => {
        let text = '';
        let isFinal = false;
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          text += event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            isFinal = true;
          }
        }
        onResult(text, isFinal);
      };

      if (onEnd) recognizer.onend = onEnd;
      if (onError) recognizer.onerror = onError;

      return recognizer;
    } catch (err) {
      console.warn('Failed to initialize SpeechRecognition:', err);
      return null;
    }
  },
};
