package org.diploma.transcription;

public interface TranslationService
{
    String translate(String sourceText, String sourceLang, String targetLang);
}
