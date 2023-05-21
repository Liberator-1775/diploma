package net.java.sip.communicator.service.protocol.mock.muc;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import java.util.*;
import org.jitsi.utils.logging.Logger;

public class MockMucShare
    implements ChatRoomMemberPresenceListener
{
    private final static Logger logger = Logger.getLogger(MockMucShare.class);

    private final String roomName;

    private List<MockMultiUserChat> groupedChats
        = new ArrayList<MockMultiUserChat>();

    public MockMucShare(String roomName)
    {
        this.roomName = roomName;
    }

    public void nextRoomCreated(MockMultiUserChat chatRoom)
    {
        synchronized(groupedChats)
        {
            groupedChats.add(chatRoom);
        }

        chatRoom.addMemberPresenceListener(this);

        for (ChatRoomMember member : chatRoom.getMembers())
        {
            broadcastMemberJoined(chatRoom, member);
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        String eventType = evt.getEventType();

        if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED.equals(eventType))
        {
            broadcastMemberJoined(evt.getChatRoom(), evt.getChatRoomMember());
        }
        else if(
            ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED.equals(eventType)
            || ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT.equals(eventType)
            || ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT.equals(eventType) )
        {
            broadcastMemberLeft(evt.getChatRoom(), evt.getChatRoomMember());
        }
        else
        {
            logger.warn("Unsupported event type: " + eventType);
        }
    }

    private void broadcastMemberJoined(ChatRoom       chatRoom,
                                       ChatRoomMember chatRoomMember)
    {
        List<MockMultiUserChat> listeners;
        synchronized(groupedChats)
        {
            listeners = new ArrayList<>(groupedChats);
        }

        for (MockMultiUserChat chatToNotify : listeners)
        {
            if (chatToNotify != chatRoom)
            {
                chatToNotify.removeMemberPresenceListener(this);

                chatToNotify.mockJoin((MockRoomMember) chatRoomMember);

                chatToNotify.addMemberPresenceListener(this);
            }
        }
    }

    private void broadcastMemberLeft(ChatRoom       chatRoom,
                                     ChatRoomMember chatRoomMember)
    {
        List<MockMultiUserChat> listeners;
        synchronized(groupedChats)
        {
            listeners = new ArrayList<>(groupedChats);
        }

        for (MockMultiUserChat chatToNotify : listeners)
        {
            if (chatToNotify != chatRoom)
            {
                chatToNotify.mockLeave(chatRoomMember.getName());
            }
        }
    }
}
