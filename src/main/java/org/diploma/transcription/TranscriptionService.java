package org.diploma.transcription;

import java.util.function.*;

public interface TranscriptionService
{
    boolean supportsFragmentTranscription();

    void sendSingleRequest(TranscriptionRequest request,
                           Consumer<TranscriptionResult> resultConsumer)
        throws UnsupportedOperationException;

    boolean supportsStreamRecognition();

    boolean supportsLanguageRouting();

    StreamingRecognitionSession initStreamingSession(Participant participant)
        throws UnsupportedOperationException;

    boolean isConfiguredProperly();

    interface StreamingRecognitionSession
    {
        void sendRequest(TranscriptionRequest request);

        void end();

        boolean ended();

        void addTranscriptionListener(TranscriptionListener listener);
    }
}
