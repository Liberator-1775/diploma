package org.diploma;

import net.java.sip.communicator.service.protocol.*;

import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.jxmpp.jid.*;
import org.osgi.framework.*;

import java.util.*;

public abstract class AbstractGateway<T extends AbstractGatewaySession>
    implements GatewaySessionListener<T>
{
    private final static Logger logger
        = Logger.getLogger(AbstractGateway.class);

    public static final String P_NAME_DEFAULT_JVB_ROOM
        = "org.diploma.DEFAULT_JVB_ROOM_NAME";

    public static final String P_NAME_JVB_INVITE_TIMEOUT
        = "org.diploma.JVB_INVITE_TIMEOUT";

    public static final String P_NAME_DISABLE_ICE
        = "org.diploma.DISABLE_ICE";

    public static final long DEFAULT_JVB_INVITE_TIMEOUT = 30L * 1000L;

    private final Map<CallContext, T> sessions = new HashMap<>();

    private BundleContext bundleContext;

    private final ArrayList<GatewayListener> gatewayListeners
        = new ArrayList<>();

    public AbstractGateway(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
    }

    public abstract void stop();

    public abstract boolean isReady();

    public OrderedJsonObject getDebugState()
    {
        OrderedJsonObject debugState = new OrderedJsonObject();
        OrderedJsonObject sessionsJson = new OrderedJsonObject();
        debugState.put("sessions", sessionsJson);
        synchronized (sessions)
        {
            sessions.forEach((callContext, session) -> {
                String displayName = session.getMucDisplayName();
                if (displayName == null || displayName.trim() == "")
                {
                    displayName = Integer.toString(session.hashCode());
                }
                sessionsJson.put(displayName, session.getDebugState());
            });
        }

        return debugState;
    }

    void notifyCallEnded(CallContext callContext)
    {
        T session;

        synchronized (sessions)
        {
            session = sessions.remove(callContext);

            if (session == null)
            {
                logger.error(
                    callContext + " Call resource not exists for session.");
                return;
            }
        }

        fireGatewaySessionRemoved(session);

        logger.info(callContext
            + " Removed session for call. Sessions:" + sessions.size());
    }

    public abstract T createOutgoingCall(CallContext ctx);

    @Override
    public void onJvbRoomJoined(T source)
    {
        synchronized(sessions)
        {
            sessions.put(source.getCallContext(), source);
        }

        fireGatewaySessionAdded(source);
    }

    @Override
    public void onLobbyWaitReview(ChatRoom lobbyRoom)
    {}

    public T getSession(Jid callResource)
    {
        synchronized (sessions)
        {
            for (Map.Entry<CallContext, T> en
                : sessions.entrySet())
            {
                if (callResource.equals(en.getKey().getCallResource()))
                    return en.getValue();
            }

            return null;
        }
    }

    public List<T> getActiveSessions()
    {
        synchronized (sessions)
        {
            return new ArrayList<>(sessions.values());
        }
    }

    public static long getJvbInviteTimeout()
    {
        return JigasiBundleActivator.getConfigurationService()
                                    .getLong(P_NAME_JVB_INVITE_TIMEOUT,
                                             DEFAULT_JVB_INVITE_TIMEOUT);
    }

    public static void setJvbInviteTimeout(long newTimeout)
    {
        JigasiBundleActivator.getConfigurationService()
                             .setProperty(
                                     AbstractGateway.P_NAME_JVB_INVITE_TIMEOUT,
                                          newTimeout);
    }

    public void addGatewayListener(GatewayListener listener)
    {
        synchronized(gatewayListeners)
        {
            if (!gatewayListeners.contains(listener))
                gatewayListeners.add(listener);
        }
    }

    public void removeGatewayListener(GatewayListener listener)
    {
        synchronized(gatewayListeners)
        {
            gatewayListeners.remove(listener);
        }
    }

    private void fireGatewaySessionAdded(AbstractGatewaySession session)
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onSessionAdded(session);
        }
    }

    private void fireGatewaySessionRemoved(AbstractGatewaySession session)
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onSessionRemoved(session);
        }
    }

    void fireGatewaySessionFailed(AbstractGatewaySession session)
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onSessionFailed(session);
        }
    }

    void fireGatewayReady()
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onReady();
        }
    }

}
