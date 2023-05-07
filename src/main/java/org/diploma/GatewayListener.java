package org.diploma;

public interface GatewayListener
{
    default void onSessionAdded(AbstractGatewaySession session)
    {}

    default void onSessionRemoved(AbstractGatewaySession session)
    {}

    default void onSessionFailed(AbstractGatewaySession session)
    {}

    default void onReady()
    {}
}
