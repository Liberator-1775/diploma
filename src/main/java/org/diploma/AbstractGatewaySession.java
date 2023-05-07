package org.diploma;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import org.diploma.lobby.Lobby;
import org.diploma.lobby.*;
import org.jitsi.utils.*;
import org.jivesoftware.smack.packet.*;

import java.util.*;

public abstract class AbstractGatewaySession
    implements OperationSetJitsiMeetTools.JitsiMeetRequestListener,
               DTMFListener
{
    protected AbstractGateway gateway;

    protected CallContext callContext;

    protected JvbConference jvbConference;

    private final ArrayList<GatewaySessionListener> listeners
            = new ArrayList<>();

    private int participantsCount = 0;

    protected boolean gatewayMediaDropped = false;

    private static final String FOCUSE_RESOURCE_PROP
        = "org.diploma.FOCUS_RESOURCE";

    private final String focusResourceAddr;

    public AbstractGatewaySession(AbstractGateway gateway,
                                  CallContext callContext)
    {
        this.gateway = gateway;
        this.callContext = callContext;
        this.focusResourceAddr = JigasiBundleActivator.getConfigurationService()
            .getString(FOCUSE_RESOURCE_PROP, "focus");

    }

    public OrderedJsonObject getDebugState()
    {
        OrderedJsonObject debugState = new OrderedJsonObject();
        if (jvbConference != null)
        {
            debugState.put("jvbConference", jvbConference.getDebugState());
        }
        return debugState;
    }

    public void createOutgoingCall()
    {
        if (jvbConference != null)
        {
            throw new IllegalStateException("Conference in progress");
        }

        jvbConference = new JvbConference(this, callContext);
        jvbConference.start();
    }

    public CallContext getCallContext()
    {
        return callContext;
    }

    public String getMeetingUrl()
    {
        return jvbConference != null ? jvbConference.getMeetingUrl() : null;
    }

    public ChatRoom getJvbChatRoom()
    {
        return jvbConference != null ? jvbConference.getJvbRoom() : null;
    }

    public boolean isInTheRoom()
    {
        return jvbConference != null && jvbConference.isInTheRoom();
    }

    public JvbConference getJvbConference()
    {
        return jvbConference;
    }

    abstract void onConferenceCallInvited(Call incomingCall);

    abstract Exception onConferenceCallStarted(Call jvbConferenceCall);

    abstract void onJvbConferenceStopped(JvbConference jvbConference,
                                         int reasonCode, String reason);

    abstract void onJvbConferenceWillStop(JvbConference jvbConference,
        int reasonCode, String reason);

    public void onJvbCallEnded() {}

    public void onJvbCallEstablished() {}

    public void hangUp()
    {
        if (jvbConference != null)
        {
            jvbConference.stop();
        }
    }

    public void addListener(GatewaySessionListener listener)
    {
        synchronized(listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    public void removeListener(GatewaySessionListener listener)
    {
        synchronized(listeners)
        {
            listeners.remove(listener);
        }
    }

    void notifyJvbRoomJoined()
    {
        participantsCount += getJvbChatRoom().getMembersCount();

        Iterable<GatewaySessionListener> gwListeners;
        synchronized (listeners)
        {
            gwListeners = new ArrayList<>(listeners);
        }

        for (GatewaySessionListener listener : gwListeners)
        {
            listener.onJvbRoomJoined(this);
        }
    }

    public void notifyOnLobbyWaitReview(ChatRoom lobbyRoom)
    {
        Iterable<GatewaySessionListener> gwListeners;
        synchronized (listeners)
        {
            gwListeners = new ArrayList<>(listeners);
        }

        for (GatewaySessionListener listener : gwListeners)
        {
            listener.onLobbyWaitReview(lobbyRoom);
        }
    }

    void handleMaxOccupantsLimitReached()
    {}

    void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        participantsCount++;
    }

    void notifyChatRoomMemberLeft(ChatRoomMember member)
    {

    }

    void notifyChatRoomMemberUpdated(ChatRoomMember member, Presence presence)
    {

    }

    public int getParticipantsCount()
    {
        return participantsCount;
    }

    public abstract String getMucDisplayName();

    public abstract boolean isTranslatorSupported();

    public abstract String getDefaultInitStatus();

    public AbstractGateway getGateway()
    {
        return gateway;
    }

    void notifyConferenceMemberJoined(ConferenceMember conferenceMember)
    {

    }

    void notifyConferenceMemberLeft(ConferenceMember conferenceMember)
    {

    }

    public boolean isGatewayMediaDropped()
    {
        return gatewayMediaDropped;
    }

    public String getFocusResourceAddr()
    {
        return focusResourceAddr;
    }

    public abstract boolean hasCallResumeSupport();
}
