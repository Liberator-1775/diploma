package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;

import java.text.*;
import java.util.*;

public class MockBasicTeleOpSet
    extends AbstractOperationSetBasicTelephony<MockProtocolProvider>
{
    private final MockProtocolProvider protocolProvider;

    private List<MockCall> activeCalls = new ArrayList<MockCall>();


    public MockBasicTeleOpSet(MockProtocolProvider protocolProvider)
    {
        this.protocolProvider = protocolProvider;
    }

    @Override
    public synchronized Call createCall(String uri, CallConference conference)
        throws OperationFailedException, ParseException
    {
        MockCall outgoingCall = new MockCall(this);

        MockCallPeer peer = new MockCallPeer(uri, outgoingCall);

        outgoingCall.addCallPeer(peer);

        activeCalls.add(outgoingCall);

        fireCallEvent(CallEvent.CALL_INITIATED, outgoingCall);

        return outgoingCall;
    }

    @Override
    public void answerCallPeer(CallPeer peer)
        throws OperationFailedException
    {

        ((MockCallPeer) peer).setState(CallPeerState.CONNECTED);

        ((MockCall) peer.getCall()).setCallState(CallState.CALL_IN_PROGRESS);
    }

    @Override
    public void putOnHold(CallPeer peer)
        throws OperationFailedException
    {
        ((MockCallPeer) peer).putOnHold();
    }

    @Override
    public void putOffHold(CallPeer peer)
        throws OperationFailedException
    {
        ((MockCallPeer) peer).putOffHold();
    }

    @Override
    public void hangupCallPeer(CallPeer peer)
        throws OperationFailedException
    {
        ((MockCall)peer.getCall()).hangup();
    }

    @Override
    public void hangupCallPeer(CallPeer peer, int reasonCode, String reason)
        throws OperationFailedException
    {
        hangupCallPeer(peer);
    }

    @Override
    public synchronized Iterator<? extends Call> getActiveCalls()
    {
        return activeCalls.iterator();
    }

    @Override
    public MockProtocolProvider getProtocolProvider()
    {
        return protocolProvider;
    }

    public synchronized MockCall createIncomingCall(String calee)
    {
        MockCall incomingCall = new MockCall(this);

        MockCallPeer peer = new MockCallPeer(calee, incomingCall);

        incomingCall.addCallPeer(peer);

        activeCalls.add(incomingCall);

        fireCallEvent(CallEvent.CALL_RECEIVED, incomingCall);

        return incomingCall;
    }

    public MockCall mockIncomingGatewayCall(String uri, final String roomName)
    {
        final MockCall call = createIncomingCall(uri);

        if (!CallState.CALL_ENDED.equals(call.getCallState()))
        {
            getProtocolProvider()
                .getJitsiMeetTools()
                .notifyJoinJitsiMeetRoom(call, roomName);
        }

        return call;
    }
}
