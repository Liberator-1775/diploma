package org.diploma.transcription;

public class TranslationResult
{
    private String language;

    private String text;

    private TranscriptionResult transcriptionResult;

    public TranslationResult(TranscriptionResult result,
                             String lang,
                             String translation)
    {
        transcriptionResult = result;
        language = lang;
        text = translation;
    }

    public String getLanguage()
    {
        return language;
    }

    public String getTranslatedText()
    {
        return text;
    }

    public TranscriptionResult getTranscriptionResult()
    {
        return transcriptionResult;
    }
}
