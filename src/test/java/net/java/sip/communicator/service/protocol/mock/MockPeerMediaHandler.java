package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.service.neomedia.event.*;

public class MockPeerMediaHandler
    extends CallPeerMediaHandler<MockCallPeer>
{
    public MockPeerMediaHandler(
        MockCallPeer peer,
        SrtpListener srtpListener)
    {
        super(peer, srtpListener);
    }

    @Override
    protected TransportManager<MockCallPeer> getTransportManager()
    {
        return null;
    }

    @Override
    protected TransportManager<MockCallPeer> queryTransportManager()
    {
        return null;
    }

    @Override
    protected void throwOperationFailedException(String message, int errorCode,
                                                 Throwable cause)
        throws OperationFailedException
    {
        throw new OperationFailedException(message, errorCode, cause);
    }
}
