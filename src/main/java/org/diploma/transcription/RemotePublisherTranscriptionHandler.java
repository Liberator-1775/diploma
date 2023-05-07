package org.diploma.transcription;

import net.java.sip.communicator.service.protocol.*;
import org.json.*;

import java.util.*;

public class RemotePublisherTranscriptionHandler
    extends LocalJsonTranscriptHandler
    implements TranscriptionEventListener
{
    private List<String> urls = new ArrayList<>();

    public RemotePublisherTranscriptionHandler(String urlsStr)
    {
        super();

        StringTokenizer tokens = new StringTokenizer(urlsStr, ",");
        while (tokens.hasMoreTokens())
        {
            urls.add(tokens.nextToken().trim());
        }
    }

    @Override
    public void publish(ChatRoom room, TranscriptionResult result)
    {
        if (result.isInterim())
            return;

        JSONObject eventObject = createTranscriptionJSONObject(result);

        eventObject.put(
                JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
            result.getParticipant().getTranscriber().getRoomName());

        eventObject.put(
                JSON_KEY_EVENT_EVENT_TYPE,
            Transcript.TranscriptEventType.SPEECH.toString());

        for (String url : urls)
        {
            Util.postJSON(url, eventObject);
        }
    }

    @Override
    public void notify(Transcriber transcriber, TranscriptEvent event)
    {
        JSONObject object = new JSONObject();
        object.put(
            JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
            transcriber.getRoomName());

        if (event.getEvent() == Transcript.TranscriptEventType.JOIN
            || event.getEvent() == Transcript.TranscriptEventType.LEAVE)
        {
            addEventDescriptions(object, event);
        }
        else if (event.getEvent() == Transcript.TranscriptEventType.START
            || event.getEvent() == Transcript.TranscriptEventType.END)
        {
            object.put(JSON_KEY_EVENT_EVENT_TYPE,
                event.getEvent().toString());
            object.put(JSON_KEY_EVENT_TIMESTAMP,
                event.getTimeStamp().toString());
        }

        for (String url : urls)
        {
            Util.postJSON(url, object);
        }
    }
}
