package org.diploma;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import org.diploma.sip.SipInfoJsonProtocol;
import org.diploma.util.Util;
import org.diploma.sip.*;
import org.diploma.util.*;
import org.jitsi.utils.logging.*;
import org.jitsi.xmpp.extensions.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.disco.*;
import org.jivesoftware.smackx.disco.packet.*;
import org.json.simple.parser.*;
import org.jxmpp.jid.*;

import org.json.simple.*;
import org.jxmpp.jid.impl.*;

import java.util.*;

public class AudioModeration
    implements ChatRoomLocalUserRoleListener
{
    private final static Logger logger = Logger.getLogger(AudioModeration.class);

    public static final String MUTED_FEATURE_NAME = "http://jitsi.org/protocol/audio-mute";

    private MuteIqHandler muteIqHandler = null;

    private final SipGatewaySession gatewaySession;

    private boolean startAudioMuted = false;

    private final JvbConference jvbConference;

    private static final AudioMutedExtension initialAudioMutedExtension = new AudioMutedExtension();
    static
    {
        initialAudioMutedExtension.setAudioMuted(false);
    }

    private final CallContext callContext;

    private String avModerationAddress = null;

    private boolean avModerationEnabled = false;

    private boolean isAllowedToUnmute = true;

    private static final RaiseHandExtension lowerHandExtension = new RaiseHandExtension();

    private final AVModerationListener avModerationListener;

    public AudioModeration(JvbConference jvbConference, SipGatewaySession gatewaySession, CallContext ctx)
    {
        this.gatewaySession = gatewaySession;
        this.jvbConference = jvbConference;
        this.callContext = ctx;
        this.avModerationListener = new AVModerationListener();
    }

    static ExtensionElement addSupportedFeatures(OperationSetJitsiMeetToolsJabber meetTools)
    {
        if (isMutingSupported())
        {
            meetTools.addSupportedFeature(MUTED_FEATURE_NAME);
            return Util.createFeature(MUTED_FEATURE_NAME);
        }

        return null;
    }

    public void clean()
    {
        XMPPConnection connection = jvbConference.getConnection();

        if (jvbConference.getJvbRoom() != null)
        {
            jvbConference.getJvbRoom().removelocalUserRoleListener(this);
        }

        if (connection == null)
        {
            return;
        }

        if (muteIqHandler != null)
        {
            connection.unregisterIQRequestHandler(muteIqHandler);
        }
    }

    void notifyWillJoinJvbRoom(ChatRoom mucRoom)
    {
        if (mucRoom instanceof ChatRoomJabberImpl)
        {
            ((ChatRoomJabberImpl) mucRoom).addPresencePacketExtensions(initialAudioMutedExtension);
        }

        mucRoom.addLocalUserRoleListener(this);

        if (isMutingSupported())
        {
            if (muteIqHandler == null)
            {
                muteIqHandler = new MuteIqHandler(this.gatewaySession);
            }

            jvbConference.getConnection().registerIQRequestHandler(muteIqHandler);
        }
    }

    public void setStartAudioMuted(boolean value)
    {
        this.startAudioMuted = value;
    }

    public static boolean isMutingSupported()
    {
        return JigasiBundleActivator.isSipStartMutedEnabled();
    }

    public void onJSONReceived(CallPeer callPeer, JSONObject jsonObject, Map<String, Object> params)
    {
        try
        {
            if (jsonObject.containsKey("i"))
            {
                int msgId = ((Long)jsonObject.get("i")).intValue();
                if (logger.isDebugEnabled())
                {
                    logger.debug("Received message " + msgId);
                }
            }

            if (jsonObject.containsKey("t"))
            {
                int messageType = ((Long)jsonObject.get("t")).intValue();

                if (messageType == SipInfoJsonProtocol.MESSAGE_TYPE.REQUEST_ROOM_ACCESS)
                {
                    this.jvbConference.onPasswordReceived(
                        SipInfoJsonProtocol.getPasswordFromRoomAccessRequest(jsonObject));
                }
            }

            if (jsonObject.containsKey("type"))
            {

                if (!jsonObject.containsKey("id"))
                {
                    logger.error(this.callContext + " Unknown json object id!");
                    return;
                }

                String id = (String)jsonObject.get("id");
                String type = (String)jsonObject.get("type");

                if (type.equalsIgnoreCase("muteResponse"))
                {
                    if (!jsonObject.containsKey("status"))
                    {
                        logger.error(this.callContext + " muteResponse without status!");
                        return;
                    }

                    if (((String) jsonObject.get("status")).equalsIgnoreCase("OK"))
                    {
                        JSONObject data = (JSONObject) jsonObject.get("data");

                        boolean bMute = (boolean)data.get("audio");

                        this.setChatRoomAudioMuted(bMute);
                    }
                }
                else if (type.equalsIgnoreCase("muteRequest"))
                {
                    JSONObject data = (JSONObject) jsonObject.get("data");

                    boolean bAudioMute = (boolean)data.get("audio");

                    if (this.requestAudioMuteByJicofo(bAudioMute))
                    {
                        this.gatewaySession.sendJson(
                            SipInfoJsonProtocol.createSIPJSONAudioMuteResponse(bAudioMute, true, id));

                        this.setChatRoomAudioMuted(bAudioMute);
                    }
                    else
                    {
                        this.gatewaySession.sendJson(
                            SipInfoJsonProtocol.createSIPJSONAudioMuteResponse(bAudioMute, false, id));
                    }
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(this.callContext + " Error processing json ", ex);
        }
    }

    void setChatRoomAudioMuted(boolean muted)
    {
        ChatRoom mucRoom = this.jvbConference.getJvbRoom();

        if (mucRoom != null)
        {
            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                ((ChatRoomJabberImpl) mucRoom).removePresencePacketExtensions(initialAudioMutedExtension);

                if (!muted)
                {
                    ((ChatRoomJabberImpl) mucRoom).addPresencePacketExtensions(lowerHandExtension);
                }
            }

            AudioMutedExtension audioMutedExtension = new AudioMutedExtension();

            audioMutedExtension.setAudioMuted(muted);

            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = this.jvbConference.getXmppProvider()
                .getOperationSet(OperationSetJitsiMeetToolsJabber.class);

            jitsiMeetTools.sendPresenceExtension(mucRoom, audioMutedExtension);
        }
    }

    public boolean requestAudioMuteByJicofo(boolean bMuted)
    {
        ChatRoom mucRoom = this.jvbConference.getJvbRoom();

        if (!bMuted && this.avModerationEnabled && !isAllowedToUnmute)
        {
            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = this.jvbConference.getXmppProvider()
                .getOperationSet(OperationSetJitsiMeetToolsJabber.class);

            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                ((ChatRoomJabberImpl) mucRoom).removePresencePacketExtensions(lowerHandExtension);
            }

            jitsiMeetTools.sendPresenceExtension(mucRoom, new RaiseHandExtension().setRaisedHandValue(true));

            return false;
        }

        StanzaCollector collector = null;
        try
        {
            String roomName = mucRoom.getIdentifier();

            String jidString = roomName  + "/" + this.jvbConference.getResourceIdentifier().toString();
            Jid memberJid = JidCreate.from(jidString);
            String roomJidString = roomName + "/" + this.gatewaySession.getFocusResourceAddr();
            Jid roomJid = JidCreate.from(roomJidString);

            MuteIq muteIq = new MuteIq();
            muteIq.setJid(memberJid);
            muteIq.setMute(bMuted);
            muteIq.setType(IQ.Type.set);
            muteIq.setTo(roomJid);

            collector = this.jvbConference.getConnection().createStanzaCollectorAndSend(muteIq);

            collector.nextResultOrThrow();
        }
        catch(Exception ex)
        {
            logger.error(this.callContext + " Error sending xmpp request for audio mute", ex);

            return false;
        }
        finally
        {
            if (collector != null)
            {
                collector.cancel();
            }
        }

        return true;
    }

    public void maybeProcessStartMuted()
    {
        Call jvbConferenceCall = this.gatewaySession.getJvbCall();
        Call sipCall = this.gatewaySession.getSipCall();

        if (this.startAudioMuted
            && isMutingSupported()
            && jvbConferenceCall != null
            && jvbConferenceCall.getCallState() == CallState.CALL_IN_PROGRESS
            && sipCall != null
            && sipCall.getCallState() == CallState.CALL_IN_PROGRESS)
        {
            if (!this.avModerationEnabled)
            {
                this.requestAudioMuteByJicofo(true);
            }

            mute();

            this.startAudioMuted = false;
        }
    }

    public void mute()
    {
        if (!isMutingSupported())
            return;

        try
        {
            logger.info(this.callContext + " Sending mute request avModeration:" + this.avModerationEnabled
                + " allowed to unmute:" + this.isAllowedToUnmute);

            this.gatewaySession.sendJson(SipInfoJsonProtocol.createSIPJSONAudioMuteRequest(true));
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending mute request", ex);
        }
    }

    public void xmppProviderRegistered()
    {
        if (this.callContext.getRoomJidDomain() != null)
        {
            try
            {
                long startQuery = System.currentTimeMillis();

                if (this.jvbConference.getConnection() == null)
                {
                    return;
                }

                DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(this.jvbConference.getConnection())
                    .discoverInfo(JidCreate.domainBareFrom(this.callContext.getRoomJidDomain()));

                DiscoverInfo.Identity avIdentity =
                    info.getIdentities().stream().
                        filter(di -> di.getCategory().equals("component") && di.getType().equals("av_moderation"))
                        .findFirst().orElse(null);

                if (avIdentity != null)
                {
                    this.avModerationAddress = avIdentity.getName();
                    logger.info(String.format("%s Discovered %s for %oms.",
                        this.callContext, this.avModerationAddress, System.currentTimeMillis() - startQuery));
                }
            }
            catch(Exception e)
            {
                logger.error("Error querying for av moderation address", e);
            }
        }

        if (this.avModerationAddress != null)
        {
            try
            {
                this.jvbConference.getConnection().addAsyncStanzaListener(
                    this.avModerationListener,
                    new AndFilter(
                        MessageTypeFilter.NORMAL,
                        FromMatchesFilter.create(JidCreate.domainBareFrom(this.avModerationAddress))));
            }
            catch(Exception e)
            {
                logger.error("Error adding AV moderation listener", e);
            }
        }
    }

    public void cleanXmppProvider()
    {
        XMPPConnection connection = jvbConference.getConnection();

        if (connection == null)
        {
            return;
        }

        connection.removeAsyncStanzaListener(this.avModerationListener);
    }

    @Override
    public void localUserRoleChanged(ChatRoomLocalUserRoleChangeEvent evt)
    {
        if (!avModerationEnabled || evt.getNewRole().equals(evt.getPreviousRole()))
        {
            return;
        }

        boolean signalAvModerationEnabled = true;
        if (evt.getNewRole().equals(ChatRoomMemberRole.OWNER) || evt.getNewRole().equals(ChatRoomMemberRole.MODERATOR))
        {
            isAllowedToUnmute = true;

            signalAvModerationEnabled = false;
        }
        else
        {
            isAllowedToUnmute = false;
        }

        try
        {
            gatewaySession.sendJson(
                SipInfoJsonProtocol.createAVModerationEnabledNotification(signalAvModerationEnabled));
        }
        catch(OperationFailedException e)
        {
            logger.info(callContext + " Error sending av moderation enable/disable notification", e);
        }
    }

    private class MuteIqHandler
        extends AbstractIqRequestHandler
    {
        private final AbstractGatewaySession gatewaySession;

        public MuteIqHandler(AbstractGatewaySession gatewaySession)
        {
            super(
                MuteIq.ELEMENT,
                MuteIq.NAMESPACE,
                IQ.Type.set,
                Mode.sync);

            this.gatewaySession = gatewaySession;
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest)
        {
            return handleMuteIq((MuteIq) iqRequest);
        }

        private IQ handleMuteIq(MuteIq muteIq)
        {
            Boolean doMute = muteIq.getMute();
            Jid from = muteIq.getFrom();

            if (doMute == null || !from.getResourceOrEmpty().toString()
                    .equals(this.gatewaySession.getFocusResourceAddr()))
            {
                return IQ.createErrorResponse(muteIq,
                    StanzaError.getBuilder(StanzaError.Condition.item_not_found)
                        .build());
            }

            if (doMute)
            {
                mute();
            }

            return IQ.createResultIQ(muteIq);
        }
    }

    private static class RaiseHandExtension
        extends AbstractPacketExtension
    {
        public static final String NAMESPACE = "jabber:client";

        public static final String ELEMENT_NAME = "jitsi_participant_raisedHand";

        public RaiseHandExtension()
        {
            super(NAMESPACE, ELEMENT_NAME);
        }

        public ExtensionElement setRaisedHandValue(Boolean value)
        {
            setText(value ? value.toString() : null);

            return this;
        }
    }

    private class AVModerationListener
        implements StanzaListener
    {
        @Override
        public void processStanza(Stanza packet)
        {
            JsonMessageExtension jsonMsg =
                packet.getExtension(JsonMessageExtension.class);

            if (jsonMsg == null)
            {
                return;
            }

            try
            {
                Object o = new JSONParser().parse(jsonMsg.getJson());

                if (o instanceof JSONObject)
                {
                    JSONObject data = (JSONObject) o;

                    if (data.get("type").equals("av_moderation"))
                    {
                        Object enabledObj = data.get("enabled");
                        Object approvedObj = data.get("approved");
                        Object removedObj = data.get("removed");
                        Object mediaTypeObj = data.get("mediaType");

                        if (mediaTypeObj == null || !mediaTypeObj.equals("audio"))
                        {
                            return;
                        }

                        if (enabledObj != null)
                        {
                            avModerationEnabled = (Boolean) enabledObj;
                            logger.info(callContext + " AV moderation has been enabled:" + avModerationEnabled);

                            ChatRoomMemberRole role = jvbConference.getJvbRoom().getUserRole();
                            if (!role.equals(ChatRoomMemberRole.OWNER) && !role.equals(ChatRoomMemberRole.MODERATOR))
                            {
                                isAllowedToUnmute = false;

                                gatewaySession.sendJson(
                                    SipInfoJsonProtocol.createAVModerationEnabledNotification(avModerationEnabled));
                            }
                        }
                        else if (removedObj != null && (Boolean) removedObj)
                        {
                            isAllowedToUnmute = false;
                            gatewaySession.sendJson(SipInfoJsonProtocol.createAVModerationDeniedNotification());
                        }
                        else if (approvedObj != null && (Boolean) approvedObj)
                        {
                            isAllowedToUnmute = true;
                            gatewaySession.sendJson(SipInfoJsonProtocol.createAVModerationApprovedNotification());
                        }
                    }
                }
            }
            catch(Exception e)
            {
                logger.error(callContext + " Error parsing", e);
            }
        }
    }
}
