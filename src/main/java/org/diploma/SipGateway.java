package org.diploma;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import org.diploma.util.RegisterThread;
import org.diploma.util.*;
import org.jitsi.utils.logging.Logger;
import org.osgi.framework.*;

public class SipGateway
    extends AbstractGateway<SipGatewaySession>
    implements RegistrationStateChangeListener
{
    public static final String CALL_DIRECTION_HEADER = "JigasiCallDirection";

    public static final String CALL_DIRECTION_OUTGOING = "out";

    public static final String CALL_DIRECTION_INCOMING = "in";

    private final static Logger logger = Logger.getLogger(SipGateway.class);

    private ProtocolProviderService sipProvider;

    private final Object syncRoot = new Object();

    private final SipCallListener callListener = new SipCallListener();

    public SipGateway(BundleContext bundleContext)
    {
        super(bundleContext);
    }

    public void stop()
    {
        if (this.sipProvider == null)
            throw new IllegalStateException("SIP provider not present");

        try
        {
            this.sipProvider.unregister();
        }
        catch(OperationFailedException e)
        {
            logger.error("Cannot unregister");
        }
    }

    public void setSipProvider(ProtocolProviderService sipProvider)
    {
        if (this.sipProvider != null)
            throw new IllegalStateException("SIP provider already set");

        this.sipProvider = sipProvider;

        initProvider(sipProvider);

        new RegisterThread(sipProvider).start();
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        ProtocolProviderService pps = evt.getProvider();

        logger.info("REG STATE CHANGE " + pps + " -> " + evt);

        if (evt.getNewState().equals(RegistrationState.REGISTERED))
        {
            fireGatewayReady();
        }
    }

    public ProtocolProviderService getSipProvider()
    {
        return sipProvider;
    }

    public String getSipAccountProperty(String propertyName)
    {
        if (sipProvider == null)
        {
            return null;
        }

        return this.sipProvider.getAccountID()
            .getAccountPropertyString(propertyName);
    }


    private void initProvider(ProtocolProviderService pps)
    {
        pps.addRegistrationStateChangeListener(this);

        OperationSetBasicTelephony telephony = pps.getOperationSet(
            OperationSetBasicTelephony.class);

        telephony.addCallListener(callListener);
    }

    public boolean isReady()
    {
        return this.sipProvider != null
            && this.sipProvider.getRegistrationState()
                .equals(RegistrationState.REGISTERED);
    }

    public SipGatewaySession createOutgoingCall(CallContext ctx)
    {
        ctx.addExtraHeader(CALL_DIRECTION_HEADER, CALL_DIRECTION_OUTGOING);

        SipGatewaySession outgoingSession = new SipGatewaySession(this, ctx);
        outgoingSession.addListener(this);
        outgoingSession.createOutgoingCall();

        return outgoingSession;
    }

    class SipCallListener
        implements CallListener
    {
        @Override
        public void incomingCallReceived(CallEvent event)
        {
            synchronized (syncRoot)
            {

                Call call = event.getSourceCall();

                CallContext ctx = new CallContext(call.getProtocolProvider());

                ctx.addExtraHeader(CALL_DIRECTION_HEADER, CALL_DIRECTION_INCOMING);

                ctx.setDomain(sipProvider.getAccountID()
                    .getAccountPropertyString(
                        CallContext.DOMAIN_BASE_ACCOUNT_PROP));
                call.setData(CallContext.class, ctx);

                logger.info(ctx + " Incoming call received...");

                SipGatewaySession incomingSession
                    = new SipGatewaySession(
                            SipGateway.this, ctx, call);
                incomingSession.addListener(SipGateway.this);

                incomingSession.initIncomingCall();
            }
        }

        @Override
        public void outgoingCallCreated(CallEvent event) { }

        @Override
        public void callEnded(CallEvent event)
        {

        }
    }

}
