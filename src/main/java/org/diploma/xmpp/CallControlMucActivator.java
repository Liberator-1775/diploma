package org.diploma.xmpp;

import static org.jivesoftware.smack.packet.StanzaError.Condition.internal_server_error;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.plugin.reconnectplugin.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.osgi.*;
import org.diploma.*;
import org.diploma.stats.Statistics;
import org.diploma.util.RegisterThread;
import org.diploma.util.Util;
import org.diploma.*;
import org.diploma.stats.*;
import org.diploma.util.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.bosh.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.parts.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class CallControlMucActivator
    extends DependentActivator
    implements ServiceListener,
               RegistrationStateChangeListener,
        GatewaySessionListener,
               GatewayListener
{
    private final static Logger logger = Logger.getLogger(CallControlMucActivator.class);

    private static BundleContext osgiContext;

    public static final String ROOM_NAME_ACCOUNT_PROP = "BREWERY";

    private CallControl callControl = null;

    private ConfigurationService configService;
    
    private static ExecutorService threadPool = Util.createNewThreadPool("jigasi-callcontrol");

    public CallControlMucActivator()
    {
        super(ConfigurationService.class);
    }
    
    @Override
    public void startWithServices(final BundleContext bundleContext)
    {
        osgiContext = bundleContext;
        configService = getService(ConfigurationService.class);

        osgiContext.addServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                osgiContext,
                ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            initializeNewProvider(osgiContext.getService(ref));
        }

        SipGateway sipGateway = ServiceUtils.getService(bundleContext, SipGateway.class);

        TranscriptionGateway transcriptionGateway = ServiceUtils.getService(bundleContext, TranscriptionGateway.class);

        this.callControl = new CallControl(configService);

        if (sipGateway != null)
        {
            sipGateway.addGatewayListener(this);
            this.callControl.setSipGateway(sipGateway);
        }
        if (transcriptionGateway != null)
        {
            transcriptionGateway.addGatewayListener(this);
            this.callControl.setTranscriptionGateway(transcriptionGateway);
        }
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        osgiContext.removeServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(osgiContext, ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            ProtocolProviderService pps = osgiContext.getService(ref);
            if (ProtocolNames.JABBER.equals(pps.getProtocolName()))
            {
                try
                {
                    pps.unregister();
                }
                catch(OperationFailedException e)
                {
                    logger.error("Cannot unregister xmpp provider", e);
                }
            }
        }
    }

    public static long getMucJoinWaitTimeout()
    {
        return JigasiBundleActivator.getConfigurationService()
                                    .getLong(JigasiBundleActivator.P_NAME_MUC_JOIN_TIMEOUT,
                                             JigasiBundleActivator.MUC_JOIN_TIMEOUT_DEFAULT_VALUE);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference<?> ref = serviceEvent.getServiceReference();

        Object service = osgiContext.getService(ref);

        if (service instanceof ProtocolProviderService)
        {
            initializeNewProvider((ProtocolProviderService) service);
        }
        else if (service instanceof SipGateway)
        {
            SipGateway gateway = (SipGateway) service;
            gateway.addGatewayListener(this);

            if (this.callControl == null)
            {
                this.callControl = new CallControl(gateway, configService);
            }
            else
            {
                this.callControl.setSipGateway(gateway);
            }
        }
        else if (service instanceof TranscriptionGateway)
        {
            TranscriptionGateway gateway = (TranscriptionGateway) service;
            gateway.addGatewayListener(this);

            if (this.callControl == null)
            {
                this.callControl = new CallControl(gateway, configService);
            }
            else
            {
                this.callControl.setTranscriptionGateway(gateway);
            }
        }
    }

    private void initializeNewProvider(ProtocolProviderService pps)
    {
        if (!ProtocolNames.JABBER.equals(pps.getProtocolName())
            || pps.getAccountID().getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP) == null)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Drop provider - not xmpp or no room name account property:" + pps);
            }

            return;
        }

        pps.addRegistrationStateChangeListener(this);

        if (logger.isDebugEnabled())
        {
            logger.debug("Will register new control muc provider:" + describeProvider(pps));
        }

        new RegisterThread(pps, pps.getAccountID().getPassword()).start();
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        ProtocolProviderService provider = evt.getProvider();

        if (logger.isDebugEnabled()
            && provider instanceof ProtocolProviderServiceJabberImpl
            && provider.getAccountID().getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP) != null)
        {
            logger.debug("Got control muc provider " + describeProvider(provider)
                + " new state -> " + evt.getNewState(), new Exception());
        }

        if (evt.getNewState() == RegistrationState.REGISTERED)
        {
            joinCommonRoom(provider);
        }
        else if (evt.getNewState() == RegistrationState.UNREGISTERING)
        {
            leaveCommonRoom(provider);
        }
    }

    private void joinCommonRoom(ProtocolProviderService pps)
    {
        String roomName = pps.getAccountID().getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP);

        try
        {
            logger.info("Joining call control room: " + roomName + " pps:" + describeProvider(pps));
            Resourcepart connectionResource = null;

            if (pps instanceof ProtocolProviderServiceJabberImpl)
            {
                XMPPConnection conn = ((ProtocolProviderServiceJabberImpl) pps).getConnection();
                conn.registerIQRequestHandler(new DialIqHandler(pps));
                conn.registerIQRequestHandler(new HangUpIqHandler(pps));

                connectionResource = conn.getUser().getResourceOrNull();
            }

            OperationSetMultiUserChat muc = pps.getOperationSet(OperationSetMultiUserChat.class);

            ChatRoom mucRoom = muc.findRoom(roomName);
            if (connectionResource != null)
            {
                mucRoom.joinAs(connectionResource.toString());
            }
            else
            {
                mucRoom.join();
            }

            XMPPConnection connection;
            Object boshSessionId = null;
            if (pps instanceof ProtocolProviderServiceJabberImpl &&
                (connection = ((ProtocolProviderServiceJabberImpl) pps).getConnection()) != null
                && connection instanceof XMPPBOSHConnection)
            {
                boshSessionId = Util.getConnSessionId(connection);
            }
            logger.info("Joined call control room: " + roomName + " pps:" + describeProvider(pps)
                + " nickname:" + mucRoom.getUserNickname() + " sessionId:" + boshSessionId);

            Statistics.updatePresenceStatusForXmppProviders(Collections.singletonList(pps));
        }
        catch (Exception e)
        {
            logger.error(e, e);
        }
    }

    private void leaveCommonRoom(ProtocolProviderService pps)
    {
        String roomName = pps.getAccountID().getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP);

        if (roomName == null)
        {
            return;
        }

        try
        {
            logger.info("Leaving call control room: " + roomName + " pps:" + describeProvider(pps));

            OperationSetMultiUserChat muc = pps.getOperationSet(OperationSetMultiUserChat.class);

            ChatRoom mucRoom = muc.findRoom(roomName);
            if (mucRoom != null)
            {
                mucRoom.leave();
            }
        }
        catch (Exception e)
        {
            logger.error(e, e);
        }
    }

    @Override
    public void onJvbRoomJoined(AbstractGatewaySession source)
    {
        updatePresenceStatusForXmppProviders();
    }

    @Override
    public void onLobbyWaitReview(ChatRoom lobbyRoom)
    {}

    @Override
    public void onSessionAdded(AbstractGatewaySession session)
    {
        session.addListener(this);

        if (session.isInTheRoom())
        {
            updatePresenceStatusForXmppProviders();
        }
    }

    @Override
    public void onSessionRemoved(AbstractGatewaySession session)
    {
        updatePresenceStatusForXmppProviders();
        session.removeListener(this);
    }

    private void updatePresenceStatusForXmppProviders()
    {
        Statistics.updatePresenceStatusForXmppProviders();
    }

    public synchronized static void addCallControlMucAccount(
            String id,
            Map<String, String> properties)
        throws OperationFailedException
    {
        ConfigurationService config = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager = ProtocolProviderActivator.getAccountManager();

        String propPrefix = accountManager.getFactoryImplPackageName(xmppProviderFactory);
        String accountConfigPrefix = propPrefix + "." + id;
        if (listCallControlMucAccounts().contains(id))
        {
            String storedAccountUid
                = config.getString(accountConfigPrefix + "." + ProtocolProviderFactory.ACCOUNT_UID);

            if (storedAccountUid.equals(properties.get(ProtocolProviderFactory.ACCOUNT_UID)))
            {
                logger.warn("Account already exists id:" + id);
                return;
            }
            else
            {
                removeCallControlMucAccount(id);
            }
        }

        AccountID xmppAccount = xmppProviderFactory.createAccount(properties);

        {
            config.setProperty(accountConfigPrefix, id);
            config.setProperty(accountConfigPrefix + "." + ProtocolProviderFactory.ACCOUNT_UID,
                xmppAccount.getAccountUniqueID());
        }

        config.setProperty(
            ReconnectPluginActivator.ATLEAST_ONE_CONNECTION_PROP + "." + xmppAccount.getAccountUniqueID(),
            Boolean.TRUE.toString());

        accountManager.loadAccount(xmppAccount);

        logger.info("Added new control muc account:" + id + " -> " + xmppAccount);
    }

    public synchronized static boolean removeCallControlMucAccount(String id)
    {
        ConfigurationService config = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager = ProtocolProviderActivator.getAccountManager();

        String accountIDStr = config.getString(accountManager.getFactoryImplPackageName(xmppProviderFactory)
            + "." + id + "." + ProtocolProviderFactory.ACCOUNT_UID);

        AccountID accountID = accountManager.getStoredAccounts().stream()
            .filter(a -> a.getAccountUniqueID().equals(accountIDStr))
            .findFirst().orElse(null);

        if (accountID != null)
        {
            logger.info("Removing muc control account: " + id + ", " + accountID);
            boolean result = xmppProviderFactory.uninstallAccount(accountID);
            logger.info("Removed muc control account: " + id + ", " + accountID + ", successful:" + result);

            config.removeProperty(
                ReconnectPluginActivator.ATLEAST_ONE_CONNECTION_PROP + "." + accountID.getAccountUniqueID());

            return result;
        }
        else
        {
            logger.warn("No muc control account found for removing id: " + id);
            return false;
        }
    }

    public synchronized static List<String> listCallControlMucAccounts()
    {
        ConfigurationService config = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager = ProtocolProviderActivator.getAccountManager();

        String propPrefix = accountManager.getFactoryImplPackageName(xmppProviderFactory);

        return
            config.getPropertyNamesByPrefix(propPrefix, false)
            .stream()
            .filter(p -> p.endsWith(ROOM_NAME_ACCOUNT_PROP))
            .map(p -> p.substring(
                propPrefix.length() + 1, // the prefix and '.'
                p.indexOf(ROOM_NAME_ACCOUNT_PROP) - 1)) // property and the '.'
            .collect(Collectors.toList());
    }

    private static String describeProvider(ProtocolProviderService provider)
    {
        AccountID acc = provider.getAccountID();
        ConfigurationService config = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager = ProtocolProviderActivator.getAccountManager();

        String propPrefix = accountManager.getFactoryImplPackageName(xmppProviderFactory);

        String id = config.getPropertyNamesByPrefix(propPrefix, false)
            .stream()
            .filter(p -> p.endsWith(ProtocolProviderFactory.ACCOUNT_UID))
            .filter(p -> config.getString(p).equals(acc.getAccountUniqueID()))
            .map(p -> p.substring(
                propPrefix.length() + 1,
                p.indexOf(ProtocolProviderFactory.ACCOUNT_UID) - 1))
            .findFirst().orElse(null);

        return provider + ", id:" + id;
    }

    private class DialIqHandler
        extends RayoIqHandler<DialIq>
    {
        DialIqHandler(ProtocolProviderService pps)
        {
            super(DialIq.ELEMENT, IQ.Type.set, pps);
        }

        @Override
        public IQ processIQ(DialIq packet, CallContext ctx)
        {
            try
            {
                threadPool.execute(
                    () -> {
                        IQ result = processIQInternal(packet, ctx);
                        if (result != null)
                        {
                            try
                            {
                                XMPPConnection conn = ((ProtocolProviderServiceJabberImpl) pps).getConnection();
                                conn.sendStanza(result);
                            }
                            catch(SmackException.NotConnectedException | InterruptedException e)
                            {
                                logger.error(ctx + " Cannot send reply for dialIQ:" + packet.toXML());
                            }
                        }
                    });
            }
            catch (RejectedExecutionException e)
            {
                logger.error(ctx + " Failed to handle incoming dialIQ:" + packet.toXML());

                return IQ.createErrorResponse(packet, StanzaError.getBuilder()
                    .setCondition(internal_server_error)
                    .setConditionText(e.getMessage())
                    .build());
            }

            return null;
        }

        private IQ processIQInternal(DialIq packet, CallContext ctx)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(ctx + " Processing a RayoIq: " + packet.toXML());
            }

            try
            {
                AbstractGatewaySession[] session = { null };
                RefIq resultIQ = callControl.handleDialIq(packet, ctx, session);

                if (session[0] != null)
                    setDialResponseAndRegisterHangUpHandler(resultIQ,
                        session[0]);

                return resultIQ;
            }
            catch (CallControlAuthorizationException ccae)
            {
                return ccae.getErrorIq();
            }
            catch (Exception e)
            {
                logger.error(ctx + " Error processing RayoIq", e);
                return IQ.createErrorResponse(packet, StanzaError.from(
                    StanzaError.Condition.internal_server_error, e.getMessage()).build());
            }
        }

        private void setDialResponseAndRegisterHangUpHandler(
            RefIq response, final AbstractGatewaySession session)
            throws Exception
        {
            WaitToJoinRoom waiter = new WaitToJoinRoom();
            try
            {
                session.addListener(waiter);
                if (!session.isInTheRoom())
                {
                    waiter.waitToJoinRoom();
                }
            }
            finally
            {
                session.removeListener(waiter);
            }

            ChatRoom room = session.getJvbChatRoom();
            if (room == null)
            {
                room = waiter.lobbyRoom;
            }

            response.setUri("xmpp:" + room.getIdentifier() + "/" + room.getUserNickname());

            final XMPPConnection roomConnection = ((ProtocolProviderServiceJabberImpl) room.getParentProvider())
                .getConnection();
            roomConnection.registerIQRequestHandler(new HangUpIqHandler(room.getParentProvider()));
        }
    }

    private class WaitToJoinRoom
        implements GatewaySessionListener
    {
        ChatRoom lobbyRoom = null;

        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        @Override
        public void onJvbRoomJoined(AbstractGatewaySession source)
        {
            countDownLatch.countDown();
        }

        @Override
        public void onLobbyWaitReview(ChatRoom lobbyRoom)
        {
            this.lobbyRoom = lobbyRoom;

            countDownLatch.countDown();
        }

        public void waitToJoinRoom()
            throws Exception
        {
            try
            {
                if (!countDownLatch.await(getMucJoinWaitTimeout(), TimeUnit.SECONDS))
                {
                    throw new Exception("Fail to join muc!");
                }
            }
            catch (InterruptedException e)
            {
                logger.error(e);
            }
        }
    }

    private class HangUpIqHandler
        extends RayoIqHandler<HangUp>
    {
        HangUpIqHandler(ProtocolProviderService pps)
        {
            super(HangUp.ELEMENT, IQ.Type.set, pps);
        }

        @Override
        public IQ processIQ(HangUp iqRequest, CallContext ctx)
        {
            final AbstractGatewaySession session = callControl.getSession(ctx.getCallResource());
            session.hangUp();
            return IQ.createResultIQ(iqRequest);
        }
    }

    private static abstract class RayoIqHandler<T extends RayoIq>
        extends AbstractIqRequestHandler
    {
        protected ProtocolProviderService pps;

        RayoIqHandler(String element, IQ.Type type, ProtocolProviderService pps)
        {
            super(element, RayoIqProvider.NAMESPACE, type, Mode.sync);
            this.pps = pps;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final IQ handleIQRequest(IQ iqRequest)
        {
            AccountID acc = pps.getAccountID();

            final CallContext ctx = new CallContext(pps);
            ctx.setDomain(acc.getAccountPropertyString(CallContext.DOMAIN_BASE_ACCOUNT_PROP));
            ctx.setBoshURL(acc.getAccountPropertyString(CallContext.BOSH_URL_ACCOUNT_PROP));
            ctx.setMucAddressPrefix(acc.getAccountPropertyString(CallContext.MUC_DOMAIN_PREFIX_PROP, null));

            return processIQ((T)iqRequest, ctx);
        }

        protected abstract IQ processIQ(T iq, CallContext ctx);
    }
}
