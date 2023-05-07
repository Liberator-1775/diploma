package org.diploma.transcription;

public interface TranscriptionEventListener
{
    public void notify(Transcriber transcriber, TranscriptEvent event);
}
