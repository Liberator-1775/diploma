package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.jitsi.utils.logging.Logger;
import org.jivesoftware.smack.packet.*;
import org.json.simple.*;

import java.util.*;
import java.util.concurrent.*;

public class MockJitsiMeetTools
    implements OperationSetJitsiMeetTools, OperationSetJitsiMeetToolsJabber
{
    private final static Logger logger
        = Logger.getLogger(MockJitsiMeetTools.class);

    private final MockProtocolProvider protocolProvider;

    private final List<JitsiMeetRequestListener> requestHandlers
        = new CopyOnWriteArrayList<>();

    public MockJitsiMeetTools(MockProtocolProvider protocolProvider)
    {
        this.protocolProvider = protocolProvider;
    }

    public MockCall mockIncomingGatewayCall(String uri, String roomName)
    {
        return protocolProvider.getTelephony()
                .mockIncomingGatewayCall(uri, roomName);
    }

    @Override
    public void addRequestListener(JitsiMeetRequestListener requestHandler)
    {
        this.requestHandlers.add(requestHandler);
    }

    @Override
    public void removeRequestListener(JitsiMeetRequestListener requestHandler)
    {
        this.requestHandlers.remove(requestHandler);
    }

    public void notifyJoinJitsiMeetRoom(Call call, String jitsiMeetRoom)
    {
        boolean handled = false;
        for (JitsiMeetRequestListener l : requestHandlers)
        {
            l.onJoinJitsiMeetRequest(
                call, jitsiMeetRoom, new HashMap<>());
            handled = true;
        }
        if (!handled)
        {
            logger.warn(
                "Unhandled join Jitsi Meet request R:" + jitsiMeetRoom
                    + " C: " + call);
        }
    }

    @Override
    public void sendJSON(CallPeer callPeer, JSONObject jsonObject, Map<String, Object> map)
    {
    }

    @Override
    public void addSupportedFeature(String featureName)
    {
        
    }

    @Override
    public void removeSupportedFeature(String featureName)
    {

    }

    @Override
    public void sendPresenceExtension(ChatRoom chatRoom,
        ExtensionElement extension)
    {

    }

    @Override
    public void removePresenceExtension(ChatRoom chatRoom,
        ExtensionElement extension)
    {

    }

    @Override
    public void setPresenceStatus(ChatRoom chatRoom, String statusMessage)
    {

    }
}
