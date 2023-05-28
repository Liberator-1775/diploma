package org.diploma;

import java.util.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.util.osgi.*;
import org.diploma.osgi.JigasiBundleConfig;
import org.diploma.osgi.*;
import org.osgi.framework.*;
import org.osgi.framework.launch.*;

public class OSGiHandler
{
    private BundleContext bc;

    public Framework init()
        throws InterruptedException, BundleException
    {
        System.setProperty(
            "net.java.sip.communicator.impl.configuration.USE_PROPFILE_CONFIG",
            "true");

        System.setProperty(
            "net.java.sip.communicator.impl.protocol.SingleCallInProgressPolicy"
                + ".enabled",
            "false"
        );

        JigasiBundleConfig.setSystemPropertyDefaults();
        var fw = Main.start(List.of(
            MockSipActivator.class,
            MockProtocolProviderFactoriesActivator.class));
        bc = fw.getBundleContext();
        return fw;
    }

    public static class MockSipActivator
        extends DependentActivator
    {
        public MockSipActivator()
        {
            super(ProtocolProviderFactory.class);
        }

        @Override
        public void startWithServices(BundleContext context)
        {
            Map<String, String> accProps = new HashMap<>();
            accProps.put(CallContext.DOMAIN_BASE_ACCOUNT_PROP, "sipserver.net");
            MockAccountID mockSipAccount
                = new MockAccountID("sipuser@sipserver.net",
                accProps,
                ProtocolNames.SIP);

            var sipProvider = new MockProtocolProvider(mockSipAccount);
            sipProvider.includeBasicTeleOpSet();
            sipProvider.includeJitsiMeetToolsSip();

            var properties = new Hashtable<String, String>();
            properties.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.SIP);
            properties.put(ProtocolProviderFactory.USER_ID,
                "sipuser@sipserver.net");
            context.registerService(ProtocolProviderService.class, sipProvider,
                properties);
        }

        @Override
        public void stop(BundleContext context)
        {
        }
    }

    public MockProtocolProvider getSipProvider() throws InvalidSyntaxException
    {
        var filter =
            "(&(" + ProtocolProviderFactory.PROTOCOL + "=" + ProtocolNames.SIP
                + ")"
                + "(" + ProtocolProviderFactory.USER_ID
                + "=sipuser@sipserver.net))";
        var ref = bc.getServiceReferences(ProtocolProviderService.class, filter)
            .stream()
            .findFirst()
            .orElseThrow();
        return (MockProtocolProvider) bc.getService(ref);
    }

    public SipGateway getSipGateway()
    {
        return ServiceUtils.getService(bc, SipGateway.class);
    }
}
