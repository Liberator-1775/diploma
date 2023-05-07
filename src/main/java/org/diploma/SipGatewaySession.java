package org.diploma;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.apache.commons.lang3.StringUtils;
import org.diploma.sip.SipInfoJsonProtocol;
import org.diploma.sounds.SoundNotificationManager;
import org.diploma.stats.Statistics;
import org.diploma.util.Util;
import org.jitsi.impl.neomedia.*;
import org.diploma.sip.*;
import org.diploma.sounds.*;
import org.diploma.stats.*;
import org.diploma.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.jivesoftware.smack.packet.*;
import org.json.simple.*;
import org.jxmpp.stringprep.*;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SipGatewaySession
    extends AbstractGatewaySession
{
    private final static Logger logger = Logger.getLogger(SipGatewaySession.class);

    private final String roomPassHeaderName;

    public static final String JITSI_MEET_ROOM_PASS_HEADER_DEFAULT
        = "Jitsi-Conference-Room-Pass";

    private static final String JITSI_MEET_ROOM_PASS_HEADER_PROPERTY
        = "JITSI_MEET_ROOM_PASS_HEADER_NAME";

    private static final String JITSI_MEET_ROOM_HEADER_PROPERTY
        = "JITSI_MEET_ROOM_HEADER_NAME";

    private final String authTokenHeaderName;

    private static final String JITSI_AUTH_TOKEN_HEADER_PROPERTY
        = "JITSI_AUTH_TOKEN_HEADER_NAME";

    private static final String JITSI_AUTH_TOKEN_HEADER_NAME_DEFAULT
        = "Jitsi-Auth-Token";

    private final String authUserIdHeaderName;

    private static final String JITSI_AUTH_USER_ID_HEADER_PROPERTY
        = "JITSI_AUTH_USER_ID_HEADER_NAME";

    private static final String JITSI_AUTH_USER_ID_HEADER_NAME_DEFAULT
        = "Jitsi-User-ID-Token";

    private final String domainBaseHeaderName;

    public static final String JITSI_MEET_DOMAIN_BASE_HEADER_DEFAULT
        = "Jitsi-Conference-Domain-Base";

    private static final String JITSI_MEET_DOMAIN_BASE_HEADER_PROPERTY
        = "JITSI_MEET_DOMAIN_BASE_HEADER_NAME";

    private final String domainTenantHeaderName;

    public static final String JITSI_MEET_DOMAIN_TENANT_HEADER_DEFAULT
        = "Jitsi-Conference-Domain-Tenant";

    private static final String JITSI_MEET_DOMAIN_TENANT_HEADER_PROPERTY
        = "JITSI_MEET_DOMAIN_TENANT_HEADER_NAME";

    private static final String OUTBOUND_PREFIX_PROPERTY = "OUTBOUND_PREFIX";

    private final String outboundPrefix;

    private static final String INIT_STATUS_NAME = "Initializing Call";

    private static final String P_NAME_MEDIA_DROPPED_THRESHOLD_MS
        = "org.diploma.SIP_MEDIA_DROPPED_THRESHOLD_MS";

    private static final int DEFAULT_MEDIA_DROPPED_THRESHOLD = 10*1000;

    private static final String P_NAME_HANGUP_SIP_ON_MEDIA_DROPPED
        = "org.diploma.HANGUP_SIP_ON_MEDIA_DROPPED";

    private static final int mediaDroppedThresholdMs
        = JigasiBundleActivator.getConfigurationService().getInt(
            P_NAME_MEDIA_DROPPED_THRESHOLD_MS,
            DEFAULT_MEDIA_DROPPED_THRESHOLD);

    private static final RecurringRunnableExecutor EXECUTOR
        = new RecurringRunnableExecutor(ExpireMediaStream.class.getName());

    private final SoundNotificationManager soundNotificationManager
        = new SoundNotificationManager(this);

    private ExpireMediaStream expireMediaStream;

    private final OperationSetJitsiMeetTools jitsiMeetTools;

    private Call sipCall;

    private Call jvbConferenceCall;

    private final SipCallStateListener callStateListener
        = new SipCallStateListener();

    private CallPeerListener peerStateListener;

    private String destination;

    private final ProtocolProviderService sipProvider;

    private final Object waitLock = new Object();

    private WaitForJvbRoomNameThread waitThread;

    private SipCallKeepAliveTransformer transformerMonitor;

    private boolean callReconnectedStatsSent = false;

    private final SipInfoJsonProtocol sipInfoJsonProtocol;

    private final LinkedList<JSONObject> jsonToSendQueue = new LinkedList<>();

    private final Object jsonToSendLock = new Object();

    private final CallHeartbeat callHeartbeat = new CallHeartbeat();

    private ScheduledExecutorService heartbeatExecutor = null;

    private int heartbeatPeriodInSec = -1;

    private static final String HEARTBEAT_SECONDS_PROPERTY = "HEARTBEAT_SECONDS";

    public SipGatewaySession(SipGateway gateway,
                             CallContext callContext,
                             Call       sipCall)
    {
        this(gateway, callContext);
        this.sipCall = sipCall;
    }

    public SipGatewaySession(SipGateway gateway, CallContext callContext)
    {
        super(gateway, callContext);
        this.sipProvider = gateway.getSipProvider();
        this.jitsiMeetTools
            = sipProvider.getOperationSet(
                    OperationSetJitsiMeetTools.class);

        roomPassHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_MEET_ROOM_PASS_HEADER_PROPERTY,
                JITSI_MEET_ROOM_PASS_HEADER_DEFAULT);

        authTokenHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_AUTH_TOKEN_HEADER_PROPERTY,
                JITSI_AUTH_TOKEN_HEADER_NAME_DEFAULT);

        authUserIdHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_AUTH_USER_ID_HEADER_PROPERTY,
                JITSI_AUTH_USER_ID_HEADER_NAME_DEFAULT);

        domainBaseHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_MEET_DOMAIN_BASE_HEADER_PROPERTY,
                JITSI_MEET_DOMAIN_BASE_HEADER_DEFAULT);

        domainTenantHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_MEET_DOMAIN_TENANT_HEADER_PROPERTY,
                JITSI_MEET_DOMAIN_TENANT_HEADER_DEFAULT);

        heartbeatPeriodInSec = sipProvider.getAccountID()
            .getAccountPropertyInt(HEARTBEAT_SECONDS_PROPERTY, heartbeatPeriodInSec);

        outboundPrefix = sipProvider.getAccountID().getAccountPropertyString(OUTBOUND_PREFIX_PROPERTY, "");

        this.sipInfoJsonProtocol = new SipInfoJsonProtocol(jitsiMeetTools);
    }

    private void allCallsEnded()
    {
        CallContext ctx = super.callContext;

        super.gateway.notifyCallEnded(ctx);

        destination = null;
        callContext = null;
    }

    private void cancelWaitThread()
    {
        if (waitThread != null)
        {
            waitThread.cancel();
        }
    }

    public void createOutgoingCall()
    {
        if (sipCall != null)
        {
            throw new IllegalStateException("SIP call in progress");
        }

        this.destination = callContext.getDestination();

        super.createOutgoingCall();
    }

    public Call getSipCall()
    {
        return sipCall;
    }

    public void hangUp()
    {
        hangUp(-1, null);
    }

    private void hangUp(int reasonCode, String reason)
    {
        super.hangUp();
        hangUpSipCall(reasonCode, reason);
    }

    private void hangUpSipCall(int reasonCode, String reason)
    {
        cancelWaitThread();

        if (sipCall != null)
        {
            if (reasonCode != -1)
                CallManager.hangupCall(sipCall, reasonCode, reason);
            else
                CallManager.hangupCall(sipCall);
        }
    }

    private void joinJvbConference(CallContext ctx)
    {
        cancelWaitThread();
        jvbConference = new JvbConference(this, ctx);
        jvbConference.start();
    }

    void onConferenceCallInvited(Call incomingCall)
    {
        if (destination == null)
        {
            incomingCall.setConference(sipCall.getConference());
        }

        boolean useTranslator = incomingCall.getProtocolProvider().getAccountID()
            .getAccountPropertyBoolean(ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, false);
        CallPeer peer = incomingCall.getCallPeers().next();
        if (useTranslator && !addSsrcRewriter(peer))
        {
            peer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer peer = evt.getSourceCallPeer();
                    CallPeerState peerState = peer.getState();

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        peer.removeCallPeerListener(this);
                        addSsrcRewriter(peer);
                    }
                }
            });
        }

        Exception error = this.onConferenceCallStarted(incomingCall);

        if (error != null)
        {
            logger.error(this.callContext + " " + error, error);

            if (error instanceof OperationFailedException
                && !CallManager.isHealthy())
            {
                OperationFailedException ex = (OperationFailedException)error;
                hangUpSipCall(ex.getErrorCode(), ex.getMessage());
            }
        }
    }

    Exception onConferenceCallStarted(Call jvbConferenceCall)
    {
        this.jvbConferenceCall = jvbConferenceCall;

        if (destination == null)
        {
            try
            {
                CallManager.acceptCall(sipCall);
            }
            catch(OperationFailedException e)
            {
                hangUpSipCall(e.getErrorCode(), "Cannot answer call");

                return e;
            }
        }
        else
        {
            if (this.sipCall != null)
            {
                logger.info(
                    this.callContext +
                    " Connecting existing sip call to incoming xmpp call "
                        + this);

                jvbConferenceCall.setConference(sipCall.getConference());

                try
                {
                    CallManager.acceptCall(jvbConferenceCall);
                }
                catch(OperationFailedException e)
                {
                    return e;
                }

                if (!callReconnectedStatsSent)
                {
                    Statistics.incrementTotalCallsWithSipCallReconnected();
                    callReconnectedStatsSent = true;
                }

                return null;
            }

            final OperationSetBasicTelephony tele
                = sipProvider.getOperationSet(
                        OperationSetBasicTelephony.class);
            tele.addCallListener(new CallListener()
            {
                @Override
                public void incomingCallReceived(CallEvent callEvent)
                {}

                @Override
                public void outgoingCallCreated(CallEvent callEvent)
                {
                    String roomName = getCallContext().getRoomJid().toString();
                    if (roomName != null)
                    {
                        Call call = callEvent.getSourceCall();
                        AtomicInteger headerCount = new AtomicInteger(0);
                        call.setData("EXTRA_HEADER_NAME." + headerCount.addAndGet(1),
                            sipProvider.getAccountID().getAccountPropertyString(
                                JITSI_MEET_ROOM_HEADER_PROPERTY, "Jitsi-Conference-Room"));
                        call.setData("EXTRA_HEADER_VALUE." + headerCount.get(), roomName);

                        callContext.getExtraHeaders().forEach(
                            (key, value) ->
                            {
                                call.setData("EXTRA_HEADER_NAME." + headerCount.addAndGet(1), key);
                                call.setData("EXTRA_HEADER_VALUE." + headerCount.get(), value);
                            });
                    }

                    tele.removeCallListener(this);
                }

                @Override
                public void callEnded(CallEvent callEvent)
                {
                    tele.removeCallListener(this);
                }
            });
            try
            {
                this.sipCall = tele.createCall(outboundPrefix + destination);
                this.initSipCall();

                jvbConferenceCall.setConference(sipCall.getConference());

                logger.info(
                    this.callContext + " Created outgoing call to " + this);

                if (!CallState.CALL_INITIALIZATION.equals(sipCall.getCallState()))
                {
                    callStateListener.handleCallState(sipCall, null);
                }
            }
            catch (OperationFailedException | ParseException e)
            {
                return e;
            }
        }

        try
        {
            CallManager.acceptCall(jvbConferenceCall);
        }
        catch(OperationFailedException e)
        {
            return e;
        }

        return null;
    }

    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason)
    {
        this.jvbConference = null;

        if (sipCall != null)
        {
            hangUp(reasonCode, reason);
        }
        else
        {
            allCallsEnded();
        }
    }

    @Override
    void onJvbConferenceWillStop(JvbConference jvbConference, int reasonCode,
        String reason)
    {}

    private void sipCallEnded()
    {
        if (sipCall == null)
            return;

        logger.info(this.callContext + " Sip call ended: " + sipCall);

        sipCall.removeCallChangeListener(callStateListener);

        jitsiMeetTools.removeRequestListener(SipGatewaySession.this);

        if (peerStateListener != null)
            peerStateListener.unregister();

        if (this.transformerMonitor != null)
        {
            this.transformerMonitor.dispose();
            this.transformerMonitor = null;
        }

        this.soundNotificationManager.stop();

        sipCall = null;

        if (jvbConference != null)
        {
            jvbConference.stop();
        }
        else
        {
            allCallsEnded();
        }

        if (heartbeatExecutor != null)
        {
            heartbeatExecutor.shutdown();
        }
    }

    @Override
    public void onJoinJitsiMeetRequest(
        Call call, String room, Map<String, String> data)
    {
        try
        {
            if (jvbConference == null && this.sipCall == call)
            {
                if (room != null)
                {
                    callContext.setRoomName(room);
                    callContext.setRoomPassword(data.get(roomPassHeaderName));
                    callContext.setDomain(data.get(domainBaseHeaderName));
                    callContext.setTenant(data.get(domainTenantHeaderName));
                    callContext.setAuthToken(data.get(authTokenHeaderName));
                    callContext.setAuthUserId(data.get(authUserIdHeaderName));
                    callContext.setMucAddressPrefix(sipProvider.getAccountID()
                        .getAccountPropertyString(CallContext.MUC_DOMAIN_PREFIX_PROP, null));

                    joinJvbConference(callContext);
                }
                else
                {
                    logger.warn("No JVB room name provided in INVITE header.");
                    logger.info("Count of headers received:" + (data != null ? data.size() : 0));
                }
            }
        }
        catch(XmppStringprepException e)
        {
            logger.error("Malformed JVB room name provided:" + room, e);
        }
    }

    @Override
    public void onSessionStartMuted(boolean[] startMutedFlags)
    {
        logger.info(this.callContext + " Received start audio muted:" + startMutedFlags[0]);
        if (this.jvbConference != null)
        {
            if (this.jvbConference.getAudioModeration() != null)
            {
                this.jvbConference.getAudioModeration().setStartAudioMuted(startMutedFlags[0]);
            }
        }
        else
        {
            logger.warn(this.callContext + " Received start muted with no jvbConference created.");
        }
    }

    @Override
    public void onJSONReceived(CallPeer callPeer,
                               JSONObject jsonObject,
                               Map<String, Object> params)
    {
        if (callPeer.getCall() != this.sipCall)
        {
            if (logger.isTraceEnabled())
            {
                logger.trace(this.callContext
                    + " Ignoring event for non session call.");
            }
            return;
        }

        if (jsonObject.containsKey("t")
            && ((Long)jsonObject.get("t")).intValue() == SipInfoJsonProtocol.MESSAGE_TYPE.SIP_CALL_HEARTBEAT)
        {
            this.callHeartbeat.response();
            return;
        }

        if (this.jvbConference != null)
        {
            AudioModeration avMod = this.jvbConference.getAudioModeration();
            if (avMod != null)
            {
                avMod.onJSONReceived(callPeer, jsonObject, params);
            }
        }
        else
        {
            logger.warn(this.callContext + " Received json with no jvbConference created.");
        }
    }

    private void initSipCall()
    {
        sipCall.setData(CallContext.class, super.callContext);
        sipCall.addCallChangeListener(callStateListener);

        jitsiMeetTools.addRequestListener(this);

        if (mediaDroppedThresholdMs != -1)
        {
            CallPeer peer = sipCall.getCallPeers().next();
            if (!addExpireRunnable(peer))
            {
                peer.addCallPeerListener(new CallPeerAdapter()
                {
                    @Override
                    public void peerStateChanged(CallPeerChangeEvent evt)
                    {
                        CallPeer peer = evt.getSourceCallPeer();
                        CallPeerState peerState = peer.getState();

                        if (CallPeerState.CONNECTED.equals(peerState))
                        {
                            peer.removeCallPeerListener(this);
                            addExpireRunnable(peer);
                        }
                    }
                });
            }
        }

        peerStateListener = new CallPeerListener(sipCall);

        boolean useTranslator = sipCall.getProtocolProvider()
            .getAccountID().getAccountPropertyBoolean(
                ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE,
                false);

        CallPeer sipPeer = sipCall.getCallPeers().next();
        if (useTranslator && !addSipCallTransformer(sipPeer))
        {
            sipPeer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer peer = evt.getSourceCallPeer();
                    CallPeerState peerState = peer.getState();

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        peer.removeCallPeerListener(this);
                        addSipCallTransformer(peer);
                    }
                }
            });
        }

        if (heartbeatPeriodInSec > 0)
        {
            heartbeatExecutor = Executors.newScheduledThreadPool(1,
                new CustomizableThreadFactory("sip-call-heartbeat", true));
            heartbeatExecutor.scheduleAtFixedRate(
                callHeartbeat, heartbeatPeriodInSec, heartbeatPeriodInSec, TimeUnit.SECONDS);
        }
    }

    void initIncomingCall()
    {
        initSipCall();

        if (jvbConference != null)
        {
            // Reject incoming call
            CallManager.hangupCall(sipCall);
        }
        else
        {
            waitForRoomName();
        }
    }

    private void waitForRoomName()
    {
        if (waitThread != null)
        {
            throw new IllegalStateException("Wait thread exists");
        }

        waitThread = new WaitForJvbRoomNameThread();

        waitThread.start();
    }

    public Call getJvbCall()
    {
        return jvbConferenceCall;
    }

    @Override
    public void toneReceived(DTMFReceivedEvent dtmfReceivedEvent)
    {
        if (dtmfReceivedEvent != null
                && dtmfReceivedEvent.getSource() == jvbConferenceCall)
        {
            OperationSetDTMF opSet
                    = sipProvider.getOperationSet(OperationSetDTMF.class);
            if (opSet != null && dtmfReceivedEvent.getStart() != null)
            {
                if (dtmfReceivedEvent.getStart())
                {
                    try
                    {
                        opSet.startSendingDTMF(
                                peerStateListener.thePeer,
                                dtmfReceivedEvent.getValue());
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.info(this.callContext
                            + " Failed to forward a DTMF tone: " + ofe);
                    }
                }
                else
                {
                    opSet.stopSendingDTMF(peerStateListener.thePeer);
                }
            }
        }
    }

    @Override
    public boolean isTranslatorSupported()
    {
        return true;
    }

    @Override
    public String getDefaultInitStatus()
    {
        return INIT_STATUS_NAME;
    }

    private boolean addSsrcRewriter(CallPeer peer)
    {
        if (peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);
                if (stream != null)
                {
                    stream.setExternalTransformer(new SsrcRewriter(stream.getLocalSourceID()));
                    return true;
                }
            }
        }

        return false;
    }

    private boolean addExpireRunnable(CallPeer peer)
    {
        if (peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);
                if (stream != null)
                {
                    expireMediaStream
                        = new ExpireMediaStream((AudioMediaStreamImpl)stream);
                    EXECUTOR.registerRecurringRunnable(expireMediaStream);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getMucDisplayName()
    {
        String mucDisplayName = null;

        String sipDestination = callContext.getDestination();
        Call sipCall = getSipCall();

        if (sipDestination != null)
        {
            mucDisplayName = sipDestination;
        }
        else if (sipCall != null && sipCall.getCallPeers().hasNext())
        {
            CallPeer firstPeer = sipCall.getCallPeers().next();
            if (firstPeer != null)
            {
                mucDisplayName = firstPeer.getDisplayName();
            }
        }

        return mucDisplayName;
    }

    @Override
    public void onJvbCallEnded()
    {
        if (this.soundNotificationManager != null)
        {
            this.soundNotificationManager.onJvbCallEnded();
        }
    }

    @Override
    void notifyChatRoomMemberUpdated(
        ChatRoomMember chatMember, Presence presence)
    {
        soundNotificationManager.process(presence);
    }

    @Override
    void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        super.notifyChatRoomMemberJoined(member);

        if (soundNotificationManager != null && member instanceof ChatRoomMemberJabberImpl)
        {
            Presence presence = ((ChatRoomMemberJabberImpl) member).getLastPresence();

            if (getFocusResourceAddr().equals(presence.getFrom().getResourceOrEmpty().toString()))
            {
                soundNotificationManager.process(presence);
            }
            else
            {
                soundNotificationManager.notifyChatRoomMemberJoined(member);
            }
        }
    }

    @Override
    void notifyChatRoomMemberLeft(ChatRoomMember member)
    {
        super.notifyChatRoomMemberLeft(member);

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyChatRoomMemberLeft(member);
        }
    }

    @Override
    void notifyJvbRoomJoined()
    {
        super.notifyJvbRoomJoined();

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyJvbRoomJoined();
        }
    }

    @Override
    public void notifyOnLobbyWaitReview(ChatRoom lobbyRoom)
    {
        super.notifyOnLobbyWaitReview(lobbyRoom);

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyLobbyWaitReview();
        }
    }

    @Override
    void handleMaxOccupantsLimitReached()
    {
        soundNotificationManager.indicateMaxOccupantsLimitReached();
    }

    private boolean addSipCallTransformer(CallPeer peer)
    {
        if (peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler
                = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);
                if (stream != null)
                {
                    transformerMonitor = new SipCallKeepAliveTransformer(
                        peerMedia.getMediaHandler(), stream);
                    stream.setExternalTransformer(transformerMonitor);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String toString()
    {
        return "SipGatewaySession{" +
            "sipCall=" + sipCall +
            ", destination='" + destination + '\'' +
            '}';
    }

    public boolean hasCallResumeSupport()
    {
        return true;
    }

    public SoundNotificationManager getSoundNotificationManager()
    {
        return this.soundNotificationManager;
    }

    private void sendJson(CallPeer callPeer, JSONObject jsonObject)
        throws OperationFailedException
    {
        this.sipInfoJsonProtocol.sendJson(callPeer, jsonObject);
    }

    public void sendJson(JSONObject jsonObject)
        throws OperationFailedException
    {
        synchronized(jsonToSendLock)
        {
            if (sipCall == null || !sipCall.getCallPeers().hasNext() || !jsonToSendQueue.isEmpty())
            {
                jsonToSendQueue.offer(jsonObject);

                return;
            }
        }

        this.sendJson(sipCall.getCallPeers().next(), jsonObject);
    }

    public void notifyLobbyJoined()
    {
        if (sipCall == null)
        {
            return;
        }

        try
        {
            this.sendJson(this.sipInfoJsonProtocol.createLobbyJoinedNotification());
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending lobby joined notification", ex);
        }
    }

    public void notifyLobbyLeft()
    {
        if (sipCall == null)
        {
            return;
        }

        try
        {
            this.sendJson(this.sipInfoJsonProtocol.createLobbyLeftNotification());
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending lobby left notification", ex);
        }
    }

    public void notifyLobbyAllowedJoin()
    {
        if (sipCall == null)
        {
            return;
        }

        try
        {
            this.sendJson(this.sipInfoJsonProtocol.createLobbyAllowedJoinNotification());
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending lobby is allowed to join", ex);
        }
    }

    public void notifyLobbyRejectedJoin()
    {
        if (sipCall == null)
        {
            return;
        }

        try
        {
            this.sendJson(this.sipInfoJsonProtocol.createLobbyRejectedJoinNotification());
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending lobby rejection notification", ex);
        }
    }

    private class ExpireMediaStream
        extends PeriodicRunnable
    {
        private final AudioMediaStreamImpl stream;

        private boolean statsSent = false;

        public ExpireMediaStream(AudioMediaStreamImpl stream)
        {
            super(2000, false);
            this.stream = stream;
        }

        @Override
        public void run()
        {
            super.run();

            try
            {
                long lastReceived = stream.getLastInputActivityTime();

                if (System.currentTimeMillis() - lastReceived
                        > mediaDroppedThresholdMs)
                {
                    if (!gatewayMediaDropped)
                    {
                        logger.error(
                            SipGatewaySession.this.callContext +
                            " Stopped receiving RTP for " + getSipCall());

                        if (!statsSent)
                        {
                            Statistics.incrementTotalCallsWithMediaDropped();
                            statsSent = true;
                        }
                    }

                    gatewayMediaDropped = true;

                    if (JigasiBundleActivator.getConfigurationService()
                        .getBoolean(P_NAME_HANGUP_SIP_ON_MEDIA_DROPPED, false))
                    {
                        CallManager.hangupCall(getSipCall(),
                            OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT,
                            "Stopped receiving media");
                    }

                }
                else
                {
                    if (gatewayMediaDropped)
                    {
                        logger.info(SipGatewaySession.this.callContext
                            + " RTP resumed for " + getSipCall());
                    }
                    gatewayMediaDropped = false;
                }
            }
            catch(IOException e)
            {
                logger.error(SipGatewaySession.this.callContext
                    + " Should not happen exception", e);
            }
        }
    }

    class SipCallStateListener
        implements CallChangeListener
    {

        @Override
        public void callPeerAdded(CallPeerEvent evt) { }

        @Override
        public void callPeerRemoved(CallPeerEvent evt) { }

        @Override
        public void callStateChanged(CallChangeEvent evt)
        {
            handleCallState(evt.getSourceCall(), evt.getCause());
        }

        void handleCallState(Call call, CallPeerChangeEvent cause)
        {
            if (call.getCallState() == CallState.CALL_IN_PROGRESS)
            {
                logger.info(SipGatewaySession.this.callContext + " Sip call IN_PROGRESS: " + call);

                logger.info(SipGatewaySession.this.callContext + " SIP call format used: "
                    + Util.getFirstPeerMediaFormat(call));

                if (jvbConference.getAudioModeration() != null)
                {
                    jvbConference.getAudioModeration().maybeProcessStartMuted();
                }
            }
            else if (call.getCallState() == CallState.CALL_ENDED)
            {
                logger.info(SipGatewaySession.this.callContext
                    + " SIP call ended: " + cause);

                if (peerStateListener != null)
                    peerStateListener.unregister();

                EXECUTOR.deRegisterRecurringRunnable(expireMediaStream);
                expireMediaStream = null;

                if (cause != null
                    && jvbConference != null && jvbConference.isInTheRoom())
                {
                    if (StringUtils.isNotEmpty(StringUtils.trim(cause.getReasonString())))
                    {
                        jvbConference.setPresenceStatus(
                            cause.getReasonString());
                    }

                    new Thread(() -> {
                        try
                        {
                            Thread.sleep(5000);

                            sipCallEnded();
                        }
                        catch (InterruptedException e)
                        {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
                else
                {
                    sipCallEnded();
                }
            }
        }
    }

    class CallPeerListener
        extends CallPeerAdapter
    {
        CallPeer thePeer;

        CallPeerListener(Call call)
        {
            thePeer = call.getCallPeers().next();
            thePeer.addCallPeerListener(this);
        }

        @Override
        public void peerStateChanged(final CallPeerChangeEvent evt)
        {
            CallPeerState callPeerState = (CallPeerState)evt.getNewValue();
            String stateString = callPeerState.getStateString();

            logger.info(callContext + " SIP peer state: " + stateString);

            if (jvbConference != null
                    && CallPeerState.CONNECTED.equals(callPeerState)
                    && destination != null)
            {
                jvbConference.setPresenceStatus(stateString);

                processJsonSendQueue();
            }

            soundNotificationManager.process(callPeerState);
        }

        void unregister()
        {
            thePeer.removeCallPeerListener(this);
        }

        private void processJsonSendQueue()
        {
            List<JSONObject> toProcess;

            synchronized(jsonToSendLock)
            {
                toProcess = new LinkedList<>(jsonToSendQueue);
                jsonToSendQueue.clear();
            }

            toProcess.forEach(json -> {
                try
                {
                    SipGatewaySession.this.sendJson(thePeer, json);
                }
                catch(OperationFailedException e)
                {
                    logger.error(SipGatewaySession.this.callContext + " Error processing json ", e);
                }
            });
        }
    }

    class WaitForJvbRoomNameThread
        extends Thread
    {
        private boolean cancel = false;

        @Override
        public void run()
        {
            synchronized (waitLock)
            {
                try
                {
                    waitLock.wait(1000);

                    if (cancel)
                    {
                        logger.info(SipGatewaySession.this.callContext
                            + " Wait thread cancelled");
                        return;
                    }

                    if (getCallContext().getRoomJid() == null && !CallState.CALL_ENDED.equals(sipCall.getCallState()))
                    {
                        String defaultRoom
                            = JigasiBundleActivator.getConfigurationService()
                                .getString(SipGateway.P_NAME_DEFAULT_JVB_ROOM);

                        if (defaultRoom != null)
                        {
                            logger.info(
                                SipGatewaySession.this.callContext
                                + "Using default JVB room name property "
                                + defaultRoom);

                            callContext.setRoomName(defaultRoom);

                            joinJvbConference(callContext);
                        }
                        else
                        {
                            logger.warn(SipGatewaySession.this.callContext
                                + " No JVB room name provided in INVITE header");

                            hangUp(OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE, "No JVB room name provided");
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                catch(XmppStringprepException e)
                {
                    logger.error(SipGatewaySession.this.callContext + " Malformed default JVB room name.", e);

                    hangUp(OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE, "No JVB room name provided");
                }
            }
        }

        void cancel()
        {
            if (Thread.currentThread() == waitThread)
            {
                waitThread = null;
                return;
            }

            synchronized (waitLock)
            {
                cancel = true;
                waitLock.notifyAll();
            }
            try
            {
                waitThread.join();
                waitThread = null;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class CallHeartbeat
        implements Runnable
    {
        private AtomicLong counter = new AtomicLong();

        void response()
        {
            counter.decrementAndGet();
        }

        @Override
        public void run()
        {
            if (sipCall == null)
            {
                return;
            }

            if (counter.get() >= 2)
            {
                heartbeatExecutor.shutdown();

                Statistics.incrementTotalCallsWithNoSipHeartbeat();

                CallManager.hangupCall(getSipCall(),
                    OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT,
                    "No response on heartbeat");

                return;
            }

            try
            {
                counter.incrementAndGet();
                SipGatewaySession.this.sendJson(SipInfoJsonProtocol.createSIPCallHeartBeat());
            }
            catch(OperationFailedException ex)
            {
                logger.error("Cannot send heartbeat", ex);
            }
        }
    }
}
