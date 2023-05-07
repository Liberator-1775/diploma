package org.diploma.transcription;

import java.time.*;
import java.util.*;

public class Transcript
    implements TranscriptionListener
{
    public enum TranscriptEventType
    {
        START,
        SPEECH,
        JOIN,
        LEAVE,
        RAISE_HAND,
        WILL_END,
        END
    }

    private final List<SpeechEvent> speechEvents = new LinkedList<>();

    private final List<TranscriptEvent> joinedEvents = new LinkedList<>();

    private final List<TranscriptEvent> leftEvents = new LinkedList<>();

    private final List<TranscriptEvent> raisedHandEvents = new LinkedList<>();

    private TranscriptEvent started;

    private TranscriptEvent ended;

    private List<Participant> initialParticipantNames = new LinkedList<>();

    private String roomName;

    private String roomUrl;

    Transcript(String roomName, String roomUrl)
    {
        this.roomName = roomName;
        this.roomUrl = roomUrl;
    }

    Transcript()
    {
        this.roomName = "";
        this.roomUrl = "";
    }

    @Override
    public void notify(TranscriptionResult result)
    {
        if (started != null && !result.isInterim())
        {
            SpeechEvent speechEvent = new SpeechEvent(result);
            speechEvents.add(speechEvent);
        }
    }

    @Override
    public void completed()
    {

    }

    @Override
    public void failed(FailureReason reason)
    {

    }

    public TranscriptEvent started(List<Participant> initialParticipants)
    {
        if (started == null)
        {
            this.started
                = new TranscriptEvent(Instant.now(), TranscriptEventType.START);
            this.initialParticipantNames.addAll(initialParticipants);

            return this.started;
        }

        return null;
    }

    protected TranscriptEvent started(String roomName,
                                      String roomUrl,
                                      List<Participant> initialParticipants)
    {
        if (started == null)
        {
            this.roomName = roomName;
            this.roomUrl = roomUrl;
            this.started
                = new TranscriptEvent(Instant.now(), TranscriptEventType.START);
            this.initialParticipantNames.addAll(initialParticipants);

            return this.started;
        }

        return null;
    }

    protected TranscriptEvent ended()
    {
        if (started != null && ended == null)
        {
            this.ended
                = new TranscriptEvent(Instant.now(), TranscriptEventType.END);
        }

        return this.ended;
    }

    public TranscriptEvent willEnd()
    {
        if (started != null && ended == null)
        {
            return new TranscriptEvent(
                Instant.now(), TranscriptEventType.WILL_END);
        }

        return null;
    }

    public synchronized TranscriptEvent notifyJoined(Participant participant)
    {
        if (started != null && ended == null)
        {
            for (TranscriptEvent ev : joinedEvents)
            {
                if (ev.getParticipant().equals(participant))
                    return null;
            }

            TranscriptEvent event = new TranscriptEvent(
                Instant.now(), participant, TranscriptEventType.JOIN);
            joinedEvents.add(event);

            return event;
        }

        return null;
    }

    public TranscriptEvent notifyLeft(Participant participant)
    {
        if (started != null && ended == null)
        {
            TranscriptEvent event = new TranscriptEvent(
                Instant.now(), participant, TranscriptEventType.LEAVE);
            leftEvents.add(event);

            return event;
        }

        return null;
    }

    public TranscriptEvent notifyRaisedHand(Participant participant)
    {
        if (started != null && ended == null)
        {
            TranscriptEvent event = new TranscriptEvent(
                Instant.now(), participant, TranscriptEventType.RAISE_HAND);
            raisedHandEvents.add(event);

            return event;
        }

        return null;
    }

    public <T> T getTranscript(AbstractTranscriptPublisher<T> publisher)
    {
        return publisher.getFormatter()
            .startedOn(started)
            .initialParticipants(initialParticipantNames)
            .tookPlaceInRoom(roomName)
            .tookPlaceAtUrl(roomUrl)
            .speechEvents(speechEvents)
            .raiseHandEvents(raisedHandEvents)
            .joinEvents(joinedEvents)
            .leaveEvents(leftEvents)
            .endedOn(ended)
            .finish();
    }
}
