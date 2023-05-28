package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;

public class MockCallPeer
    extends MediaAwareCallPeer<MockCall, MockPeerMediaHandler,
                               MockProtocolProvider>
{
    private final static Logger logger = Logger.getLogger(MockCallPeer.class);

    private final String address;

    private final MockCall call;

    public MockCallPeer(String address, MockCall call)
    {
        super(call);

        this.address = address;
        this.call = call;

        setMediaHandler(new MockPeerMediaHandler(this, null));
    }

    @Override
    public void addConferenceMembersSoundLevelListener(
        ConferenceMembersSoundLevelListener listener)
    {

    }

    @Override
    public void addStreamSoundLevelListener(SoundLevelListener listener)
    {

    }

    @Override
    public String getAddress()
    {
        return address;
    }

    @Override
    public MockCall getCall()
    {
        return call;
    }

    @Override
    public Contact getContact()
    {
        return null;
    }

    @Override
    public String getDisplayName()
    {
        return null;
    }

    @Override
    public byte[] getImage()
    {
        return new byte[0];
    }

    @Override
    public String getPeerID()
    {
        return null;
    }

    @Override
    public String getURI()
    {
        return null;
    }

    @Override
    public void removeConferenceMembersSoundLevelListener(
        ConferenceMembersSoundLevelListener listener)
    {

    }

    @Override
    public void removeStreamSoundLevelListener(SoundLevelListener listener)
    {

    }

    private CallPeerState lastState;

    @Override
    public void setState(CallPeerState newState, String reason, int reasonCode)
    {
        this.lastState = getState();

        super.setState(newState, reason, reasonCode);
    }

    @Override
    public String getEntity()
    {
        throw new UnsupportedOperationException("getEntity");
    }

    @Override
    public MediaDirection getDirection(MediaType mediaType)
    {
        throw new UnsupportedOperationException("getDirection");
    }

    public void putOnHold()
    {
        try
        {
            getMediaHandler().setLocallyOnHold(true);
            setState(CallPeerState.ON_HOLD_LOCALLY);
            logger.info(this + " is now on hold, last state: " + lastState);
        }
        catch (OperationFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void putOffHold()
    {
        try
        {
            getMediaHandler().setLocallyOnHold(false);
            setState(lastState);
            logger.info(this + " is now off hold, switch to: " + lastState);
        }
        catch (OperationFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

}
