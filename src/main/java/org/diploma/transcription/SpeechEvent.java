package org.diploma.transcription;

public class SpeechEvent
    extends TranscriptEvent
{
    private TranscriptionResult result;

    SpeechEvent(TranscriptionResult result)
    {
        super(result.getTimeStamp(), result.getParticipant(), Transcript.TranscriptEventType.SPEECH);
        this.result = result;
    }

    public TranscriptionResult getResult()
    {
        return result;
    }
}

