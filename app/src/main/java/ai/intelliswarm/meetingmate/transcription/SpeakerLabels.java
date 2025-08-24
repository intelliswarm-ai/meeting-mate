package ai.intelliswarm.meetingmate.transcription;

import java.util.HashMap;
import java.util.Map;

/**
 * Multilingual speaker labels for transcription
 */
public class SpeakerLabels {
    
    private static final Map<String, LanguageLabels> LABELS = new HashMap<>();
    
    public static class LanguageLabels {
        public String speaker;
        public String rate;
        public String wordsPerSecond;
        public String confidence;
        public String keyPoints;
        public String actionItems;
        public String summary;
        
        LanguageLabels(String speaker, String rate, String wordsPerSecond) {
            this.speaker = speaker;
            this.rate = rate;
            this.wordsPerSecond = wordsPerSecond;
        }
    }
    
    static {
        // English
        LABELS.put("en", new LanguageLabels("Speaker", "rate", "w/s"));
        
        // Spanish
        LABELS.put("es", new LanguageLabels("Hablante", "velocidad", "p/s"));
        
        // French
        LABELS.put("fr", new LanguageLabels("Intervenant", "débit", "m/s"));
        
        // German
        LABELS.put("de", new LanguageLabels("Sprecher", "Tempo", "W/s"));
        
        // Italian
        LABELS.put("it", new LanguageLabels("Parlante", "velocità", "p/s"));
        
        // Portuguese
        LABELS.put("pt", new LanguageLabels("Falante", "velocidade", "p/s"));
        
        // Russian
        LABELS.put("ru", new LanguageLabels("Говорящий", "скорость", "сл/с"));
        
        // Polish
        LABELS.put("pl", new LanguageLabels("Mówca", "tempo", "sł/s"));
        
        // Dutch
        LABELS.put("nl", new LanguageLabels("Spreker", "tempo", "w/s"));
        
        // Greek
        LABELS.put("el", new LanguageLabels("Ομιλητής", "ρυθμός", "λ/δ"));
        
        // Swedish
        LABELS.put("sv", new LanguageLabels("Talare", "takt", "o/s"));
        
        // Danish
        LABELS.put("da", new LanguageLabels("Taler", "hastighed", "o/s"));
        
        // Norwegian
        LABELS.put("no", new LanguageLabels("Taler", "tempo", "o/s"));
        
        // Finnish
        LABELS.put("fi", new LanguageLabels("Puhuja", "nopeus", "s/s"));
        
        // Czech
        LABELS.put("cs", new LanguageLabels("Řečník", "tempo", "s/s"));
        
        // Hungarian
        LABELS.put("hu", new LanguageLabels("Beszélő", "sebesség", "sz/mp"));
        
        // Slovak
        LABELS.put("sk", new LanguageLabels("Rečník", "tempo", "s/s"));
        
        // Romanian
        LABELS.put("ro", new LanguageLabels("Vorbitor", "viteză", "c/s"));
        
        // Bulgarian
        LABELS.put("bg", new LanguageLabels("Говорител", "скорост", "д/с"));
        
        // Croatian
        LABELS.put("hr", new LanguageLabels("Govornik", "brzina", "r/s"));
        
        // Slovenian
        LABELS.put("sl", new LanguageLabels("Govorec", "hitrost", "b/s"));
        
        // Estonian
        LABELS.put("et", new LanguageLabels("Kõneleja", "kiirus", "s/s"));
        
        // Latvian
        LABELS.put("lv", new LanguageLabels("Runātājs", "ātrums", "v/s"));
        
        // Lithuanian
        LABELS.put("lt", new LanguageLabels("Kalbėtojas", "greitis", "ž/s"));
        
        // Maltese
        LABELS.put("mt", new LanguageLabels("Kelliem", "rata", "k/s"));
        
        // Turkish
        LABELS.put("tr", new LanguageLabels("Konuşmacı", "hız", "k/sn"));
        
        // Ukrainian
        LABELS.put("uk", new LanguageLabels("Мовець", "швидкість", "сл/с"));
    }
    
    /**
     * Get labels for a specific language
     * @param languageCode ISO 639-1 language code
     * @return Language-specific labels or English as fallback
     */
    public static LanguageLabels getLabels(String languageCode) {
        if (languageCode == null || languageCode.isEmpty() || languageCode.equals("auto")) {
            return LABELS.get("en");
        }
        
        // Try exact match first
        LanguageLabels labels = LABELS.get(languageCode.toLowerCase());
        if (labels != null) {
            return labels;
        }
        
        // Try language part only (e.g., "en-US" -> "en")
        if (languageCode.contains("-")) {
            String baseLang = languageCode.split("-")[0].toLowerCase();
            labels = LABELS.get(baseLang);
            if (labels != null) {
                return labels;
            }
        }
        
        // Fallback to English
        return LABELS.get("en");
    }
    
    /**
     * Format speaker label with number
     */
    public static String formatSpeakerLabel(String languageCode, int speakerNumber) {
        LanguageLabels labels = getLabels(languageCode);
        return labels.speaker + " " + speakerNumber;
    }
    
    /**
     * Format rate label
     */
    public static String formatRateLabel(String languageCode, double rate) {
        LanguageLabels labels = getLabels(languageCode);
        return String.format("%s: %.1f %s", labels.rate, rate, labels.wordsPerSecond);
    }
}