package org.diploma.transcription;

public interface TranscriptionListener
{
    void notify(TranscriptionResult result);

    void completed();

    void failed(FailureReason reason);

    enum FailureReason
    {
        RESOURCES_EXHAUSTED
    }
}
