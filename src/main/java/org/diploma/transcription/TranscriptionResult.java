package org.diploma.transcription;

import java.time.*;
import java.util.*;

public class TranscriptionResult
{
    private Participant participant;

    private final List<TranscriptionAlternative> alternatives
        = new LinkedList<>();

    final private UUID messageID;

    final private Instant timeStamp;

    final private boolean isInterim;

    final private String language;

    final private double stability;

    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        Instant timeStamp,
        boolean isInterim,
        String language,
        double stability,
        TranscriptionAlternative alternative)
    {
        this(participant, messageID, timeStamp, isInterim, language, stability);
        if (alternative != null)
        {
            this.alternatives.add(alternative);
        }
    }

    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        Instant timeStamp,
        boolean isInterim,
        String language,
        double stability,
        Collection<TranscriptionAlternative> alternatives)
    {
        this(participant, messageID, timeStamp, isInterim, language, stability);
        if (alternatives != null)
        {
            this.alternatives.addAll(alternatives);
        }
    }

    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        Instant timeStamp,
        boolean isInterim,
        String language,
        double stability)
    {
        this.participant = participant;
        this.messageID = messageID;
        this.timeStamp = timeStamp;
        this.isInterim = isInterim;
        this.language = language;
        this.stability = stability;
    }

    public void addAlternative(TranscriptionAlternative alternative)
    {
        alternatives.add(alternative);
    }

    public Collection<TranscriptionAlternative> getAlternatives()
    {
        return alternatives;
    }

    public String getName()
    {
        return participant == null
            ? Participant.UNKNOWN_NAME
            : participant.getName();
    }

    public UUID getMessageID()
    {
        return messageID;
    }

    public boolean isInterim()
    {
        return isInterim;
    }

    public double getStability()
    {
        return stability;
    }

    public String getLanguage()
    {
        return language;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(getName());
        sb.append(": ");
        if (!alternatives.isEmpty())
        {
            sb.append('[')
                .append(alternatives.get(0).getConfidence())
                .append("] ")
                .append(alternatives.get(0).getTranscription());
        }
        return sb.toString();
    }

    public void setParticipant(Participant participant)
    {
        this.participant = participant;
    }

    public Participant getParticipant()
    {
        return participant;
    }

    public Instant getTimeStamp()
    {
        return timeStamp;
    }
}
