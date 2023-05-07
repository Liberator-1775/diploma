package org.diploma.lobby;

import net.java.sip.communicator.service.protocol.event.*;
import org.diploma.CallContext;
import org.diploma.JvbConference;
import org.diploma.SipGatewaySession;
import org.diploma.sounds.SoundNotificationManager;
import org.diploma.*;

import org.diploma.sounds.*;
import org.jitsi.utils.logging.Logger;
import org.jivesoftware.smackx.nick.packet.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.parts.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import static net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent.*;

public class Lobby
    implements ChatRoomInvitationListener,
               LocalUserChatRoomPresenceListener
{
    private final static Logger logger = Logger.getLogger(Lobby.class);

    public static final String DATA_FORM_LOBBY_ROOM_FIELD = "muc#roominfo_lobbyroom";

    public static final String DATA_FORM_SINGLE_MODERATOR_FIELD = "muc#roominfo_moderator_identity";

    private final ProtocolProviderService xmppProvider;

    private final EntityFullJid roomJid;

    private final Jid mainRoomJid;

    private final CallContext callContext;

    private ChatRoom mucRoom = null;

    private final JvbConference jvbConference;

    private final SipGatewaySession sipGatewaySession;

    public Lobby(ProtocolProviderService protocolProviderService,
                 CallContext context,
                 EntityFullJid lobbyJid,
                 Jid roomJid,
                 JvbConference jvbConference,
                 SipGatewaySession sipGateway)
    {
        super();

        this.xmppProvider = protocolProviderService;

        this.roomJid = lobbyJid;

        this.callContext = context;

        this.mainRoomJid = roomJid;

        this.jvbConference = jvbConference;

        this.sipGatewaySession = sipGateway;
    }

    public void join()
            throws OperationFailedException,
            OperationNotSupportedException
    {
        joinRoom(getRoomJid());

        this.sipGatewaySession.notifyOnLobbyWaitReview(this.mucRoom);
    }

    protected void joinRoom(Jid roomJid) throws OperationFailedException, OperationNotSupportedException
    {
        OperationSetMultiUserChat muc
                = this.xmppProvider.getOperationSet(OperationSetMultiUserChat.class);

        muc.addInvitationListener(this);

        muc.addPresenceListener(this);

        ChatRoom mucRoom = muc.findRoom(roomJid.toString());

        setupChatRoom(mucRoom);

        mucRoom.joinAs(getResourceIdentifier().toString());

        this.mucRoom = mucRoom;
    }

    public void leave()
    {
        leaveRoom();
    }

    protected void leaveRoom()
    {
        OperationSetMultiUserChat muc = this.xmppProvider.getOperationSet(OperationSetMultiUserChat.class);

        muc.removeInvitationListener(this);

        muc.removePresenceListener(this);

        if (mucRoom == null)
        {
            logger.warn(getCallContext() + " MUC room is null");
            return;
        }

        mucRoom.leave();

        mucRoom = null;
    }

    @Override
    public void invitationReceived(ChatRoomInvitationReceivedEvent chatRoomInvitationReceivedEvent)
    {
        try
        {
            byte[] pass = chatRoomInvitationReceivedEvent.getInvitation().getChatRoomPassword();
            if (pass != null)
            {
                callContext.setRoomPassword(new String(pass));
            }

            this.notifyAccessGranted();

            if (this.jvbConference != null)
            {
                this.jvbConference.joinConferenceRoom();
            }
            else
            {
                logger.error(getCallContext() + " No JVB conference!!!");
            }

            leave();
        }
        catch (Exception ex)
        {
            logger.error(getCallContext() + " " + ex, ex);
        }
    }

    private void notifyAccessGranted()
    {
        this.sipGatewaySession.getSoundNotificationManager()
            .notifyLobbyAccessGranted();

        this.sipGatewaySession.notifyLobbyAllowedJoin();
        this.sipGatewaySession.notifyLobbyLeft();
    }

    @Override
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt)
    {
        try
        {
            if (evt.getChatRoom().equals(this.mucRoom))
            {
                SoundNotificationManager soundManager = this.sipGatewaySession.getSoundNotificationManager();
                if (evt.getEventType().equals(LOCAL_USER_KICKED))
                {
                    soundManager.notifyLobbyAccessDenied();

                    sipGatewaySession.notifyLobbyRejectedJoin();

                    leave();

                    return;
                }

                if (evt.getEventType().equals(LOCAL_USER_LEFT))
                {
                    String alternateAddress = evt.getAlternateAddress();

                    if (alternateAddress != null)
                    {
                        accessGranted(alternateAddress);
                    }

                    return;
                }

                if (evt.getEventType().equals(LOCAL_USER_JOIN_FAILED))
                {

                    logger.error("Failed to join lobby!");

                    return;
                }

                if (evt.getEventType().equals(LOCAL_USER_ROOM_DESTROYED))
                {
                    String alternateAddress = evt.getAlternateAddress();

                    if (alternateAddress == null)
                    {
                        soundManager.notifyLobbyRoomDestroyed();
                    }
                    else
                    {
                        accessGranted(alternateAddress);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(getCallContext() + " " + ex, ex);
        }
    }

    private void accessGranted(String alternateAddress)
        throws XmppStringprepException
    {
        Jid alternateJid = JidCreate.entityBareFrom(alternateAddress);

        if (!alternateJid.equals(this.mainRoomJid))
        {
            logger.warn(getCallContext() + " Alternate Jid(" + alternateJid
                + ") not the same as main room Jid(" + this.mainRoomJid + ")!");
            return;
        }

        try
        {
            leave();
        }
        catch(Exception e)
        {
            logger.error(getCallContext() + " Error leaving lobby", e);
        }

        this.notifyAccessGranted();

        if (this.jvbConference != null)
        {
            this.jvbConference.setLobbyEnabled(false);
            this.jvbConference.joinConferenceRoom();
        }
        else
        {
            logger.error(getCallContext() + " No JVB conference!!!");
        }
    }

    public CallContext getCallContext()
    {
        return this.callContext;
    }

    public Jid getRoomJid()
    {
        return this.roomJid;
    }

    public Resourcepart getResourceIdentifier()
    {
        return this.roomJid.getResourceOrNull();
    }

    void setupChatRoom(ChatRoom mucRoom)
    {
        if (mucRoom instanceof ChatRoomJabberImpl)
        {
            String displayName = this.sipGatewaySession.getMucDisplayName();
            if (displayName != null)
            {
                ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(
                        new Nick(displayName));
            }
            else
            {
                logger.error(this.callContext + " No display name to use...");
            }
        }
    }
}
