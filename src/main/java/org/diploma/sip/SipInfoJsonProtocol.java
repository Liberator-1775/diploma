package org.diploma.sip;

import net.java.sip.communicator.service.protocol.*;

import org.jitsi.utils.logging.Logger;
import org.json.simple.*;

import java.util.*;

public class SipInfoJsonProtocol
{
    private final static Logger logger = Logger.getLogger(SipInfoJsonProtocol.class);

    public static class MESSAGE_TYPE
    {
        public static final int LOBBY_JOINED = 3;
        public static final int REQUEST_ROOM_ACCESS = 4;
        public static final int LOBBY_LEFT = 5;
        public static final int LOBBY_ALLOWED_JOIN = 6;
        public static final int LOBBY_REJECTED_JOIN = 7;
        public static final int AV_MODERATION_ENABLED = 8;
        public static final int AV_MODERATION_APPROVED = 9;
        public static final int AV_MODERATION_DENIED = 10;
        public static final int SIP_CALL_HEARTBEAT = 11;
    }

    private static class MESSAGE_HEADER
    {
        public static final String MESSAGE_ID = "i";
        public static final String MESSAGE_TYPE = "t";
        public static final String MESSAGE_DATA = "d";
    }

    private int messageCount = 0;

    private final OperationSetJitsiMeetTools jitsiMeetTools;

    public SipInfoJsonProtocol(OperationSetJitsiMeetTools jmt)
    {
        jitsiMeetTools = jmt;
    }

    private int getMessageCount()
    {
        return messageCount++;
    }

    public void sendJson(CallPeer callPeer, JSONObject jsonObject)
        throws OperationFailedException
    {
        try
        {
            jitsiMeetTools.sendJSON(callPeer, jsonObject,
                new HashMap<String, Object>(){{ put("VIA", "SIP.INFO"); }});
        }
        catch (Exception ex)
        {
            int msgId = -1;
            if (jsonObject.containsKey(MESSAGE_HEADER.MESSAGE_ID))
            {
                msgId = (int)jsonObject.get(MESSAGE_HEADER.MESSAGE_ID);
            }

            logger.error("Error when sending message " + msgId);
            throw ex;
        }
    }

    public static String getPasswordFromRoomAccessRequest(JSONObject request)
    {
        String roomPwd = null;
        if (request.containsKey(MESSAGE_HEADER.MESSAGE_DATA))
        {
            JSONObject jsonData = (JSONObject)request.get(MESSAGE_HEADER.MESSAGE_DATA);
            if (jsonData.containsKey("pwd"))
            {
                roomPwd = (String)jsonData.get("pwd");
            }
        }
        return roomPwd;
    }

    public JSONObject createLobbyJoinedNotification()
    {
        JSONObject lobbyInitJson = new JSONObject();
        lobbyInitJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyInitJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_JOINED);
        return lobbyInitJson;
    }

    public JSONObject createLobbyLeftNotification()
    {
        JSONObject lobbyLeftJson = new JSONObject();
        lobbyLeftJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyLeftJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_LEFT);
        return lobbyLeftJson;
    }

    public JSONObject createLobbyAllowedJoinNotification()
    {
        JSONObject lobbyAllowedJson = new JSONObject();
        lobbyAllowedJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyAllowedJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_ALLOWED_JOIN);
        return lobbyAllowedJson;
    }

    public JSONObject createLobbyRejectedJoinNotification()
    {
        JSONObject lobbyRejectedJson = new JSONObject();
        lobbyRejectedJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyRejectedJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_REJECTED_JOIN);
        return lobbyRejectedJson;
    }

    private static JSONObject createSIPJSON(String type, JSONObject data, String id)
    {
        JSONObject req = new JSONObject();
        req.put("type", type);
        req.put("data", data);
        req.put("id", id == null ? UUID.randomUUID().toString() : id);
        return req;
    }

    public static JSONObject createSIPJSONAudioMuteResponse(boolean muted,
                                                      boolean bSucceeded,
                                                      String id)
    {
        JSONObject muteSettingsJson = new JSONObject();
        muteSettingsJson.put("audio", muted);
        JSONObject muteResponseJson = createSIPJSON("muteResponse", muteSettingsJson, id);
        muteResponseJson.put("status", bSucceeded ? "OK" : "FAILED");
        return muteResponseJson;
    }

    public static JSONObject createSIPJSONAudioMuteRequest(boolean muted)
    {
        JSONObject muteSettingsJson = new JSONObject();
        muteSettingsJson.put("audio", muted);

        return createSIPJSON("muteRequest", muteSettingsJson, null);
    }

    public static JSONObject createAVModerationEnabledNotification(boolean value)
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.AV_MODERATION_ENABLED);
        obj.put(MESSAGE_HEADER.MESSAGE_DATA, value);

        return obj;
    }

    public static JSONObject createAVModerationApprovedNotification()
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.AV_MODERATION_APPROVED);

        return obj;
    }

    public static JSONObject createAVModerationDeniedNotification()
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.AV_MODERATION_DENIED);

        return obj;
    }

    public static JSONObject createSIPCallHeartBeat()
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.SIP_CALL_HEARTBEAT);

        return obj;
    }
}
