package org.diploma.transcription.action;

import net.java.sip.communicator.util.osgi.ServiceUtils;
import org.diploma.transcription.*;
import org.diploma.transcription.*;
import org.jitsi.service.configuration.*;
import org.jitsi.utils.logging.Logger;
import org.json.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class ActionServicesHandler
{
    private final static Logger logger
        = Logger.getLogger(ActionServicesHandler.class);

    private static final String ACTION_PROPS_PREFIX
        = "org.diploma.transcription.action";

    private static final String ACTION_PHRASE_PROP_NAME = "PHRASE";

    private static final String ACTION_URL_PROP_NAME = "URL";

    private static ActionServicesHandler serviceHandlerInstance = null;

    private List<ActionHandler> actions = new ArrayList<>();

    private Map<Pattern, ActionHandler> patterns = new HashMap<>();

    private Map<String, List<ActionHandler>> actionSources = new HashMap<>();

    private ActionServicesHandler(BundleContext ctx)
    {
        ConfigurationService config =
            ServiceUtils.getService(ctx, ConfigurationService.class);

        List<String> actionProps =
            config.getPropertyNamesByPrefix(ACTION_PROPS_PREFIX, false);

        Set<String> actionNames = new HashSet<>();
        for (String prop : actionProps)
        {
            prop = prop.substring(ACTION_PROPS_PREFIX.length() + 1);
            prop = prop.substring(0, prop.indexOf('.'));
            actionNames.add(prop);
        }

        for (String actionName : actionNames)
        {
            String ph = config.getString(ACTION_PROPS_PREFIX
                + "." + actionName + "." + ACTION_PHRASE_PROP_NAME);
            String url = config.getString(ACTION_PROPS_PREFIX
                + "." + actionName + "." + ACTION_URL_PROP_NAME);

            ActionHandler handler = new ActionHandler(actionName, ph, url);
            actions.add(handler);
            patterns.put(
                Pattern.compile(Pattern.quote(ph), Pattern.CASE_INSENSITIVE),
                handler);
        }
    }

    public static ActionServicesHandler getInstance()
    {
        return serviceHandlerInstance;
    }

    public static ActionServicesHandler init(BundleContext ctx)
    {
        return serviceHandlerInstance = new ActionServicesHandler(ctx);
    }

    public void stop()
    {

    }

    public List<String> getPhrases()
    {
        return actions.stream()
            .map(a -> a.getPhrase())
            .collect(Collectors.toList());
    }

    public void notifyActionServices(TranscriptionResult result)
    {
        for (Map.Entry<Pattern, ActionHandler> en : patterns.entrySet())
        {
            TranscriptionAlternative alt
                = result.getAlternatives().iterator().next();
            String msg = alt.getTranscription();
            Matcher match = en.getKey().matcher(msg);
            if (match.find())
            {
                String newText = msg.substring(match.end()).trim();
                result = new TranscriptionResult(
                    result.getParticipant(),
                    result.getMessageID(),
                    result.getTimeStamp(),
                    result.isInterim(),
                    result.getLanguage(),
                    result.getStability(),
                    new TranscriptionAlternative(newText, alt.getConfidence()));

                JSONObject jsonResult =
                    LocalJsonTranscriptHandler.createTranscriptionJSONObject(result);
                String roomName
                    = result.getParticipant().getTranscriber().getRoomName();
                jsonResult.put(
                    LocalJsonTranscriptHandler
                        .JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
                    roomName);


                ActionHandler handler = en.getValue();
                if (logger.isDebugEnabled())
                {
                    logger.debug("Action detected:" + handler.getName()
                        + ", will push to address:" + handler.getUrl());
                }

                if (!actionSources.containsKey(roomName))
                {
                    List<ActionHandler> handlers = new ArrayList<>();
                    handlers.add(handler);
                    actionSources.put(roomName, handlers);
                }
                else
                {
                    List<ActionHandler> handlers = actionSources.get(roomName);
                    if (!handlers.contains(handler))
                    {
                        handlers.add(handler);
                    }
                }

                Util.postJSON(handler.getUrl(), jsonResult);
            }
        }
    }

    public void notifyActionServices(
            Transcriber transcriber, TranscriptEvent event)
    {
        String roomName = transcriber.getRoomName();

        if (event.getEvent() != Transcript.TranscriptEventType.END
            || !actionSources.containsKey(roomName))
            return;

        JSONObject object = new JSONObject();
        object.put(
            LocalJsonTranscriptHandler
                .JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
            roomName);
        object.put(LocalJsonTranscriptHandler.JSON_KEY_EVENT_EVENT_TYPE,
            event.getEvent().toString());
        object.put(LocalJsonTranscriptHandler.JSON_KEY_EVENT_TIMESTAMP,
            event.getTimeStamp().toString());

        for (ActionHandler handler : actionSources.remove(roomName))
        {
            Util.postJSON(handler.getUrl(), object);
        }
    }
}
