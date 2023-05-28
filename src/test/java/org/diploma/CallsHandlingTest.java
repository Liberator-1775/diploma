package org.diploma;

import static org.junit.jupiter.api.Assertions.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.util.osgi.ServiceUtils;
import org.diploma.xmpp.CallControl;
import org.diploma.xmpp.*;
import org.jitsi.service.configuration.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.osgi.framework.launch.*;

public class CallsHandlingTest
{
    private static OSGiHandler osgi;

    private MockProtocolProvider sipProvider;

    private static int roomNameCounter = 1;

    private String roomName;

    private MockJvbConferenceFocus focus;

    @BeforeAll
    public static void setUpClass()
        throws InterruptedException, BundleException
    {
        osgi = new OSGiHandler();
        var fw = osgi.init();
        var start = System.nanoTime();
        Thread.sleep(5000);
        while (fw.getState() != Framework.ACTIVE)
        {
            if (System.nanoTime() - start > TimeUnit.SECONDS.toNanos(5))
            {
                throw new BundleException("Failed to start framework");
            }
        }
    }

    @BeforeEach
    public void setUp() throws InvalidSyntaxException
    {
        sipProvider = osgi.getSipProvider();

        this.roomName = getTestRoomName() + "@conference.net";

        this.focus = new MockJvbConferenceFocus(roomName);
    }

    @AfterEach
    public void tearDown()
        throws InterruptedException, TimeoutException
    {
        focus.tearDown();

        CallManager.restartPool();

        BundleContext ctx = JigasiBundleActivator.osgiContext;

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                ctx, ProtocolProviderService.class);

        for (ServiceReference<?> ref : refs)
        {
            ProtocolProviderService protoService
                = (ProtocolProviderService) ctx.getService(ref);

            if (ProtocolNames.JABBER.equals(
                    protoService.getProtocolName()))
            {
                throw new RuntimeException(
                    protoService + " is still registered");
            }
        }
    }

    private String getTestRoomName()
    {
        return "test" + roomNameCounter++;
    }

    public void testIncomingSipCall()
        throws Exception
    {
        focus.setup();

        MockCall sipCall
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee", roomName);

        CallStateListener callStateWatch = new CallStateListener();

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 1000);

        SipGatewaySession session
            = osgi.getSipGateway().getActiveSessions().get(0);

        Call jvbCall = session.getJvbCall();
        ChatRoom jvbConfRoom = session.getJvbChatRoom();

        assertNotNull(jvbCall);
        assertNotNull(jvbCall);

        CallManager.hangupCall(sipCall);

        callStateWatch.waitForState(
            session.getJvbCall(), CallState.CALL_ENDED, 1000);
        assertFalse(jvbConfRoom.isJoined());
    }

    public void testOutgoingSipCall()
        throws
        InterruptedException,
        OperationFailedException,
        OperationNotSupportedException,
        XmppStringprepException,
        InvalidSyntaxException
    {
        String destination = "sip-destination";

        SipGateway sipGw = osgi.getSipGateway();

        focus.setup();

        CallStateListener callStateWatch = new CallStateListener();

        OperationSetBasicTelephony<?> sipTele
            = osgi.getSipProvider()
                    .getOperationSet(OperationSetBasicTelephony.class);

        OutCallListener outCallWatch = new OutCallListener();

        outCallWatch.bind(sipTele);

        CallContext ctx = new CallContext(osgi.getSipProvider());
        ctx.setDestination(destination);
        ctx.setRoomName(roomName);
        ctx.setCustomCallResource(
            JidCreate.from("callResourceUri" + roomName + "@conference.net"));

        SipGatewaySession session = sipGw.createOutgoingCall(ctx);
        assertNotNull(session);

        Call sipCall = outCallWatch.getOutgoingCall(1000);

        assertNotNull(sipCall);

        CallManager.acceptCall(sipCall);

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 1000);

        GatewaySessionAsserts sessionWatch = new GatewaySessionAsserts();
        sessionWatch.assertJvbRoomJoined(session, 2000);

        ChatRoom chatRoom = session.getJvbChatRoom();
        Call jvbCall = session.getJvbCall();

        assertNotNull(chatRoom);
        assertTrue(chatRoom.isJoined());
        callStateWatch.waitForState(jvbCall, CallState.CALL_IN_PROGRESS, 1000);
        assertEquals(CallState.CALL_IN_PROGRESS, jvbCall.getCallState());

        CallManager.hangupCall(sipCall);

        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(jvbCall, CallState.CALL_ENDED, 1000);
        assertFalse(chatRoom.isJoined());
    }

    @Test
    public void testMultipleTime()
        throws Exception
    {
        testIncomingSipCall();
        tearDown();

        setUp();
        testOutgoingSipCall();
        tearDown();

        setUp();
        testIncomingSipCall();
        tearDown();

        setUp();
        testIncomingSipCall();
        tearDown();

        setUp();
        testOutgoingSipCall();
        tearDown();

        setUp();
        testOutgoingSipCall();
    }

    @Test
    public void testFocusLeftTheRoomWithNoResume()
        throws OperationFailedException,
               OperationNotSupportedException, InterruptedException
    {
        long origValue = AbstractGateway.getJvbInviteTimeout();
        AbstractGateway.setJvbInviteTimeout(-1);

        focus.setup();

        focus.setLeaveRoomAfterInvite(true);

        CallStateListener callStateWatch = new CallStateListener();

        MockCall sipCall = sipProvider.getTelephony().mockIncomingGatewayCall("calee", roomName);

        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 2000);

        assertEquals(CallState.CALL_ENDED, focus.getCall().getCallState());
        assertNull(focus.getChatRoom());

        AbstractGateway.setJvbInviteTimeout(origValue);
    }

    @Test
    public void testFocusLeftTheRoomWithResume()
        throws OperationFailedException,
               OperationNotSupportedException, InterruptedException
    {
        long origValue = AbstractGateway.getJvbInviteTimeout();
        AbstractGateway.setJvbInviteTimeout(AbstractGateway.DEFAULT_JVB_INVITE_TIMEOUT);

        focus.setup();

        focus.setLeaveRoomAfterInvite(true);

        CallStateListener callStateWatch = new CallStateListener();

        MockCall sipCall = sipProvider.getTelephony().mockIncomingGatewayCall("calee", roomName);

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 2000);

        callStateWatch.waitForState(focus.getCall(), CallState.CALL_ENDED, 2000);
        assertNull(focus.getChatRoom());

        AbstractGateway.setJvbInviteTimeout(origValue);

        CallManager.hangupCall(sipCall);
    }

    @Test
    public void testCallControl()
        throws Exception
    {
        String serverName = "conference.net";

        CallControl callControl = new CallControl(JigasiBundleActivator.getConfigurationService());
        callControl.setSipGateway(osgi.getSipGateway());

        Jid from = JidCreate.from("from@example.org");
        Jid to = JidCreate.from("sipAddress@example.com");

        focus.setup();

        OutCallListener outCallWatch = new OutCallListener();
        outCallWatch.bind(sipProvider.getTelephony());

        DialIq dialIq
            = DialIq.create(to.toString(), from.toString());

        dialIq.setFrom(from);

        dialIq.setHeader(
            CallControl.ROOM_NAME_HEADER,
            focus.getRoomName());

        CallContext ctx = new CallContext(this);
        ctx.setDomain(serverName);

        org.jivesoftware.smack.packet.IQ result = callControl.handleDialIq(dialIq, ctx, null);

        assertNotNull(result);

        RefIq callRef = (RefIq) result;

        String callUri = callRef.getUri();
        assertEquals("xmpp:", callUri.substring(0, 5));

        GatewaySessions gatewaySessions = new GatewaySessions(osgi.getSipGateway());

        Call sipCall = outCallWatch.getOutgoingCall(1000);

        assertNotNull(sipCall);

        CallManager.acceptCall(sipCall);

        CallStateListener callStateWatch = new CallStateListener();

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 1000);

        Jid callResource = JidCreate.from(callUri.substring(5)); //remove xmpp:

        List<SipGatewaySession> sessions = gatewaySessions.getSessions(2000);
        assertNotNull(sessions);
        assertEquals(1, sessions.size());

        SipGatewaySession session = sessions.get(0);

        Call xmppCall = session.getJvbCall();

        callStateWatch.waitForState(xmppCall, CallState.CALL_IN_PROGRESS, 1000);

        ChatRoom conferenceChatRoom = session.getJvbChatRoom();

        HangUp hangUp
            = HangUp.create(
                    from, callResource);

        callControl.handleHangUp(hangUp);

        callStateWatch.waitForState(xmppCall, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 1000);
        assertFalse(conferenceChatRoom.isJoined());
    }

    @Test
    public void testDefaultJVbRoomProperty()
        throws Exception
    {
        focus.setup();

        CallStateListener callStateWatch = new CallStateListener();

        ConfigurationService config
            = JigasiBundleActivator.getConfigurationService();

        config.setProperty(
            SipGateway.P_NAME_DEFAULT_JVB_ROOM, roomName);

        MockCall sipCall
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee", null);

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 2000);

        SipGatewaySession session
            = osgi.getSipGateway().getActiveSessions().get(0);
        assertNotNull(session);

        Call xmppCall = session.getJvbCall();
        assertNotNull(xmppCall);

        ChatRoom jvbRoom = session.getJvbChatRoom();
        assertNotNull(jvbRoom);

        CallManager.hangupCall(sipCall);

        callStateWatch.waitForState(xmppCall, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 1000);
        assertFalse(jvbRoom.isJoined());
    }

    @Test
    public void testSimultaneousCalls()
        throws Exception
    {
        focus.setup();

        MockBasicTeleOpSet sipTele = sipProvider.getTelephony();

        MockCall sipCall1 = sipTele.mockIncomingGatewayCall("calee1", roomName);
        MockCall sipCall2 = sipTele.mockIncomingGatewayCall("calee2", roomName);
        MockCall sipCall3 = sipTele.mockIncomingGatewayCall("calee3", roomName);

        CallStateListener callStateWatch = new CallStateListener();

        callStateWatch.waitForState(sipCall1, CallState.CALL_IN_PROGRESS, 1000);
        callStateWatch.waitForState(sipCall2, CallState.CALL_IN_PROGRESS, 1000);
        callStateWatch.waitForState(sipCall3, CallState.CALL_IN_PROGRESS, 1000);

        CallPeerStateListener peerStateWatch = new CallPeerStateListener();
        peerStateWatch.waitForState(sipCall1, 0, CallPeerState.CONNECTED, 1000);
        peerStateWatch.waitForState(sipCall2, 0, CallPeerState.CONNECTED, 1000);
        peerStateWatch.waitForState(sipCall3, 0, CallPeerState.CONNECTED, 1000);

        SipGateway gateway = osgi.getSipGateway();
        List<SipGatewaySession> sessions = gateway.getActiveSessions();

        assertEquals(3, sessions.size());

        ChatRoom jvbRoom1 = sessions.get(0).getJvbChatRoom();
        ChatRoom jvbRoom2 = sessions.get(1).getJvbChatRoom();
        ChatRoom jvbRoom3 = sessions.get(2).getJvbChatRoom();

        assertTrue(jvbRoom1.isJoined());
        assertTrue(jvbRoom2.isJoined());
        assertTrue(jvbRoom3.isJoined());

        Call jvbCall1 = sessions.get(0).getJvbCall();
        Call jvbCall2 = sessions.get(1).getJvbCall();
        Call jvbCall3 = sessions.get(2).getJvbCall();

        CallManager.hangupCall(sipCall1);
        CallManager.hangupCall(sipCall2);
        CallManager.hangupCall(sipCall3);

        callStateWatch.waitForState(jvbCall1, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(jvbCall2, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(jvbCall3, CallState.CALL_ENDED, 1000);

        assertEquals(CallState.CALL_ENDED, sipCall1.getCallState());
        assertEquals(CallState.CALL_ENDED, sipCall2.getCallState());
        assertEquals(CallState.CALL_ENDED, sipCall3.getCallState());

        assertFalse(jvbRoom1.isJoined());
        assertFalse(jvbRoom2.isJoined());
        assertFalse(jvbRoom3.isJoined());
    }

    @Test
    public void testNoFocusInTheRoom()
        throws Exception
    {
        long jvbInviteTimeout = 200;
        AbstractGateway.setJvbInviteTimeout(jvbInviteTimeout);

        SipGateway gateway = osgi.getSipGateway();
        GatewaySessions gatewaySessions = new GatewaySessions(gateway);

        MockCall sipCall1
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee1", roomName);

        CallStateListener callStateWatch = new CallStateListener();

        callStateWatch.waitForState(
            sipCall1, CallState.CALL_INITIALIZATION, 1000);

        List<SipGatewaySession> sessions = gatewaySessions.getSessions(1000);
        assertEquals(1, sessions.size());

        SipGatewaySession session1 = sessions.get(0);

        GatewaySessionAsserts sessionWatch = new GatewaySessionAsserts();
        sessionWatch.assertJvbRoomJoined(session1, 1000);

        ChatRoom jvbRoom1 = session1.getJvbChatRoom();
        assertTrue(jvbRoom1.isJoined());
        assertEquals(1, jvbRoom1.getMembersCount());

        Call jvbCall1 = sessions.get(0).getJvbCall();
        assertNull(jvbCall1);

        callStateWatch.waitForState(
            sipCall1, CallState.CALL_ENDED, jvbInviteTimeout + 200);

        assertFalse(jvbRoom1.isJoined());

        AbstractGateway.setJvbInviteTimeout(
            AbstractGateway.DEFAULT_JVB_INVITE_TIMEOUT);
    }
}
