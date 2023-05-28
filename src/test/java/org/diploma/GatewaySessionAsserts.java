package org.diploma;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.java.sip.communicator.service.protocol.*;

public class GatewaySessionAsserts
    implements GatewaySessionListener
{
    @Override
    public void onJvbRoomJoined(AbstractGatewaySession source)
    {
        synchronized (this)
        {
            this.notifyAll();
        }
    }

    @Override
    public void onLobbyWaitReview(ChatRoom lobbyRoom)
    {}

    public void assertJvbRoomJoined(AbstractGatewaySession session, long timeout)
        throws InterruptedException
    {
        synchronized (this)
        {
            session.addListener(this);

            ChatRoom room = session.getJvbChatRoom();

            if (room == null || !room.isJoined())
            {
                this.wait(timeout);
            }

            session.removeListener(this);

            assertTrue(session.getJvbChatRoom().isJoined());
        }
    }
}
