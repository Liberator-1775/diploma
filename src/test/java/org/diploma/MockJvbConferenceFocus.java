package org.diploma;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.service.protocol.mock.muc.*;
import org.jitsi.utils.logging.Logger;
import org.osgi.framework.*;

public class MockJvbConferenceFocus
    implements ChatRoomMemberPresenceListener,
               ServiceListener
{
    private final static Logger logger
        = Logger.getLogger(MockJvbConferenceFocus.class);

    private final String roomName;

    private MockCall xmppCall;

    private MockMultiUserChat chatRoom;

    private String myName;

    private MockRoomMember myMember;

    private boolean leaveRoomAfterInvite;

    public MockJvbConferenceFocus(String roomName)
    {
        this.roomName = roomName;

        myName = "focus";
    }

    public void setup()
        throws OperationFailedException, OperationNotSupportedException
    {
        JigasiBundleActivator.osgiContext.addServiceListener(this);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED)
        {
            ServiceReference<?> ref = serviceEvent.getServiceReference();

            Object service = JigasiBundleActivator.osgiContext.getService(ref);
            if (service instanceof ProtocolProviderService)
            {
                ProtocolProviderService protocol
                    = (ProtocolProviderService) service;

                if (ProtocolNames.JABBER.equals(protocol.getProtocolName()))
                {
                    try
                    {
                        logger.info(
                            myName + " registers for provider " + protocol);

                        setXmppProvider(protocol);
                    }
                    catch (OperationFailedException e)
                    {
                        logger.error(e, e);
                    }
                    catch (OperationNotSupportedException e)
                    {
                        logger.error(e, e);
                    }
                }
            }
            JigasiBundleActivator.osgiContext.removeServiceListener(this);
        }
    }

    public void tearDown()
    {
        JigasiBundleActivator.osgiContext.removeServiceListener(this);

        if (chatRoom != null)
        {
            chatRoom.removeMemberPresenceListener(this);

            chatRoom.mockLeave(myMember.getName());

            myMember = null;

            chatRoom = null;
        }
    }

    public Call getCall()
    {
        return xmppCall;
    }

    public MockMultiUserChat getChatRoom()
    {
        return chatRoom;
    }

    private void inviteToConference(ChatRoomMember member)
    {
        MockBasicTeleOpSet xmppTele
            = (MockBasicTeleOpSet) member.getProtocolProvider()
                    .getOperationSet(OperationSetBasicTelephony.class);

        xmppCall
            = xmppTele.createIncomingCall(myMember.getName());

        logger.info(
            myName + " is inviting " + member.getName()
                + " to join conference in room " + roomName);

        if (leaveRoomAfterInvite)
        {
            logger.info(myName + " invited peer will leave the room");
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    logger.info(myName + " leaving the room");
                    tearDown();
                }
            }).start();
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED
            .equals(evt.getEventType()))
        {
            inviteToConference(evt.getChatRoomMember());
        }
    }

    private void setXmppProvider(ProtocolProviderService xmppProvider)
        throws OperationFailedException, OperationNotSupportedException
    {
        MockMultiUserChatOpSet xmppMucOpSet
            = (MockMultiUserChatOpSet) xmppProvider
                .getOperationSet(OperationSetMultiUserChat.class);

        this.chatRoom
            = (MockMultiUserChat) xmppMucOpSet.findRoom(roomName);

        if (chatRoom == null)
        {
            chatRoom = (MockMultiUserChat) xmppMucOpSet
                .createChatRoom(roomName, null);
        }

        logger.info(myName + " created room " + roomName);

        this.myMember = chatRoom.mockOwnerJoin(myName);

        if (chatRoom.getMembersCount() > 0)
        {
            for (ChatRoomMember member : chatRoom.getMembers())
            {
                if (member != myMember)
                {
                    inviteToConference(member);
                }
            }
        }
        chatRoom.addMemberPresenceListener(this);
    }

    public String getRoomName()
    {
        return roomName;
    }

    public void setLeaveRoomAfterInvite(boolean leaveRoomAfterInvite)
    {
        this.leaveRoomAfterInvite = leaveRoomAfterInvite;
    }
}
