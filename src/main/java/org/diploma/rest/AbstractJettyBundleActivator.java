package org.diploma.rest;

import net.java.sip.communicator.util.osgi.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.util.ssl.*;
import org.jitsi.rest.*;
import org.jitsi.service.configuration.*;
import org.jitsi.utils.logging.*;
import org.osgi.framework.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public abstract class AbstractJettyBundleActivator
    extends DependentActivator
{
    private static final Logger logger
        = Logger.getLogger(AbstractJettyBundleActivator.class);

    static
    {
        String jettyLogLevelProperty = "org.eclipse.jetty.LEVEL";

        if (System.getProperty(jettyLogLevelProperty) == null)
        {
            String jettyLogLevelValue;

            if (logger.isDebugEnabled())
                jettyLogLevelValue = "DEBUG";
            else if (logger.isInfoEnabled())
                jettyLogLevelValue = "INFO";
            else
                jettyLogLevelValue = null;
            if (jettyLogLevelValue != null)
                System.setProperty(jettyLogLevelProperty, jettyLogLevelValue);
        }
    }

    private ConfigurationService configService;

    protected ConfigurationService getConfigService()
    {
        return configService;
    }

    protected static Handler initializeHandlerList(List<Handler> handlers)
    {
        int handlerCount = handlers.size();

        if (handlerCount == 1)
        {
            return handlers.get(0);
        }
        else
        {
            HandlerList handlerList = new HandlerList();

            handlerList.setHandlers(
                handlers.toArray(new Handler[handlerCount]));
            return handlerList;
        }
    }

    protected final JettyBundleActivatorConfig config;

    protected Server server;

    protected AbstractJettyBundleActivator(
        String legacyPropertyPrefix,
        String newPropertyPrefix)
    {
        super(ConfigurationService.class);
        this.config = new JettyBundleActivatorConfig(legacyPropertyPrefix, newPropertyPrefix);
    }

    protected void didStart(BundleContext bundleContext)
        throws Exception
    {
    }

    protected void didStop(BundleContext bundleContext)
        throws Exception
    {
    }

    protected void doStart(BundleContext bundleContext)
        throws Exception
    {
        try
        {
            Server server = initializeServer(bundleContext);

            server.start();

            this.server = server;
        }
        catch (Exception e)
        {
            logger.error(
                "Failed to initialize and/or start a new Jetty HTTP(S)"
                    + " server instance.",
                e);
            throw e;
        }
    }

    protected void doStop(BundleContext bundleContext)
        throws Exception
    {
        if (server != null)
        {
            server.stop();
            server = null;
        }
    }

    private int getPort()
    {
        if (isTls())
        {
            return config.getTlsPort();
        }
        else
        {
            return config.getPort();
        }
    }

    protected boolean isTls()
    {
        return config.isTls();
    }

    private Connector initializeConnector(Server server)
        throws Exception
    {
        HttpConfiguration httpCfg = new HttpConfiguration();

        httpCfg.setSecurePort(config.getTlsPort());
        httpCfg.setSecureScheme("https");

        Connector connector;

        if (!isTls())
        {
            connector = new ServerConnector(server, new HttpConnectionFactory(httpCfg));
        }
        else
        {
            File sslContextFactoryKeyStoreFile = Paths.get(Objects.requireNonNull(config.getKeyStorePath())).toFile();
            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

            String version = System.getProperty("java.version");
            if (version.startsWith("1."))
            {
                version = version.substring(2, 3);
            }
            else
            {
                int dot = version.indexOf(".");
                if (dot != -1)
                {
                    version = version.substring(0, dot);
                }
            }
            int javaVersion = Integer.parseInt(version);

            if (javaVersion >= 11)
            {
                sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
            }
            else
            {
                sslContextFactory.setIncludeProtocols("TLSv1.2");
            }
            sslContextFactory.setIncludeCipherSuites(
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");

            sslContextFactory.setRenegotiationAllowed(false);
            if (config.getKeyStorePassword() != null)
            {
                sslContextFactory.setKeyStorePassword(config.getKeyStorePassword());
            }
            sslContextFactory.setKeyStorePath(sslContextFactoryKeyStoreFile.getPath());
            sslContextFactory.setNeedClientAuth(config.getNeedClientAuth());

            HttpConfiguration httpsCfg = new HttpConfiguration(httpCfg);

            httpsCfg.addCustomizer(new SecureRequestCustomizer());

            connector = new ServerConnector(
                server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpsCfg));
        }

        setPort(connector, getPort());

        if (config.getHost() != null)
        {
            setHost(connector, config.getHost());
        }

        return connector;
    }

    protected Handler initializeHandler(
        BundleContext bundleContext,
        Server server)
        throws Exception
    {
        return initializeHandlerList(bundleContext, server);
    }

    protected abstract Handler initializeHandlerList(
        BundleContext bundleContext,
        Server server)
        throws Exception;

    protected Server initializeServer(BundleContext bundleContext)
        throws Exception
    {
        Server server = new Server();
        Connector connector = initializeConnector(server);

        server.addConnector(connector);

        Handler handler = initializeHandler(bundleContext, server);

        if (handler != null)
            server.setHandler(handler);

        return server;
    }

    protected void setHost(Connector connector, String host)
        throws Exception
    {
        connector
            .getClass()
            .getMethod("setHost", String.class)
            .invoke(connector, host);
    }

    protected void setPort(Connector connector, int port)
        throws Exception
    {
        connector
            .getClass()
            .getMethod("setPort", int.class)
            .invoke(connector, port);
    }

    @Override
    public void startWithServices(BundleContext bundleContext)
        throws Exception
    {
        configService = getService(ConfigurationService.class);
        if (willStart(bundleContext))
        {
            doStart(bundleContext);
            didStart(bundleContext);
        }
        else
        {
            logger.info("Not starting the Jetty service for "
                + getClass().getName() + "(port=" + getPort() + ")");
        }
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        super.stop(bundleContext);
        if (willStop(bundleContext))
        {
            doStop(bundleContext);
            didStop(bundleContext);
        }
    }

    protected boolean willStart(BundleContext bundleContext)
        throws Exception
    {
        return getPort() > 0;
    }

    protected boolean willStop(BundleContext bundleContext)
        throws Exception
    {
        return true;
    }
}
