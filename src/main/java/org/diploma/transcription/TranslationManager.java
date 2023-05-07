package org.diploma.transcription;

import java.util.*;

public class TranslationManager
    implements TranscriptionListener
{
    private final Map<String, Integer> languages = new HashMap<>();

    private final List<TranslationResultListener> listeners
        = new ArrayList<>();

    private final TranslationService translationService;

    public TranslationManager(TranslationService service)
    {
        translationService = service;
    }

    public void addListener(TranslationResultListener listener)
    {
        synchronized(listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    public void addLanguage(String language)
    {
        if (language == null || language.isEmpty())
            return;

        synchronized(languages)
        {
            languages.put(language, languages.getOrDefault(language, 0) + 1);
        }
    }

    public void removeLanguage(String language)
    {
        if (language == null)
            return;

        synchronized(languages)
        {
            int count = languages.get(language);

            if (count == 1)
            {
                languages.remove(language);
            }
            else
            {
                languages.put(language, count - 1);
            }
        }
    }

    private List<TranslationResult> getTranslations(
        TranscriptionResult result)
    {
        ArrayList<TranslationResult> translatedResults
            = new ArrayList<>();
        Set<String> translationLanguages;

        synchronized (languages)
        {
            translationLanguages = languages.keySet();
        }

        Collection<TranscriptionAlternative> alternatives
            = result.getAlternatives();

        if (!alternatives.isEmpty())
        {
            for (String targetLanguage : translationLanguages)
            {
                String translatedText = translationService.translate(
                    alternatives.iterator().next().getTranscription(),
                    result.getParticipant().getSourceLanguage(),
                    targetLanguage);

                translatedResults.add(new TranslationResult(
                    result,
                    targetLanguage,
                    translatedText));
            }
        }

        return translatedResults;
    }

    @Override
    public void notify(TranscriptionResult result)
    {
        if (!result.isInterim())
        {
            List<TranslationResult> translations
                = getTranslations(result);
            Iterable<TranslationResultListener> translationResultListeners;

            synchronized (listeners)
            {
                translationResultListeners = new ArrayList<>(listeners);
            }

            translationResultListeners.forEach(
                listener -> translations.forEach(listener::notify));
        }
    }

    @Override
    public void completed()
    {
        languages.clear();
    }

    @Override
    public void failed(FailureReason reason)
    {
        completed();
    }
}
