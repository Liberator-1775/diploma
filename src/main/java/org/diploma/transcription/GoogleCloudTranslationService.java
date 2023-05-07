package org.diploma.transcription;

import com.google.cloud.translate.*;
import com.google.cloud.translate.Translate.*;

public class GoogleCloudTranslationService
    implements TranslationService
{
    private static final TranslateOption model = TranslateOption.model("nmt");

    private static final Translate translator
        = TranslateOptions.getDefaultInstance().getService();

    @Override
    public String translate(String sourceText,
                            String sourceLang, String targetLang)
    {
        TranslateOption srcLang = TranslateOption.sourceLanguage(sourceLang);
        TranslateOption tgtLang = TranslateOption.targetLanguage(targetLang);

        Translation translation = translator.translate(
            sourceText, srcLang, tgtLang, model);

        return translation.getTranslatedText();
    }
}