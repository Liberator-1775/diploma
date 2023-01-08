package org.diploma.ddclient;

import com.timgroup.statsd.*;
import net.java.sip.communicator.util.osgi.*;
import org.jitsi.service.configuration.*;
import org.jitsi.utils.*;
import org.osgi.framework.*;

public class DdClientActivator
    implements BundleActivator
{
    public static final String DDCLIENT_PREFIX_PNAME
        = "org.jitsi.ddclient.prefix";

    public static final String DDCLIENT_HOST_PNAME = "org.jitsi.ddclient.host";

    public static final String DDCLIENT_PORT_PNAME = "org.jitsi.ddclient.port";

    private static final String DEFAULT_PREFIX = "";

    private static final String DEFAULT_HOST = "localhost";

    private static final int DEFAULT_PORT = 8125;

    private StatsDClient client;

    private ServiceRegistration<StatsDClient> serviceRegistration;

    protected ConfigurationService cfg;

    @Override
    public void start(BundleContext context)
        throws Exception
    {
        if (client != null)
        {
            return;
        }

        cfg = ServiceUtils.getService(context, ConfigurationService.class);

        String prefix = ConfigUtils.getString(cfg,
            DDCLIENT_PREFIX_PNAME, DEFAULT_PREFIX);

        if (prefix.isEmpty())
        {
            return;
        }

        String host = ConfigUtils.getString(cfg,
            DDCLIENT_HOST_PNAME, DEFAULT_HOST);
        int port = ConfigUtils.getInt(cfg,
            DDCLIENT_PORT_PNAME, DEFAULT_PORT);

        client = new NonBlockingStatsDClientBuilder()
            .prefix(prefix)
            .hostname(host)
            .port(port)
            .build();

        serviceRegistration
            = context.registerService(StatsDClient.class, client, null);
    }

    @Override
    public void stop(BundleContext context)
        throws Exception
    {
        if (serviceRegistration != null)
        {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }

        if (client != null)
        {
            client.stop();
            client = null;
        }
    }
}
