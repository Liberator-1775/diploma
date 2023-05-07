package org.diploma;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.diploma.transcription.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.diploma.transcription.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.utils.logging.*;
import org.jitsi.utils.*;
import org.jivesoftware.smack.packet.Presence;
import org.json.simple.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import java.util.*;

public class TranscriptionGatewaySession
    extends AbstractGatewaySession
    implements TranscriptionListener,
        TranscriptionEventListener,
        TranslationResultListener
{
    private final static Logger logger
            = Logger.getLogger(TranscriptionGatewaySession.class);

    public final static String DISPLAY_NAME = "Transcriber";

    public final static int PRESENCE_UPDATE_WAIT_UNTIL_LEAVE_DURATION = 2500;

    private TranscriptionService service;

    private TranscriptHandler handler;

    private ChatRoom chatRoom = null;

    private Transcriber transcriber;

    private Call jvbCall = null;

    private List<TranscriptPublisher.Promise> finalTranscriptPromises
        = new LinkedList<>();

    public TranscriptionGatewaySession(AbstractGateway gateway,
                                       CallContext context,
                                       TranscriptionService service,
                                       TranscriptHandler handler)
    {
        super(gateway, context);
        this.service = service;
        this.handler = handler;

        this.transcriber = new Transcriber(this.service);

        if (this.service instanceof TranscriptionEventListener)
        {
            this.transcriber.addTranscriptionEventListener(
                (TranscriptionEventListener)this.service);
        }
    }

    @Override
    void onConferenceCallInvited(Call incomingCall)
    {
        transcriber.addTranscriptionListener(this);
        transcriber.addTranslationListener(this);
        transcriber.setRoomName(this.getCallContext().getRoomJid().toString());
        transcriber.setRoomUrl(getMeetingUrl());

        incomingCall.setConference(new MediaAwareCallConference()
        {
            @Override
            public MediaDevice getDefaultDevice(MediaType mediaType,
                MediaUseCase useCase)
            {
                if (MediaType.AUDIO.equals(mediaType))
                {
                    logger.info("Transcriber: Media Device Audio");
                    return transcriber.getMediaDevice();
                }
                logger.info("Transcriber: Media Device Video");
                return super.getDefaultDevice(mediaType, useCase);
            }
        });

        Exception error = this.onConferenceCallStarted(incomingCall);

        if (error != null)
        {
            logger.error(error, error);
        }
    }

    @Override
    Exception onConferenceCallStarted(Call jvbConferenceCall)
    {
        this.jvbCall = jvbConferenceCall;
        this.chatRoom = super.jvbConference.getJvbRoom();

        if (!service.isConfiguredProperly())
        {
            logger.warn("TranscriptionService is not properly configured");
            sendMessageToRoom("Transcriber is not properly " +
                "configured. Contact the service administrators and let them " +
                "know! I will now leave.");
            jvbConference.stop();
            return null;
        }

        for (TranscriptionResultPublisher pub
            : handler.getTranscriptResultPublishers())
        {
            if (pub instanceof TranscriptionEventListener)
                transcriber.addTranscriptionEventListener(
                    (TranscriptionEventListener)pub);
        }

        transcriber.addTranscriptionEventListener(this);

        transcriber.start();

        addInitialMembers();

        StringBuilder welcomeMessage = new StringBuilder();

        finalTranscriptPromises.addAll(handler.getTranscriptPublishPromises());
        for (TranscriptPublisher.Promise promise : finalTranscriptPromises)
        {
            if (promise.hasDescription())
            {
                welcomeMessage.append(promise.getDescription());
            }

            promise.maybeStartRecording(transcriber.getMediaDevice());
        }

        if (welcomeMessage.length() > 0)
        {
            sendMessageToRoom(welcomeMessage.toString());
        }

        try
        {
            CallManager.acceptCall(jvbConferenceCall);
        }
        catch(OperationFailedException e)
        {
            return e;
        }

        logger.debug("TranscriptionGatewaySession started transcribing");

        return null;
    }

    @Override
    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason)
    {
        if (!transcriber.finished())
        {
            transcriber.stop(null);

            for (TranscriptPublisher.Promise promise : finalTranscriptPromises)
            {
                promise.publish(transcriber.getTranscript());
            }
        }

        this.gateway.notifyCallEnded(this.callContext);

        logger.debug("Conference ended");
    }

    @Override
    void onJvbConferenceWillStop(JvbConference jvbConference, int reasonCode,
        String reason)
    {
        if (!transcriber.finished())
        {
            transcriber.willStop();
        }
    }

    @Override
    void notifyChatRoomMemberJoined(ChatRoomMember chatMember)
    {
        super.notifyChatRoomMemberJoined(chatMember);

        String identifier = getParticipantIdentifier(chatMember);

        if ("focus".equals(identifier))
        {
            return;
        }
        this.transcriber.updateParticipant(identifier, chatMember);
        this.transcriber.participantJoined(identifier);
    }

    @Override
    void notifyChatRoomMemberLeft(ChatRoomMember chatMember)
    {
        super.notifyChatRoomMemberLeft(chatMember);

        String identifier = getParticipantIdentifier(chatMember);
        this.transcriber.participantLeft(identifier);
    }

    @Override
    void notifyChatRoomMemberUpdated(ChatRoomMember chatMember, Presence presence)
    {
        super.notifyChatRoomMemberUpdated(chatMember, presence);

        String identifier = getParticipantIdentifier(chatMember);
        this.transcriber.updateParticipant(identifier, chatMember);

        if (transcriber.isTranscribing() &&
            !transcriber.isAnyParticipantRequestingTranscription())
        {
            new Thread(() ->
            {
                try
                {
                    Thread.sleep(PRESENCE_UPDATE_WAIT_UNTIL_LEAVE_DURATION);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }

                if (!transcriber.isAnyParticipantRequestingTranscription())
                {
                    jvbConference.stop();
                }
            }).start();
        }
    }

    @Override
    void notifyConferenceMemberJoined(ConferenceMember conferenceMember)
    {
        super.notifyConferenceMemberJoined(conferenceMember);

        String identifier = getParticipantIdentifier(conferenceMember);
        this.transcriber.updateParticipant(identifier, conferenceMember);
    }

    @Override
    void notifyConferenceMemberLeft(ConferenceMember conferenceMember)
    {
        super.notifyConferenceMemberLeft(conferenceMember);
    }

    @Override
    public boolean isTranslatorSupported()
    {
        return false;
    }

    @Override
    public String getDefaultInitStatus()
    {
        return null;
    }

    @Override
    public void notify(TranscriptionResult result)
    {
        sendTranscriptionResultToRoom(result);
    }

    @Override
    public void notify(TranslationResult result)
    {
        sendTranslationResultToRoom(result);
    }

    @Override
    public void completed()
    {

    }

    @Override
    public void failed(FailureReason reason)
    {
        this.jvbConference.stop();
    }

    @Override
    public void onJoinJitsiMeetRequest(Call call, String s,
                                       Map<String, String> map)
    {
        throw new UnsupportedOperationException("Incoming calls are " +
                "not supported by TranscriptionGatewaySession");
    }

    @Override
    public void onSessionStartMuted(boolean[] startMutedFlags)
    {

    }

    @Override
    public void onJSONReceived(CallPeer callPeer,
                               JSONObject jsonObject,
                               Map<String, Object> params)
    {

    }

    @Override
    public void toneReceived(DTMFReceivedEvent dtmfReceivedEvent)
    {
        throw new UnsupportedOperationException("TranscriptionGatewaySession " +
            "does " + "not support receiving DTMF tones");
    }

    private void addInitialMembers()
    {
        List<ConferenceMember> confMembers = getCurrentConferenceMembers();
        if (confMembers == null)
        {
            logger.warn("Cannot add initial ConferenceMembers to " +
                "transcription");
        }
        else
        {
            for (ConferenceMember confMember : confMembers)
            {
                if ("jvb".equals(confMember.getAddress()))
                {
                    continue;
                }

                String identifier = getParticipantIdentifier(confMember);

                this.transcriber.updateParticipant(identifier, confMember);
            }
        }

        List<ChatRoomMember> chatRoomMembers = getCurrentChatRoomMembers();
        if (chatRoomMembers == null)
        {
            logger.warn("Cannot add initial ChatRoomMembers to transcription");
            return;
        }

        for (ChatRoomMember chatRoomMember : chatRoomMembers)
        {
            ChatRoomMemberJabberImpl chatRoomMemberJabber;

            if (chatRoomMember instanceof ChatRoomMemberJabberImpl)
            {
                chatRoomMemberJabber
                    = (ChatRoomMemberJabberImpl) chatRoomMember;
            }
            else
            {
                logger.warn("Could not cast a ChatRoomMember to " +
                    "ChatRoomMemberJabberImpl");
                continue;
            }

            String identifier = getParticipantIdentifier(chatRoomMemberJabber);

            if ("focus".equals(identifier))
            {
                continue;
            }

            if (chatRoomMemberJabber.getJabberID().getResourceOrNull() == null)
            {
                continue;
            }

            this.transcriber.updateParticipant(identifier, chatRoomMember);
            this.transcriber.participantJoined(identifier);
        }
    }

    private List<ConferenceMember> getCurrentConferenceMembers()
    {
        if (jvbCall == null)
        {
            return null;
        }
        Iterator<? extends CallPeer> iter = jvbCall.getCallPeers();
        return
            iter.hasNext() ? iter.next().getConferenceMembers() : null;
    }

    private List<ChatRoomMember> getCurrentChatRoomMembers()
    {
        return chatRoom == null ? null : chatRoom.getMembers();
    }

    private String getConferenceMemberResourceID(ConferenceMember member)
    {
        try
        {
            Jid jid = JidCreate.from(member.getAddress());

            if (jid.hasResource())
            {
                return jid.getResourceOrThrow().toString();
            }
            else
            {
                return jid.toString();
            }
        }
        catch (XmppStringprepException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private String getParticipantIdentifier(ChatRoomMember chatRoomMember)
    {
        if (chatRoomMember == null)
        {
            return null;
        }

        return chatRoomMember.getName();
    }

    private String getParticipantIdentifier(ConferenceMember conferenceMember)
    {
        if (conferenceMember == null)
        {
            return null;
        }

        return getConferenceMemberResourceID(conferenceMember);
    }


    private void sendMessageToRoom(String messageString)
    {
        if (chatRoom == null)
        {
            logger.error("Cannot sent message as chatRoom is null");
            return;
        }

        Message message = chatRoom.createMessage(messageString);
        try
        {
            chatRoom.sendMessage(message);
            logger.debug("Sending message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send message " + messageString, e);
        }
    }

    private void sendTranscriptionResultToRoom(TranscriptionResult result)
    {
        handler.publishTranscriptionResult(this.chatRoom, result);
    }

    private void sendTranslationResultToRoom(TranslationResult result)
    {
        handler.publishTranslationResult(this.chatRoom, result);
    }

    @Override
    public String getMucDisplayName()
    {
        return TranscriptionGatewaySession.DISPLAY_NAME;
    }

    @Override
    public void notify(Transcriber transcriber, TranscriptEvent event)
    {
        if (event.getEvent() == Transcript.TranscriptEventType.START
                || event.getEvent() == Transcript.TranscriptEventType.WILL_END)
        {
            TranscriptionStatusExtension.Status status
                = event.getEvent() ==
                    Transcript.TranscriptEventType.WILL_END ?
                        TranscriptionStatusExtension.Status.OFF
                        : transcriber.isTranscribing() ?
                            TranscriptionStatusExtension.Status.ON
                            : TranscriptionStatusExtension.Status.OFF;

            TranscriptionStatusExtension extension
                = new TranscriptionStatusExtension();
            extension.setStatus(status);

            jvbConference.sendPresenceExtension(extension);
        }
    }

    public boolean hasCallResumeSupport()
    {
        return false;
    }
}
