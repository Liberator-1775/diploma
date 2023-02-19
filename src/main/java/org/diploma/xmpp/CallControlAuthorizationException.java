package org.diploma.xmpp;

import org.jivesoftware.smack.packet.*;

import java.util.*;

import static org.jivesoftware.smack.packet.StanzaError.Condition.forbidden;

public class CallControlAuthorizationException
    extends Exception
{
    private IQ iq;

    public CallControlAuthorizationException(IQ iq)
    {
        Objects.requireNonNull(iq);
        this.iq = iq;
    }

    public IQ getIq()
    {
        return iq;
    }

    public IQ getErrorIq()
    {
        return IQ.createErrorResponse(iq, forbidden);
    }
}
