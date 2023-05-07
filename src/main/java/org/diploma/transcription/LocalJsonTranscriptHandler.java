package org.diploma.transcription;

import net.java.sip.communicator.service.protocol.*;
import org.json.*;

import java.time.*;
import java.util.*;

public class LocalJsonTranscriptHandler
    extends AbstractTranscriptPublisher<JSONObject>
{
    public final static String JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME
        = "room_name";

    public final static String JSON_KEY_FINAL_TRANSCRIPT_ROOM_URL
        = "room_url";

    public final static String JSON_KEY_FINAL_TRANSCRIPT_EVENTS = "events";

    public final static String JSON_KEY_FINAL_TRANSCRIPT_INITIAL_PARTICIPANTS
        = "initial_participants";

    public final static String JSON_KEY_FINAL_TRANSCRIPT_START_TIME =
        "start_time";

    public final static String JSON_KEY_FINAL_TRANSCRIPT_END_TIME =
        "end_time";


    public final static String JSON_KEY_EVENT_EVENT_TYPE = "event";

    public final static String JSON_KEY_EVENT_TIMESTAMP = "timestamp";

    public final static String JSON_KEY_EVENT_PARTICIPANT = "participant";

    public final static String JSON_KEY_EVENT_TRANSCRIPT = "transcript";

    public final static String JSON_KEY_EVENT_LANGUAGE = "language";

    public final static String JSON_KEY_EVENT_MESSAGE_ID = "message_id";

    public final static String JSON_KEY_EVENT_IS_INTERIM = "is_interim";

    public final static String JSON_KEY_EVENT_STABILITY = "stability";

    public final static String JSON_KEY_ALTERNATIVE_TEXT = "text";

    public final static String JSON_KEY_ALTERNATIVE_CONFIDENCE = "confidence";

    public final static String JSON_KEY_PARTICIPANT_NAME = "name";

    public final static String JSON_KEY_PARTICIPANT_ID = "id";

    public final static String JSON_KEY_PARTICIPANT_EMAIL = "email";

    public final static String JSON_KEY_PARTICIPANT_AVATAR_URL = "avatar_url";

    public final static String JSON_KEY_PARTICIPANT_IDENTITY_USERNAME
        = "identity_name";

    public final static String JSON_KEY_PARTICIPANT_IDENTITY_USERID
        = "identity_id";

    public final static String JSON_KEY_PARTICIPANT_IDENTITY_GROUPID
        = "identity_group_id";

    public final static String JSON_KEY_TYPE = "type";

    public final static String JSON_VALUE_TYPE_TRANSCRIPTION_RESULT
        = "transcription-result";

    public final static String JSON_VALUE_TYPE_TRANSLATION_RESULT
        = "translation-result";

    @Override
    public JSONFormatter getFormatter()
    {
        return new JSONFormatter();
    }

    @Override
    public void publish(ChatRoom room, TranscriptionResult result)
    {
        JSONObject eventObject = createTranscriptionJSONObject(result);

        super.sendJsonMessage(room, eventObject);
    }

    @Override
    public void publish(ChatRoom room, TranslationResult result)
    {
        JSONObject eventObject = createTranslationJSONObject(result);

        super.sendJsonMessage(room, eventObject);
    }

    public static JSONObject createTranscriptionJSONObject(
        TranscriptionResult result)
    {
        JSONObject eventObject = new JSONObject();
        SpeechEvent event = new SpeechEvent(result);

        addEventDescriptions(eventObject, event);
        addAlternatives(eventObject, event);

        eventObject.put(JSON_KEY_TYPE, JSON_VALUE_TYPE_TRANSCRIPTION_RESULT);

        return eventObject;
    }

    private static JSONObject createTranslationJSONObject(
        TranslationResult result)
    {
        JSONObject eventObject = new JSONObject();
        SpeechEvent event = new SpeechEvent(result.getTranscriptionResult());

        addEventDescriptions(eventObject, event);

        eventObject.put(JSON_KEY_TYPE, JSON_VALUE_TYPE_TRANSLATION_RESULT);
        eventObject.put(JSON_KEY_EVENT_LANGUAGE, result.getLanguage());
        eventObject.put(JSON_KEY_ALTERNATIVE_TEXT, result.getTranslatedText());
        eventObject.put(JSON_KEY_EVENT_MESSAGE_ID,
                result.getTranscriptionResult().getMessageID().toString());

        return eventObject;
    }
    @Override
    public Promise getPublishPromise()
    {
        return new JSONPublishPromise();
    }

    @Override
    protected JSONObject formatSpeechEvent(SpeechEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        addAlternatives(object, e);
        return object;
    }

    @Override
    protected JSONObject formatJoinEvent(TranscriptEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        return object;
    }

    @Override
    protected JSONObject formatLeaveEvent(TranscriptEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        return object;
    }

    @Override
    protected JSONObject formatRaisedHandEvent(TranscriptEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        return object;
    }

    public static void addEventDescriptions(
        JSONObject jsonObject, TranscriptEvent e)
    {
        jsonObject.put(JSON_KEY_EVENT_EVENT_TYPE, e.getEvent().toString());
        jsonObject.put(JSON_KEY_EVENT_TIMESTAMP, String.valueOf(e.getTimeStamp().toEpochMilli()));

        JSONObject participantJson = new JSONObject();

        addParticipantDescription(participantJson, e.getParticipant());

        jsonObject.put(JSON_KEY_EVENT_PARTICIPANT, participantJson);
    }

    private static void addAlternatives(JSONObject jsonObject, SpeechEvent e)
    {
        TranscriptionResult result = e.getResult();
        JSONArray alternativeJSONArray = new JSONArray();

        for (TranscriptionAlternative alternative : result.getAlternatives())
        {
            JSONObject alternativeJSON = new JSONObject();

            alternativeJSON.put(JSON_KEY_ALTERNATIVE_TEXT,
                alternative.getTranscription());
            alternativeJSON.put(JSON_KEY_ALTERNATIVE_CONFIDENCE,
                alternative.getConfidence());

            alternativeJSONArray.put(alternativeJSON);
        }

        jsonObject.put(JSON_KEY_EVENT_TRANSCRIPT, alternativeJSONArray);
        jsonObject.put(JSON_KEY_EVENT_LANGUAGE, result.getLanguage());
        jsonObject.put(JSON_KEY_EVENT_IS_INTERIM, result.isInterim());
        jsonObject.put(JSON_KEY_EVENT_MESSAGE_ID,
            result.getMessageID().toString());
        jsonObject.put(JSON_KEY_EVENT_STABILITY, result.getStability());
    }


    private static void addParticipantDescription(JSONObject pJSON,
                                                  Participant participant)
    {
        pJSON.put(JSON_KEY_PARTICIPANT_NAME, participant.getName());
        pJSON.put(JSON_KEY_PARTICIPANT_ID, participant.getId());

        String email = participant.getEmail();
        if (email != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_EMAIL, email);
        }

        String avatarUrl = participant.getAvatarUrl();
        if (avatarUrl != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_AVATAR_URL, avatarUrl);
        }

        String identityUsername = participant.getIdentityUserName();
        if (identityUsername != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_IDENTITY_USERNAME, identityUsername);
        }

        String identityUserId = participant.getIdentityUserId();
        if (identityUserId != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_IDENTITY_USERID, identityUserId);
        }

        String identityGroupId = participant.getIdentityGroupId();
        if (identityGroupId != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_IDENTITY_GROUPID, identityGroupId);
        }
    }

    private void addTranscriptDescription(JSONObject jsonObject,
                                          String roomName,
                                          String roomUrl,
                                          Collection<Participant> participants,
                                          Instant start,
                                          Instant end,
                                          Collection<JSONObject> events)
    {
        if (roomName != null && !roomName.isEmpty())
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME, roomName);
        }
        if (roomUrl != null && !roomUrl.isEmpty())
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_ROOM_URL, roomUrl);
        }
        if (start != null)
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_START_TIME,
                start.toString());
        }
        if (end != null)
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_END_TIME, end.toString());
        }
        if (participants != null && !participants.isEmpty())
        {
            JSONArray participantArray = new JSONArray();

            for (Participant participant : participants)
            {
                JSONObject pJSON = new JSONObject();

                addParticipantDescription(pJSON, participant);

                participantArray.put(pJSON);
            }

            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_INITIAL_PARTICIPANTS,
                participantArray);
        }
        if (events != null && !events.isEmpty())
        {
            JSONArray eventArray = new JSONArray();

            events.forEach(eventArray::put);

            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_EVENTS, eventArray);
        }
    }

    private class JSONFormatter
        extends BaseFormatter
    {
        @Override
        public JSONObject finish()
        {
            JSONObject transcript = new JSONObject();

            addTranscriptDescription(
                transcript,
                super.roomName,
                super.roomUrl,
                super.initialMembers,
                super.startInstant,
                super.endInstant,
                super.getSortedEvents());

            return transcript;
        }
    }

    private class JSONPublishPromise
        extends BasePromise
    {

        private final String fileName
            = generateHardToGuessTimeString("transcript", ".json");

        @Override
        protected void doPublish(Transcript transcript)
        {
            JSONObject t
                = transcript.getTranscript(LocalJsonTranscriptHandler.this);

            saveTranscriptStringToFile(getDirPath(), fileName,
                t.toString());
        }

        @Override
        public String getDescription()
        {
            return String.format("Transcript will be saved in %s/%s/%s%n",
                getBaseURL(), getDirPath(), fileName);
        }
    }
}
