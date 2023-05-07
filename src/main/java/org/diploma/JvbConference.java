package org.diploma;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.DataObject;
import net.java.sip.communicator.util.osgi.ServiceUtils;
import org.apache.commons.lang3.StringUtils;
import org.diploma.lobby.Lobby;
import org.diploma.stats.Statistics;
import org.diploma.util.RegisterThread;
import org.diploma.util.Util;
import org.diploma.version.CurrentVersionImpl;
import org.jitsi.impl.neomedia.*;
import org.diploma.stats.*;
import org.diploma.util.*;
import org.diploma.version.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.*;
import org.jitsi.xmpp.extensions.colibri.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.bosh.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.disco.*;
import org.jivesoftware.smackx.disco.packet.*;
import org.jivesoftware.smackx.muc.packet.*;
import org.jivesoftware.smackx.nick.packet.*;
import org.jivesoftware.smackx.xdata.packet.*;
import org.jivesoftware.smackx.xdata.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.jid.parts.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.beans.*;
import java.io.*;
import java.util.*;

import static net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent.*;
import static org.jivesoftware.smack.packet.StanzaError.Condition.*;

public class JvbConference
    implements RegistrationStateChangeListener,
               ServiceListener,
               ChatRoomMemberPresenceListener,
               LocalUserChatRoomPresenceListener,
               CallPeerConferenceListener,
               PropertyChangeListener
{
    private final static Logger logger = Logger.getLogger(JvbConference.class);

    public static final String SIP_GATEWAY_FEATURE_NAME
        = "http://jitsi.org/protocol/jigasi";

    public static final String DTMF_FEATURE_NAME
            = "urn:xmpp:jingle:dtmf:0";

    private static final String P_NAME_USE_SIP_USER_AS_XMPP_RESOURCE
        = "org.diploma.USE_SIP_USER_AS_XMPP_RESOURCE";

    private static final String P_NAME_NOTIFY_MAX_OCCUPANTS
        = "org.diploma.NOTIFY_MAX_OCCUPANTS";

    public static final String P_NAME_ALLOW_ONLY_JIGASIS_IN_ROOM
        = "org.diploma.ALLOW_ONLY_JIGASIS_IN_ROOM";

    public static final String LOCAL_REGION_PNAME
        = "org.diploma.LOCAL_REGION";

    private static final String DATA_FORM_MEETING_ID_FIELD_NAME = "muc#roominfo_meetingId";

    private static final int JVB_ACTIVITY_CHECK_DELAY = 5000;

    private static final Timer checkReceivedMediaTimer = new Timer();

    private final AudioModeration audioModeration;

    private String meetingId;

    private static ExtensionElement addSupportedFeatures(
            OperationSetJitsiMeetToolsJabber meetTools)
    {
        FeaturesExtension features = new FeaturesExtension();

        meetTools.addSupportedFeature(SIP_GATEWAY_FEATURE_NAME);
        features.addChildExtension(Util.createFeature(SIP_GATEWAY_FEATURE_NAME));
        meetTools.addSupportedFeature(DTMF_FEATURE_NAME);
        features.addChildExtension(Util.createFeature(DTMF_FEATURE_NAME));

        ConfigurationService cfg
                = JigasiBundleActivator.getConfigurationService();

        if (cfg.getBoolean(SipGateway.P_NAME_DISABLE_ICE, false))
        {
            meetTools.removeSupportedFeature(
                    "urn:xmpp:jingle:transports:ice-udp:1");

            logger.info("ICE feature will not be advertised");
        }

        ExtensionElement audioMuteFeature = AudioModeration.addSupportedFeatures(meetTools);
        if (audioMuteFeature != null)
        {
            features.addChildExtension(audioMuteFeature);
        }

        return features;
    }

    private final AbstractGatewaySession gatewaySession;

    private final boolean allowOnlyJigasiInRoom;

    private AccountID xmppAccount;

    private String xmppPassword;

    private ProtocolProviderService xmppProvider;

    private final CallContext callContext;

    private ChatRoom mucRoom;

    private boolean started;

    private Call jvbCall;

    private final Object jvbCallWriteSync = new Object();

    private OperationSetBasicTelephony telephony;

    private OperationSetJitsiMeetTools jitsiMeetTools = null;

    private final JvbCallListener callListener
        = new JvbCallListener();

    private final JvbCallChangeListener callChangeListener
        = new JvbCallChangeListener();

    private ProtocolProviderFactory xmppProviderFactory;

    private final JvbConferenceStopTimeout inviteTimeout = new JvbConferenceStopTimeout(
        "JvbInviteTimeout",
        "No invite from conference focus",
        "Did not received session invite"
    );

    private String jvbParticipantStatus = null;

    private final Object statusSync = new Object();

    private String endReason;

    private int endReasonCode;

    private boolean connFailedStatsSent = false;

    private boolean gwSesisonWaitingStatsSent = false;

    private Lobby lobby = null;

    private boolean lobbyEnabled = false;

    private boolean singleModeratorEnabled = false;

    private RoomConfigurationChangeListener roomConfigurationListener = null;

    private final List<String> jigasiChatRoomMembers = Collections.synchronizedList(new ArrayList<>());

    private ExtensionElement features = null;

    public JvbConference(AbstractGatewaySession gatewaySession, CallContext ctx)
    {
        this.gatewaySession = gatewaySession;
        this.callContext = ctx;
        this.allowOnlyJigasiInRoom = JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ALLOW_ONLY_JIGASIS_IN_ROOM, true);

        if (this.gatewaySession instanceof SipGatewaySession)
        {
            this.audioModeration = new AudioModeration(this, (SipGatewaySession)this.gatewaySession, this.callContext);
        }
        else
        {
            this.audioModeration = null;
        }
    }

    public AudioModeration getAudioModeration()
    {
        return audioModeration;
    }

    public Localpart getResourceIdentifier()
    {
        Localpart resourceIdentifier = null;
        if (JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_USE_SIP_USER_AS_XMPP_RESOURCE, false))
        {
            String resourceIdentBuilder = gatewaySession.getMucDisplayName();
            if (StringUtils.isNotEmpty(resourceIdentBuilder))
            {
                int idx = resourceIdentBuilder.indexOf('@');
                if (idx != -1)
                {
                    resourceIdentBuilder = resourceIdentBuilder.substring(0, idx);
                }

                try
                {
                    resourceIdentifier
                        = Localpart.from(
                            resourceIdentBuilder.replace("[^A-Za-z0-9]", "-"));
                }
                catch (XmppStringprepException e)
                {
                    logger.error(this.callContext
                        + " The SIP URI is invalid to use an XMPP"
                        + " resource, identifier will be a random string", e);
                }
            }
            else
            {
                logger.info(this.callContext
                    + " The SIP URI is empty! The XMPP resource "
                    + "identifier will be a random string.");
            }
        }

        if (resourceIdentifier == null)
        {
            resourceIdentifier =
                callContext.getCallResource().getLocalpartOrNull();
        }

        return resourceIdentifier;
    }

    public ChatRoom getJvbRoom()
    {
        return mucRoom;
    }

    public synchronized void start()
    {
        if (started)
        {
            logger.error(this.callContext + " Already started !");
            return;
        }
        logger.info(this.callContext + " Starting JVB conference room: " + this.callContext.getRoomJid());

        Localpart resourceIdentifier = getResourceIdentifier();

        this.xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                JigasiBundleActivator.osgiContext,
                ProtocolNames.JABBER);

        this.xmppAccount
            = xmppProviderFactory.createAccount(
                    createAccountPropertiesForCallId(
                            callContext,
                            resourceIdentifier.toString()));

        xmppProviderFactory.loadAccount(xmppAccount);

        started = true;

        Collection<ServiceReference<ProtocolProviderService>> providers
            = ServiceUtils.getServiceReferences(
                    JigasiBundleActivator.osgiContext,
                    ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> serviceRef : providers)
        {
            ProtocolProviderService candidate
                = JigasiBundleActivator.osgiContext.getService(serviceRef);

            if (ProtocolNames.JABBER.equals(candidate.getProtocolName()))
            {
                if (candidate.getAccountID()
                    .getAccountUniqueID()
                    .equals(xmppAccount.getAccountUniqueID()))
                {
                    setXmppProvider(candidate);

                    if (this.xmppProvider != null)
                    {
                        break;
                    }
                }
            }
        }

        if (this.xmppProvider == null)
        {
            JigasiBundleActivator.osgiContext.addServiceListener(this);
        }
    }

    public synchronized void stop()
    {
        if (!started)
        {
            logger.error(this.callContext + " Already stopped !");
            return;
        }

        started = false;

        JigasiBundleActivator.osgiContext.removeServiceListener(this);

        if (telephony != null)
        {
            telephony.removeCallListener(callListener);
            telephony = null;
        }

        if (this.audioModeration != null)
        {
            this.audioModeration.clean();
            this.audioModeration.cleanXmppProvider();
        }

        gatewaySession.onJvbConferenceWillStop(this, endReasonCode, endReason);

        leaveConferenceRoom();

        if (jvbCall != null)
        {
            CallManager.hangupCall(jvbCall, true);
        }

        if (xmppProvider != null)
        {
            xmppProvider.removeRegistrationStateChangeListener(this);

            if (jvbCall == null)
            {
                logger.info(
                    callContext + " Removing account " + xmppAccount);

                xmppProviderFactory.unloadAccount(xmppAccount);
            }

            xmppProviderFactory = null;

            xmppAccount = null;

            xmppProvider = null;
        }

        gatewaySession.onJvbConferenceStopped(this, endReasonCode, endReason);

        setJvbCall(null);
    }

    private synchronized void setXmppProvider(
            ProtocolProviderService xmppProvider)
    {
        if (this.xmppProvider != null)
            throw new IllegalStateException("unexpected");

        if (!xmppProvider.getAccountID().getAccountUniqueID()
                .equals(xmppAccount.getAccountUniqueID()))
        {

            logger.info(
                this.callContext + " Rejects XMPP provider " + xmppProvider);
            return;
        }

        logger.info(this.callContext + " Using " + xmppProvider);

        this.xmppProvider = xmppProvider;

        this.features = addSupportedFeatures(xmppProvider.getOperationSet(OperationSetJitsiMeetToolsJabber.class));

        xmppProvider.addRegistrationStateChangeListener(this);

        this.telephony
            = xmppProvider.getOperationSet(OperationSetBasicTelephony.class);

        telephony.addCallListener(callListener);

        if (xmppProvider.isRegistered())
        {
            joinConferenceRoom();
        }
        else
        {
            new RegisterThread(xmppProvider, xmppPassword).start();
        }
    }

    public ProtocolProviderService getXmppProvider()
    {
        return xmppProvider;
    }

    @Override
    public synchronized void registrationStateChanged(
            RegistrationStateChangeEvent evt)
    {
        if (started
            && mucRoom == null
            && evt.getNewState() == RegistrationState.REGISTERED)
        {
            if (this.getAudioModeration() != null)
            {
                this.getAudioModeration().xmppProviderRegistered();
            }

            joinConferenceRoom();

            XMPPConnection connection = getConnection();
            if (xmppProvider != null && connection instanceof XMPPBOSHConnection)
            {
                Object sessionId = Util.getConnSessionId(connection);
                if (sessionId != null)
                {
                    logger.error(this.callContext + " Registered bosh sid: "
                        + sessionId);
                }
            }
        }
        else if (evt.getNewState() == RegistrationState.UNREGISTERED)
        {
            logger.error(this.callContext + " Unregistered XMPP:" + evt);
        }
        else if (evt.getNewState() == RegistrationState.REGISTERING)
        {
            logger.info(this.callContext + " Registering XMPP.");
        }
        else if (evt.getNewState() == RegistrationState.CONNECTION_FAILED)
        {
            logger.error(this.callContext + " XMPP Connection failed. " + evt);

            if (!connFailedStatsSent)
            {
                Statistics.incrementTotalCallsWithConnectionFailed();
                connFailedStatsSent = true;
            }

            leaveConferenceRoom();

            callContext.updateCallResource();

            if (evt.getReasonCode() == RegistrationStateChangeEvent.REASON_INTERNAL_ERROR
                && evt.getReason().contains("No supported and enabled SASL Mechanism provided by server"))
            {
                logger.error("Server didn't like our xmpp configs, we are giving up!    ");
                stop();
            }
            else
            {
                CallManager.hangupCall(jvbCall, 502, "Connection failed");
            }
        }
        else
        {
            logger.info(this.callContext + evt.toString());
        }
    }

    public boolean isInTheRoom()
    {
        return mucRoom != null && mucRoom.isJoined();
    }

    public boolean isStarted()
    {
        return started;
    }

    public void joinConferenceRoom()
    {
        OperationSetMultiUserChat muc = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);
        muc.addPresenceListener(this);

        OperationSetIncomingDTMF opSet = this.xmppProvider.getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet != null)
            opSet.addDTMFListener(gatewaySession);

        this.jitsiMeetTools = xmppProvider.getOperationSet(OperationSetJitsiMeetToolsJabber.class);

        if (this.jitsiMeetTools != null)
        {
            this.jitsiMeetTools.addRequestListener(this.gatewaySession);
        }

        Localpart lobbyLocalpart = null;

        ChatRoom mucRoom = null;
        try
        {
            String roomName = callContext.getRoomJid().toString();
            String roomPassword = callContext.getRoomPassword();

            logger.info(this.callContext + " Joining JVB conference room: " + roomName);

            mucRoom = muc.findRoom(roomName);

            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                String displayName = gatewaySession.getMucDisplayName();
                if (displayName != null)
                {
                    ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(
                        new Nick(displayName));
                }
                else
                {
                    logger.error(this.callContext
                        + " No display name to use...");
                }

                String region = JigasiBundleActivator.getConfigurationService()
                    .getString(LOCAL_REGION_PNAME);
                if (StringUtils.isNotEmpty(region))
                {
                    JitsiParticipantRegionPacketExtension rpe = new JitsiParticipantRegionPacketExtension();
                    rpe.setRegionId(region);

                    ((ChatRoomJabberImpl)mucRoom)
                        .addPresencePacketExtensions(rpe);
                }

                ((ChatRoomJabberImpl)mucRoom)
                    .addPresencePacketExtensions(
                        new ColibriStatsExtension.Stat(
                            ColibriStatsExtension.VERSION,
                            CurrentVersionImpl.VERSION.getApplicationName()
                                + " " + CurrentVersionImpl.VERSION));

                AbstractPacketExtension initiator
                    = new AbstractPacketExtension(
                        SIP_GATEWAY_FEATURE_NAME, "initiator"){};

                callContext.getExtraHeaders().forEach(
                    (key, value) ->
                    {
                        HeaderExtension he = new HeaderExtension();
                        he.setName(key);
                        he.setValue(value);

                        initiator.addChildExtension(he);
                    });
                if (initiator.getChildExtensions().size() > 0)
                {
                    ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(initiator);
                }

                ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(this.features);
            }
            else
            {
                logger.error(this.callContext
                    + " Cannot set presence extensions as chatRoom "
                    + "is not an instance of ChatRoomJabberImpl");
            }

            if (this.audioModeration != null)
            {
                this.audioModeration.notifyWillJoinJvbRoom(mucRoom);
            }

            inviteFocus(JidCreate.entityBareFrom(mucRoom.getIdentifier()));

            Localpart resourceIdentifier = getResourceIdentifier();

            lobbyLocalpart = resourceIdentifier;

            inviteTimeout.scheduleTimeout();

            mucRoom.addMemberPresenceListener(this);

            if (StringUtils.isEmpty(roomPassword))
            {
                mucRoom.joinAs(resourceIdentifier.toString());
            }
            else
            {
                mucRoom.joinAs(resourceIdentifier.toString(),
                    roomPassword.getBytes());
            }

            this.mucRoom = mucRoom;

            if (gatewaySession.getDefaultInitStatus() != null)
            {
                setPresenceStatus(gatewaySession.getDefaultInitStatus());
            }

            gatewaySession.notifyJvbRoomJoined();

            if (lobbyEnabled)
            {
                updateFromRoomConfiguration();
            }

            if (roomConfigurationListener == null && mucRoom instanceof ChatRoomJabberImpl)
            {
                roomConfigurationListener = new RoomConfigurationChangeListener();
                getConnection().addAsyncStanzaListener(roomConfigurationListener,
                    new AndFilter(
                        FromMatchesFilter.create(((ChatRoomJabberImpl)this.mucRoom).getIdentifierAsJid()),
                        MessageTypeFilter.GROUPCHAT));
            }
        }
        catch (Exception e)
        {
            if (e instanceof OperationFailedException)
            {
                OperationFailedException opex = (OperationFailedException)e;

                if (opex.getErrorCode() == OperationFailedException.REGISTRATION_REQUIRED)
                {
                    if (this.gatewaySession != null && this.gatewaySession instanceof SipGatewaySession)
                    {
                        try
                        {
                            this.audioModeration.clean();

                            if (mucRoom != null)
                            {
                                mucRoom.removeMemberPresenceListener(this);
                                mucRoom.leave();
                            }

                           muc.removePresenceListener(this);

                            if (opSet != null)
                            {
                                opSet.removeDTMFListener(this.gatewaySession);
                            }

                            if (this.jitsiMeetTools != null)
                            {
                                this.jitsiMeetTools.removeRequestListener(this.gatewaySession);
                            }

                            DataObject dataObject = opex.getDataObject();

                            if (dataObject != null)
                            {
                                Jid lobbyJid = (Jid)dataObject.getData("lobbyroomjid");

                                if (lobbyJid != null)
                                {
                                    EntityFullJid lobbyFullJid =
                                        JidCreate.entityFullFrom(
                                            lobbyJid.asEntityBareJidOrThrow(),
                                            Resourcepart.from(
                                                lobbyLocalpart.toString()));

                                    this.lobby = new Lobby(this.xmppProvider,
                                            this.callContext,
                                            lobbyFullJid,
                                            this.callContext.getRoomJid(),
                                            this,
                                            (SipGatewaySession)this.gatewaySession);

                                    logger.info(
                                        callContext + " Lobby enabled by moderator! Will try to join lobby!");

                                    this.lobby.join();

                                    this.setLobbyEnabled(true);

                                    return;
                                }
                                else
                                {
                                    logger.error(callContext + " No required lobby jid!");
                                }
                            }
                        }
                        catch(Exception ex)
                        {
                            logger.error(callContext + " Failed to join lobby room!", ex);
                        }
                    }
                }
            }

            if (e.getCause() instanceof XMPPException.XMPPErrorException)
            {
                if (JigasiBundleActivator.getConfigurationService()
                        .getBoolean(P_NAME_NOTIFY_MAX_OCCUPANTS, true)
                    && ((XMPPException.XMPPErrorException)e.getCause())
                        .getStanzaError().getCondition() == service_unavailable)
                {
                    gatewaySession.handleMaxOccupantsLimitReached();
                }
            }

            logger.error(this.callContext + " " + e.getMessage(), e);

            gatewaySession.getGateway().fireGatewaySessionFailed(gatewaySession);

            stop();
        }
    }

    void setPresenceStatus(String statusMsg)
    {
        synchronized(statusSync)
        {
            if (statusMsg.equals(jvbParticipantStatus))
            {
                return;
            }

            jvbParticipantStatus = statusMsg;
        }

        if (mucRoom != null)
        {
            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = xmppProvider.getOperationSet(
                OperationSetJitsiMeetToolsJabber.class);

            jitsiMeetTools.setPresenceStatus(mucRoom, statusMsg);
        }
    }

    private void onJvbCallEnded()
    {
        if (jvbCall == null)
        {
            logger.warn(this.callContext + " JVB call already disposed");
            return;
        }

        setJvbCall(null);

        if (started)
        {
            if (AbstractGateway.getJvbInviteTimeout() <= 0 || !gatewaySession.hasCallResumeSupport())
            {
                stop();
            }
            else
            {
                logger.info(this.callContext
                    + " Proceed with gwSession call on xmpp call hangup.");

                if (!gwSesisonWaitingStatsSent)
                {
                    Statistics.incrementTotalCallsWithSipCallWaiting();
                    gwSesisonWaitingStatsSent = true;
                }

                this.gatewaySession.onJvbCallEnded();
            }
        }
    }

    private void leaveConferenceRoom()
    {
        if (this.jitsiMeetTools != null)
        {
            this.jitsiMeetTools.removeRequestListener(this.gatewaySession);

            this.jitsiMeetTools = null;
        }

        OperationSetIncomingDTMF opSet
            = this.xmppProvider.getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet != null)
            opSet.removeDTMFListener(gatewaySession);

        OperationSetMultiUserChat muc
            = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);
        muc.removePresenceListener(this);

        if (this.roomConfigurationListener != null)
        {
            XMPPConnection connection = getConnection();
            if (connection != null)
            {
                connection.removeAsyncStanzaListener(roomConfigurationListener);
            }

            this.roomConfigurationListener = null;
        }

        if (mucRoom != null)
        {
            mucRoom.leave();
            mucRoom.removeMemberPresenceListener(this);
            mucRoom = null;
        }

        if (this.lobby != null)
        {
            this.lobby.leave();
        }

        this.lobby = null;
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference<?> ref = serviceEvent.getServiceReference();

        Object service = JigasiBundleActivator.osgiContext.getService(ref);

        if (!(service instanceof ProtocolProviderService))
            return;

        ProtocolProviderService pps = (ProtocolProviderService) service;

        if (xmppProvider == null &&
            ProtocolNames.JABBER.equals(pps.getProtocolName()))
        {
            setXmppProvider(pps);
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        if (logger.isTraceEnabled())
        {
            logger.trace(this.callContext + " Member presence change: " + evt);
        }

        ChatRoomMember member = evt.getChatRoomMember();
        String eventType = evt.getEventType();

        if (!ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT.equals(eventType))
        {
            if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED.equals(eventType))
            {
                gatewaySession.notifyChatRoomMemberJoined(member);
            }
            else if (ChatRoomMemberPresenceChangeEvent.MEMBER_UPDATED
                    .equals(eventType))
            {
                if (member instanceof ChatRoomMemberJabberImpl)
                {
                    Presence presence = ((ChatRoomMemberJabberImpl) member).getLastPresence();

                    gatewaySession.notifyChatRoomMemberUpdated(member, presence);

                    if (presence != null
                        && !jigasiChatRoomMembers.contains(member.getName())
                        && presence.hasExtension("initiator", SIP_GATEWAY_FEATURE_NAME))
                    {
                        jigasiChatRoomMembers.add(member.getName());
                    }
                }
            }

            return;
        }
        else
        {
            gatewaySession.notifyChatRoomMemberLeft(member);
            logger.info(
                this.callContext + " Member left : " + member.getRole()
                            + " " + member.getContactAddress());

            jigasiChatRoomMembers.remove(member.getName());

            CallPeer peer;
            if (jvbCall != null && (peer = jvbCall.getCallPeers().next()) instanceof MediaAwareCallPeer)
            {
                MediaAwareCallPeer<?, ?, ?> peerMedia = (MediaAwareCallPeer<?, ?, ?>) peer;
                peerMedia.getConferenceMembers().forEach(confMember ->
                {
                    String address = confMember.getAddress();
                    if (address != null && !address.equals("jvb"))
                    {
                        try
                        {
                            if (JidCreate.from(address).getResourceOrEmpty().equals(member.getName())
                                || address.equals(member.getName()))
                            {
                                peerMedia.removeConferenceMember(confMember);
                            }
                        }
                        catch(Exception e)
                        {
                            logger.error(this.callContext + " Error removing conference member=" + member.getName());
                        }
                    }
                });
            }
        }

        processChatRoomMemberLeft(member);
    }

    private void processChatRoomMemberLeft(ChatRoomMember member)
    {
        if (!this.started)
        {
            return;
        }

        if (member.getName().equals(gatewaySession.getFocusResourceAddr()))
        {
            logger.info(this.callContext + " Focus left! - stopping the call");
            CallManager.hangupCall(jvbCall, 502, "Focus left");

            return;
        }

        if (getConnection() == null || !getConnection().isConnected())
        {
            return;
        }

        if ((this.lobbyEnabled && !this.singleModeratorEnabled) || !this.allowOnlyJigasiInRoom)
        {
            boolean onlyJigasisInRoom = this.mucRoom.getMembers().stream().allMatch(m ->
                m.getName().equals(getResourceIdentifier().toString())
                || m.getName().equals(gatewaySession.getFocusResourceAddr())
                || jigasiChatRoomMembers.contains(m.getName()));

            if (onlyJigasisInRoom)
            {
                if (!this.allowOnlyJigasiInRoom)
                {
                    logger.info(this.callContext + " Leaving room without web users and only jigasi participants!");
                    stop();
                    return;
                }

                logger.info(this.callContext + " Leaving room with lobby enabled and only jigasi participants!");

                if (this.gatewaySession instanceof SipGatewaySession)
                {
                    ((SipGatewaySession) this.gatewaySession)
                        .getSoundNotificationManager().notifyLobbyRoomDestroyed();
                }
                else
                {
                    stop();
                }
            }
        }
    }

    @Override
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt)
    {
        try
        {
            if (evt.getChatRoom().equals(JvbConference.this.mucRoom))
            {
                if (Objects.equals(evt.getEventType(), LOCAL_USER_KICKED)
                    || Objects.equals(evt.getEventType(), LOCAL_USER_ROOM_DESTROYED))
                {
                    this.stop();
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(callContext + " " + ex, ex);
        }
    }

    void sendPresenceExtension(ExtensionElement extension)
    {
        if (mucRoom != null)
        {
            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = xmppProvider.getOperationSet(
                OperationSetJitsiMeetToolsJabber.class);

            jitsiMeetTools.sendPresenceExtension(mucRoom, extension);
        }
    }

    public OrderedJsonObject getDebugState()
    {
        OrderedJsonObject debugState = new OrderedJsonObject();
        String meetingUrl = getMeetingUrl();
        if (StringUtils.isNotEmpty(meetingUrl))
        {
            debugState.put("meetingUrl", getMeetingUrl());
        }

        String meetingIdCopy = getMeetingId();
        if (StringUtils.isNotEmpty(meetingUrl))
        {
            debugState.put("meetingId", meetingIdCopy);
        }
        return debugState;
    }

    public String getMeetingUrl()
    {
        return callContext.getMeetingUrl();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        if (evt.getPropertyName().equals(CallPeerJabberImpl.TRANSPORT_REPLACE_PROPERTY_NAME))
        {
            Statistics.incrementTotalCallsWithJvbMigrate();

            leaveConferenceRoom();

            joinConferenceRoom();
        }
    }

    private class JvbCallListener
        implements CallListener
    {
        @Override
        public void incomingCallReceived(CallEvent event)
        {
            CallPeer peer = event.getSourceCall().getCallPeers().next();
            String peerAddress;
            if (peer == null || peer.getAddress() == null)
            {
                logger.error(callContext
                    + " Failed to obtain focus peer address");
                peerAddress = null;
            }
            else
            {
                String fullAddress = peer.getAddress();
                peerAddress
                    = fullAddress.substring(
                            fullAddress.indexOf("/") + 1);

                logger.info(callContext
                    + " Got invite from " + peerAddress);
            }

            if (peerAddress == null
                || !peerAddress.equals(gatewaySession.getFocusResourceAddr()))
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(callContext +
                        " Calls not initiated from focus are not allowed");
                }

                CallManager.hangupCall(event.getSourceCall(),
                    403, "Only calls from focus allowed");
                return;
            }

            if (jvbCall != null)
            {
                logger.error(callContext +
                    " JVB conference call already started ");
                CallManager.hangupCall(event.getSourceCall(),
                    200, "Call completed elsewhere");
                return;
            }

            if (!started || xmppProvider == null)
            {
                logger.error(callContext + " Instance disposed");
                return;
            }

            Call jvbCall = event.getSourceCall();
            setJvbCall(jvbCall);
            jvbCall.setData(CallContext.class, callContext);

            peer.addCallPeerConferenceListener(JvbConference.this);
            peer.addPropertyChangeListener(JvbConference.this);

            peer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer p = evt.getSourceCallPeer();
                    CallPeerState peerState = p.getState();

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        p.removeCallPeerListener(this);

                        if (callContext.getDestination() == null && gatewaySession instanceof SipGatewaySession)
                        {
                            setPresenceStatus(peerState.getStateString());
                        }
                    }
                }
            });

            if (peer instanceof MediaAwareCallPeer)
            {
                ((MediaAwareCallPeer)peer).getMediaHandler()
                    .setDisableHolePunching(true);
            }

            jvbCall.addCallChangeListener(callChangeListener);

            gatewaySession.onConferenceCallInvited(jvbCall);
        }

        @Override
        public void outgoingCallCreated(CallEvent event) { }

        @Override
        public void callEnded(CallEvent event) { }
    }

    private class JvbCallChangeListener
        extends CallChangeAdapter
    {
        @Override
        public synchronized void callStateChanged(CallChangeEvent evt)
        {
            if (jvbCall != evt.getSourceCall())
            {
                logger.error(
                    callContext + " Call change event for different call ? "
                        + evt.getSourceCall() + " : " + jvbCall);
                return;
            }

            if (jvbCall.getCallState() == CallState.CALL_IN_PROGRESS)
            {
                logger.info(callContext + " JVB conference call IN_PROGRESS.");
                gatewaySession.onJvbCallEstablished();

                AudioModeration avMod = JvbConference.this.getAudioModeration();
                if (avMod != null)
                {
                    avMod.maybeProcessStartMuted();
                }

                checkReceivedMediaTimer.schedule(new MediaActivityChecker(), JVB_ACTIVITY_CHECK_DELAY);
            }
            else if (jvbCall.getCallState() == CallState.CALL_ENDED)
            {
                onJvbCallEnded();
            }
        }
    }

    private Map<String, String> createAccountPropertiesForCallId(CallContext ctx, String resourceName)
    {
        HashMap<String, String> properties = new HashMap<>();

        String domain = ctx.getRoomJidDomain();

        properties.put(ProtocolProviderFactory.USER_ID, resourceName + "@" + domain);
        properties.put(ProtocolProviderFactory.SERVER_ADDRESS, domain);
        properties.put(ProtocolProviderFactory.SERVER_PORT, "5222");

        properties.put(ProtocolProviderFactory.RESOURCE, resourceName);
        properties.put(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, "false");
        properties.put(ProtocolProviderFactory.RESOURCE_PRIORITY, "30");

        properties.put(JabberAccountID.ANONYMOUS_AUTH, "true");
        properties.put(ProtocolProviderFactory.IS_CARBON_DISABLED, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_ENCRYPTION, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE,
            "false");
        properties.put(ProtocolProviderFactory.IS_USE_ICE, "true");
        properties.put(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, "false");
        properties.put(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL, "false");
        properties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "false");
        properties.put(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES,
            "false");
        properties.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);
        properties.put(ProtocolProviderFactory.IS_USE_UPNP, "false");
        properties.put(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, "true");
        properties.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL
            + ".DTLS-SRTP", "0");
        properties.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS
            + ".DTLS-SRTP", "true");

        AbstractGateway gw = gatewaySession.getGateway();
        String overridePrefix = "org.jitsi.jigasi.xmpp.acc";
        List<String> overriddenProps =
            JigasiBundleActivator.getConfigurationService()
                .getPropertyNamesByPrefix(overridePrefix, false);

        if (gw instanceof SipGateway
            && Boolean.parseBoolean(((SipGateway) gw).getSipAccountProperty("PREVENT_AUTH_LOGIN")))
        {
            overriddenProps.remove(overridePrefix + "." + ProtocolProviderFactory.USER_ID);
            overriddenProps.remove(overridePrefix + ".PASS");
            overriddenProps.remove(overridePrefix + "." + JabberAccountID.ANONYMOUS_AUTH);
            overriddenProps.remove(overridePrefix + "." + ProtocolProviderFactory.IS_ALLOW_NON_SECURE);
        }

        for (String overridenProp : overriddenProps)
        {
            String key = overridenProp.replace(overridePrefix + ".", "");
            String value = JigasiBundleActivator.getConfigurationService()
                .getString(overridenProp);

            if ("org.jitsi.jigasi.xmpp.acc.PASS".equals(overridenProp))
            {
                this.xmppPassword = value;
                properties.put(ProtocolProviderFactory.PASSWORD, value);
            }
            else if ("org.jitsi.jigasi.xmpp.acc.BOSH_URL_PATTERN".equals(overridenProp))
            {
                if (StringUtils.isEmpty(ctx.getBoshURL()))
                {
                    ctx.setBoshURL(value);
                }
            }
            else
            {
                properties.put(key, value);
            }
        }

        String boshUrl = ctx.getBoshURL();
        if (StringUtils.isNotEmpty(boshUrl))
        {
            boshUrl = boshUrl.replace(
                "{roomName}", callContext.getConferenceName());

            logger.info(ctx + " Using bosh url:" + boshUrl);
            properties.put(JabberAccountID.BOSH_URL, boshUrl);

            if (ctx.hasAuthToken() &&  ctx.getAuthUserId() != null)
            {
                properties.put(ProtocolProviderFactory.USER_ID, ctx.getAuthUserId());
            }
        }

        String accountUID = "Jabber:" + properties.get(ProtocolProviderFactory.USER_ID) + "/" + resourceName;
        properties.put(ProtocolProviderFactory.ACCOUNT_UID, accountUID);

        if (!gatewaySession.isTranslatorSupported())
        {
            properties.put(ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE,
                "false");
        }

        return properties;
    }

    @Override
    public void conferenceFocusChanged(CallPeerConferenceEvent conferenceEvent)
    {

    }

    @Override
    public void conferenceMemberAdded(CallPeerConferenceEvent conferenceEvent)
    {
        ConferenceMember conferenceMember
            = conferenceEvent.getConferenceMember();

        this.gatewaySession.notifyConferenceMemberJoined(conferenceMember);
    }

    @Override
    public void conferenceMemberErrorReceived(CallPeerConferenceEvent conferenceEvent)
    {

    }

    @Override
    public void conferenceMemberRemoved(CallPeerConferenceEvent conferenceEvent)
    {
        ConferenceMember conferenceMember
            = conferenceEvent.getConferenceMember();

        this.gatewaySession.notifyConferenceMemberLeft(conferenceMember);
    }

    private void inviteFocus(final EntityBareJid roomIdentifier)
    {
        if (callContext == null || callContext.getRoomJidDomain() == null)
        {
            logger.error(this.callContext
                + " No domain name info to use for inviting focus!"
                + " Please set DOMAIN_BASE to the sip account.");
            return;
        }

        ConferenceIq focusInviteIQ = new ConferenceIq();
        focusInviteIQ.setRoom(roomIdentifier);

        try
        {
            focusInviteIQ.setType(IQ.Type.set);
            focusInviteIQ.setTo(JidCreate.domainBareFrom(
                gatewaySession.getFocusResourceAddr() + "." + callContext.getRoomJidDomain()));
        }
        catch (XmppStringprepException e)
        {
            logger.error(this.callContext +
                " Could not create destination address for focus invite", e);
            return;
        }

        if (xmppProvider instanceof ProtocolProviderServiceJabberImpl)
        {
            StanzaCollector collector = null;
            try
            {
                collector = getConnection()
                    .createStanzaCollectorAndSend(focusInviteIQ);
                collector.nextResultOrThrow();
            }
            catch (SmackException
                | XMPPException.XMPPErrorException
                | InterruptedException e)
            {
                logger.error(this.callContext +
                    " Could not invite the focus to the conference", e);
            }
            finally
            {
                if (collector != null)
                {
                    collector.cancel();
                }
            }
        }
    }

    private void setJvbCall(Call newJvbCall)
    {
        synchronized(jvbCallWriteSync)
        {
            if (newJvbCall == null)
            {
                if (this.jvbCall != null)
                {
                    this.jvbCall.removeCallChangeListener(callChangeListener);
                }
            }

            this.jvbCall = newJvbCall;

            inviteTimeout.maybeScheduleInviteTimeout();
        }
    }

    public XMPPConnection getConnection()
    {
        if (this.xmppProvider instanceof ProtocolProviderServiceJabberImpl)
        {
            return ((ProtocolProviderServiceJabberImpl) this.xmppProvider)
                .getConnection();
        }

        return null;
    }

    public void onPasswordReceived(String pwd)
    {
        if (this.mucRoom != null)
        {
            logger.warn(this.callContext + " Strange received a password after joining the room");
            return;
        }

        this.callContext.setRoomPassword(pwd);

        if (this.lobby != null)
        {
            this.lobby.leave();
        }

        joinConferenceRoom();
    }

    public void setLobbyEnabled(boolean value)
    {
        lobbyEnabled = value;
    }

    private String getMeetingId()
    {
        if (this.meetingId == null)
        {
            try
            {
                DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(getConnection())
                    .discoverInfo(((ChatRoomJabberImpl) this.mucRoom).getIdentifierAsJid());

                DataForm df = (DataForm) info.getExtension(DataForm.NAMESPACE);
                FormField meetingIdField = df.getField(DATA_FORM_MEETING_ID_FIELD_NAME);
                if (meetingIdField != null)
                {
                    this.meetingId = meetingIdField.getFirstValue();
                }
            }
            catch (Exception e)
            {
                logger.error(this.callContext + " Error checking room configuration", e);
            }
        }

        return this.meetingId;
    }

    private void updateFromRoomConfiguration()
    {
        try
        {
            DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(getConnection()).
                discoverInfo(((ChatRoomJabberImpl)this.mucRoom).getIdentifierAsJid());

            DataForm df = (DataForm) info.getExtension(DataForm.NAMESPACE);
            boolean lobbyEnabled = df.getField(Lobby.DATA_FORM_LOBBY_ROOM_FIELD) != null;
            boolean singleModeratorEnabled = df.getField(Lobby.DATA_FORM_SINGLE_MODERATOR_FIELD) != null;
            setLobbyEnabled(lobbyEnabled);
            this.singleModeratorEnabled = singleModeratorEnabled;
        }
        catch(Exception e)
        {
            logger.error(this.callContext + " Error checking room configuration", e);
        }
    }

    class JvbConferenceStopTimeout
        implements Runnable
    {
        private final Object syncRoot = new Object();

        private boolean willCauseTimeout = true;

        private long timeout;

        Thread timeoutThread;

        private final String errorLog;
        private final String endReason;
        private final String name;

        JvbConferenceStopTimeout(String name, String reason, String errorLog)
        {
            this.name = name;
            this.endReason = reason;
            this.errorLog = errorLog;
        }

        void scheduleTimeout()
        {
            if (AbstractGateway.getJvbInviteTimeout() > 0)
                this.scheduleTimeout(AbstractGateway.getJvbInviteTimeout());
        }

        void scheduleTimeout(long timeout)
        {
            synchronized (syncRoot)
            {
                if (timeoutThread != null)
                {
                    return;
                }

                this.timeout = timeout;

                timeoutThread = new Thread(this, name);
                willCauseTimeout = true;
                timeoutThread.start();
                logger.debug(callContext + " Scheduled new " + this);
            }
        }

        @Override
        public void run()
        {
            synchronized (syncRoot)
            {
                try
                {
                    syncRoot.wait(timeout);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            if (willCauseTimeout)
            {
                logger.error(callContext + " "
                    + errorLog + " (" + timeout + " ms)");

                JvbConference.this.endReason = this.endReason;
                JvbConference.this.endReasonCode
                    = OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT;

                stop();
            }

            timeoutThread = null;
            logger.debug("Timeout thread is done " + this);
        }

        private void cancel()
        {
            synchronized (syncRoot)
            {
                willCauseTimeout = false;

                if (timeoutThread == null)
                {
                    return;
                }

                logger.debug("Trying to cancel " + this);

                syncRoot.notifyAll();
            }

            logger.debug("Canceled " + this);
        }

        void maybeScheduleInviteTimeout()
        {
            synchronized(jvbCallWriteSync)
            {
                if (JvbConference.this.jvbCall == null
                        && JvbConference.this.started
                        && AbstractGateway.getJvbInviteTimeout() > 0)
                {
                    this.scheduleTimeout(AbstractGateway.getJvbInviteTimeout());
                }
                else
                {
                    this.cancel();
                }
            }
        }

        @Override
        public String toString()
        {
            return "JvbConferenceStopTimeout[" + callContext
                + ", willCauseTimeout:" + willCauseTimeout + " details:"
                + (willCauseTimeout ? endReason + "," + errorLog: "")
                + "]@"+ hashCode();
        }
    }

    private class RoomConfigurationChangeListener
        implements StanzaListener
    {
        @Override
        public void processStanza(Stanza stanza)
        {
            MUCUser mucUser = stanza.getExtension(MUCUser.class);

            if (mucUser == null)
            {
                return;
            }

            if (mucUser.getStatus().contains(MUCUser.Status.create(104)))
            {
                updateFromRoomConfiguration();
            }
        }
    }

    private class MediaActivityChecker
        extends TimerTask
    {
        @Override
        public void run()
        {
            if (!started)
            {
                logger.warn("Media activity checker exiting early as call is not started!");
                return;
            }

            CallPeer peer = jvbCall.getCallPeers().next();

            if (peer == null)
            {
                dropCall();
            }

            if (peer instanceof MediaAwareCallPeer)
            {
                MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

                CallPeerMediaHandler mediaHandler = peerMedia.getMediaHandler();
                if (mediaHandler != null)
                {
                    MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);

                    if (stream == null)
                    {
                        dropCall();
                    }

                    if (stream instanceof AudioMediaStreamImpl)
                    {
                        try
                        {
                            if (((AudioMediaStreamImpl) stream).getLastInputActivityTime() <= 0)
                            {
                                dropCall();
                            }
                        }
                        catch(IOException e)
                        {
                            logger.error("Error obtaining last activity while checking for media activity", e);
                        }
                    }
                }
                else
                {
                    dropCall();
                }
            }
        }

        private void dropCall()
        {
            Statistics.incrementTotalCallsJvbNoMedia();
            logger.error(callContext + " No activity on JVB conference call will stop");

            stop();
        }
    }
}
