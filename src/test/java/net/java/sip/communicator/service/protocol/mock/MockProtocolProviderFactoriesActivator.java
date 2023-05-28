package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;

import org.osgi.framework.*;

import java.util.*;

public class MockProtocolProviderFactoriesActivator
    implements BundleActivator
{
    private ServiceRegistration<?> xmppRegistration;

    private ServiceRegistration<?> sipRegistration;

    @Override
    public void start(BundleContext bundleContext)
    {
        MockProtocolProviderFactory sipFactory
            = new MockProtocolProviderFactory(
                    bundleContext, ProtocolNames.SIP);

        MockProtocolProviderFactory xmppFactory
            = new MockProtocolProviderFactory(
                    bundleContext, ProtocolNames.JABBER);

        Hashtable<String, String> hashtable = new Hashtable<String, String>();

        hashtable.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);

        xmppRegistration = bundleContext.registerService(
            ProtocolProviderFactory.class.getName(),
            xmppFactory,
            hashtable);

        hashtable.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.SIP);

        sipRegistration = bundleContext.registerService(
            ProtocolProviderFactory.class.getName(),
            sipFactory,
            hashtable);
    }

    @Override
    public void stop(BundleContext bundleContext)
    {
        if (xmppRegistration != null)
            xmppRegistration.unregister();

        if (sipRegistration != null)
            sipRegistration.unregister();
    }
}
