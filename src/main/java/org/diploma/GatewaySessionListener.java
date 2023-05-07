package org.diploma;

import net.java.sip.communicator.service.protocol.*;

public interface GatewaySessionListener<T extends AbstractGatewaySession>
{
    void onJvbRoomJoined(T source);

    void onLobbyWaitReview(ChatRoom lobbyRoom);
}
