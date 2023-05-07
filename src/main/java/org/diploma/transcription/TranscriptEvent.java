package org.diploma.transcription;

import java.time.*;

public class TranscriptEvent
    implements Comparable<TranscriptEvent>
{
    private Instant timeStamp;

    private Participant participant;

    private Transcript.TranscriptEventType event;

    TranscriptEvent(Instant timeStamp, Participant participant,
                    Transcript.TranscriptEventType event)
    {
        this.timeStamp = timeStamp;
        this.participant = participant;
        this.event = event;
    }

    TranscriptEvent(Instant timeStamp, Transcript.TranscriptEventType event)
    {
        if (Transcript.TranscriptEventType.END.equals(event)
            || Transcript.TranscriptEventType.WILL_END.equals(event)
            || Transcript.TranscriptEventType.START.equals(event))
        {
            this.timeStamp = timeStamp;
            this.event = event;
        }
        else
        {
            throw new IllegalArgumentException("TranscriptEvent " + event +
                " needs a participant");
        }
    }

    public Instant getTimeStamp()
    {
        return timeStamp;
    }

    public String getName()
    {
        return participant.getName();
    }

    public String getID()
    {
        return participant.getId();
    }

    public Transcript.TranscriptEventType getEvent()
    {
        return event;
    }

    @Override
    public int compareTo(TranscriptEvent other)
        throws NullPointerException
    {
        return this.timeStamp.compareTo(other.timeStamp);
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof TranscriptEvent &&
            this.timeStamp.equals(((TranscriptEvent) obj).timeStamp);
    }

    public Participant getParticipant()
    {
        return participant;
    }
}
