package org.diploma.transcription;

public class TranscriptionAlternative
{
    private static double DEFAULT_CONFIDENCE = 1.0D;

    private String transcription;

    private double confidence;

    public TranscriptionAlternative(String transcription, double confidence)
    {
        this.transcription = transcription;
        this.confidence = confidence;
    }

    public TranscriptionAlternative(String transcription)
    {
        this(transcription, DEFAULT_CONFIDENCE);
    }

    public String getTranscription()
    {
        return transcription;
    }

    public double getConfidence()
    {
        return confidence;
    }
}
