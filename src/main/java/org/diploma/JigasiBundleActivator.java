package org.diploma;

import com.timgroup.statsd.*;
import net.java.sip.communicator.util.osgi.*;
import org.diploma.health.Health;
import org.diploma.stats.Statistics;
import org.jitsi.meet.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.*;
import org.jitsi.xmpp.extensions.jibri.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.xmpp.extensions.rayo.*;
import net.java.sip.communicator.service.protocol.*;
import org.diploma.health.*;
import org.diploma.stats.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.provider.*;
import org.osgi.framework.*;

import java.util.*;

public class JigasiBundleActivator
    extends DependentActivator
    implements ServiceListener
{
    private final static Logger logger
        = Logger.getLogger(JigasiBundleActivator.class);

    public static BundleContext osgiContext;

    public final static String P_NAME_MUC_JOIN_TIMEOUT
        = "org.diploma.MUC_JOIN_TIMEOUT";

    public final static String P_NAME_ENABLE_TRANSCRIPTION
        = "org.diploma.ENABLE_TRANSCRIPTION";

    public final static String P_NAME_ENABLE_SIP
        = "org.diploma.ENABLE_SIP";

    public final static String P_NAME_ENABLE_SIP_STARTMUTED
        = "org.diploma.ENABLE_SIP_STARTMUTED";

    public final static long MUC_JOIN_TIMEOUT_DEFAULT_VALUE = 10;

    public final static boolean ENABLE_TRANSCRIPTION_DEFAULT_VALUE = false;

    public final static boolean ENABLE_SIP_DEFAULT_VALUE = true;

    public final static boolean ENABLE_SIP_STARTMUTED_DEFAULT_VALUE = false;

    private static SipGateway sipGateway;

    private static TranscriptionGateway transcriptionGateway;

    private static List<AbstractGateway> gateways = new ArrayList<>();

    private static boolean shutdownInProgress;

    private static ConfigurationService configService;

    public static ConfigurationService getConfigurationService()
    {
        return configService;
    }

    public static boolean isTranscriptionEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_TRANSCRIPTION,
                ENABLE_TRANSCRIPTION_DEFAULT_VALUE);
    }

    public static boolean isSipEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_SIP, ENABLE_SIP_DEFAULT_VALUE);
    }

    public static boolean isSipStartMutedEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_SIP_STARTMUTED, ENABLE_SIP_STARTMUTED_DEFAULT_VALUE);
    }

    public static StatsDClient getDataDogClient()
    {
        return ServiceUtils.getService(osgiContext, StatsDClient.class);
    }

    public JigasiBundleActivator()
    {
        super(ConfigurationService.class);
    }

    @Override
    public void startWithServices(final BundleContext bundleContext)
    {
        osgiContext = bundleContext;
        configService = getService(ConfigurationService.class);

        StartMutedProvider.registerStartMutedProvider();

        if (isSipEnabled())
        {
            if (isSipStartMutedEnabled())
            {
                MuteIqProvider.registerMuteIqProvider();
            }

            ProviderManager.addExtensionProvider(
                RecordingStatus.ELEMENT,
                RecordingStatus.NAMESPACE,
                new DefaultPacketExtensionProvider<>(RecordingStatus.class)
            );

            logger.info("initialized SipGateway");
            sipGateway = new SipGateway(bundleContext)
            {
                @Override
                void notifyCallEnded(CallContext callContext)
                {
                    super.notifyCallEnded(callContext);

                    maybeDoShutdown();
                }
            };
            gateways.add(sipGateway);
            osgiContext.registerService(SipGateway.class, sipGateway, null);
        }
        else
        {
            logger.info("skipped initialization of SipGateway");
        }

        if (isTranscriptionEnabled())
        {
            logger.info("initialized TranscriptionGateway");
            transcriptionGateway = new TranscriptionGateway(bundleContext)
            {
                @Override
                void notifyCallEnded(CallContext callContext)
                {
                    super.notifyCallEnded(callContext);

                    maybeDoShutdown();
                }
            };
            gateways.add(transcriptionGateway);
            osgiContext.registerService(TranscriptionGateway.class,
                transcriptionGateway, null);
        }
        else
        {
            logger.info("skipped initialization of TranscriptionGateway");
        }

        new RayoIqProvider().registerRayoIQs();

        bundleContext.addServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                    osgiContext,
                    ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            ProtocolProviderService pps = osgiContext.getService(ref);

            if (ProtocolNames.SIP.equals(pps.getProtocolName()))
            {
                if (sipGateway != null)
                    sipGateway.setSipProvider(pps);
            }
        }

        Health.start();
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        logger.info("Stopping JigasiBundleActivator");

        gateways.forEach(AbstractGateway::stop);
        gateways.clear();

        Health.stop();
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
        {
            return;
        }

        ServiceReference<?> ref = serviceEvent.getServiceReference();
        Object service = osgiContext.getService(ref);
        if (!(service instanceof ProtocolProviderService))
        {
            return;
        }

        ProtocolProviderService pps = (ProtocolProviderService) service;
        if (sipGateway != null && sipGateway.getSipProvider() == null &&
            ProtocolNames.SIP.equals(pps.getProtocolName()))
        {
            sipGateway.setSipProvider(pps);
        }
    }

    public static boolean isShutdownInProgress()
    {
        return shutdownInProgress;
    }

    public static void enableGracefulShutdownMode()
    {
        if (!shutdownInProgress)
        {
            logger.info("Entered graceful shutdown mode");
        }
        shutdownInProgress = true;
        maybeDoShutdown();

        Statistics.updatePresenceStatusForXmppProviders();
    }

    private static void maybeDoShutdown()
    {
        if (!shutdownInProgress)
            return;

        List<AbstractGatewaySession> sessions = new ArrayList<>();
        gateways.forEach(gw -> sessions.addAll(gw.getActiveSessions()));

        if (sessions.isEmpty())
        {
            gateways.forEach(AbstractGateway::stop);

            ShutdownService shutdownService = ServiceUtils.getService(osgiContext, ShutdownService.class);

            logger.info("Jigasi is shutting down NOW");
            shutdownService.beginShutdown();
        }
    }

    public static List<AbstractGateway> getAvailableGateways()
    {
        return gateways;
    }
}
