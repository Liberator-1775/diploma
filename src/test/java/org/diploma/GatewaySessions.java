package org.diploma;

import java.util.*;

public class GatewaySessions
    implements GatewayListener
{
    private final Object syncRoot = new Object();

    private List<SipGatewaySession> sessions = null;

    private final SipGateway gateway;

    public GatewaySessions(SipGateway gateway)
    {
        this.gateway = gateway;

        this.gateway.addGatewayListener(this);
    }

    @Override
    public void onSessionAdded(AbstractGatewaySession session)
    {
        sessions = gateway.getActiveSessions();

        synchronized (syncRoot)
        {
            syncRoot.notifyAll();
        }
    }

    public List<SipGatewaySession> getSessions(long timeout)
        throws InterruptedException
    {
        if (sessions == null)
        {
            synchronized (syncRoot)
            {
                syncRoot.wait(timeout);
            }
        }

        this.gateway.removeGatewayListener(this);

        return sessions;
    }
}
