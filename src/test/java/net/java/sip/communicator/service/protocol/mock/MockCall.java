package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;

public class MockCall
    extends MediaAwareCall<MockCallPeer,
                           MockBasicTeleOpSet,
                           MockProtocolProvider>
{
    protected MockCall(MockBasicTeleOpSet telephony)
    {
        super(telephony);
    }

    @Override
    public boolean isConferenceFocus()
    {
        return false;
    }

    @Override
    public void addLocalUserSoundLevelListener(SoundLevelListener l)
    {

    }

    @Override
    public void removeLocalUserSoundLevelListener(SoundLevelListener l)
    {

    }

    public void addCallPeer(MockCallPeer peer)
    {
        doAddCallPeer(peer);
    }

    public synchronized void hangup()
    {
        setCallState(CallState.CALL_ENDED);

        for (MockCallPeer peer : getCallPeerList())
        {
            peer.setState(CallPeerState.DISCONNECTED);
        }
    }

    @Override
    public String toString()
    {
        return super.toString() + " " + getProtocolProvider().getProtocolName();
    }

    public void setCallState(CallState newState)
    {
        super.setCallState(newState);
    }
}
