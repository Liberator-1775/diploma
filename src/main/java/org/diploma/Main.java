package org.diploma;

import java.util.*;
import net.java.sip.communicator.impl.certificate.*;
import net.java.sip.communicator.impl.configuration.*;
import net.java.sip.communicator.impl.credentialsstorage.*;
import net.java.sip.communicator.impl.dns.*;
import net.java.sip.communicator.impl.globaldisplaydetails.*;
import net.java.sip.communicator.impl.neomedia.*;
import net.java.sip.communicator.impl.netaddr.*;
import net.java.sip.communicator.impl.packetlogging.*;
import net.java.sip.communicator.impl.phonenumbers.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.impl.protocol.sip.*;
import net.java.sip.communicator.impl.resources.*;
import net.java.sip.communicator.plugin.defaultresourcepack.*;
import net.java.sip.communicator.plugin.reconnectplugin.*;
import net.java.sip.communicator.service.gui.internal.*;
import net.java.sip.communicator.service.notification.*;
import net.java.sip.communicator.service.protocol.*;

import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import org.apache.commons.lang3.*;
import org.diploma.ddclient.DdClientActivator;
import org.diploma.osgi.EmptyHidServiceActivator;
import org.diploma.osgi.EmptyMasterPasswordInputServiceActivator;
import org.diploma.osgi.EmptyUiServiceActivator;
import org.diploma.osgi.JigasiBundleConfig;
import org.diploma.rest.RESTBundleActivator;
import org.diploma.rest.TranscriptServerBundleActivator;
import org.diploma.version.VersionActivator;
import org.diploma.xmpp.CallControlMucActivator;
import org.jitsi.cmd.*;
import org.jitsi.impl.osgi.framework.launch.*;
import org.diploma.ddclient.*;
import org.diploma.osgi.*;
import org.diploma.rest.*;
import org.diploma.version.*;
import org.diploma.xmpp.*;
import org.jitsi.meet.*;
import org.jitsi.osgi.framework.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.debugger.*;
import org.jivesoftware.smack.tcp.*;
import org.osgi.framework.*;
import org.osgi.framework.launch.*;
import org.osgi.framework.startlevel.*;

public class Main
{
    private static final String MAX_PORT_ARG_NAME = "--max-port";

    private static final int MAX_PORT_ARG_VALUE = 20000;

    private static final String MIN_PORT_ARG_NAME = "--min-port";

    private static final int MIN_PORT_ARG_VALUE = 10000;

    private static final String LOGDIR_ARG_NAME = "--logdir";

    public static final String PNAME_SC_LOG_DIR_LOCATION =
        "net.java.sip.communicator.SC_LOG_DIR_LOCATION";

    public static final String PNAME_SC_CACHE_DIR_LOCATION =
        "net.java.sip.communicator.SC_CACHE_DIR_LOCATION";

    private static final String CONFIG_DIR_ARG_NAME = "--configdir";

    private static final String CONFIG_DIR_NAME_ARG_NAME = "--configdirname";

    private static final String CONFIG_WRITABLE_ARG_NAME = "--configwritable";

    private static final String[] disabledSmackPackages
        = new String[]
        {
            "org.jivesoftware.smackx.iqlast",
            "org.jivesoftware.smackx.bytestreams",
            "org.jivesoftware.smackx.filetransfer",
            "org.jivesoftware.smackx.hoxt",
            "org.jivesoftware.smackx.httpfileupload",
            "org.jivesoftware.smackx.iot",
            "org.jivesoftware.smackx.si",
            "org.jivesoftware.smackx.vcardtemp",
            "org.jivesoftware.smackx.xhtmlim",
            "org.jivesoftware.smackx.xdata",
            "org.jivesoftware.smackx.eme",
            "org.jivesoftware.smackx.iqprivate",
            "org.jivesoftware.smackx.bookmarks",
            "org.jivesoftware.smackx.receipts",
            "org.jivesoftware.smackx.commands",
            "org.jivesoftware.smackx.privacy",
            "org.jivesoftware.smackx.time",
            "org.jivesoftware.smackx.muc.bookmarkautojoin"
        };

    public static void main(String[] args)
        throws ParseException, BundleException, InterruptedException
    {
        CmdLine cmdLine = new CmdLine();

        cmdLine.parse(args);

        int maxPort
            = cmdLine.getIntOptionValue(
                    MAX_PORT_ARG_NAME, MAX_PORT_ARG_VALUE);

        int minPort
            = cmdLine.getIntOptionValue(
                    MIN_PORT_ARG_NAME, MIN_PORT_ARG_VALUE);

        System.setProperty(
            DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(maxPort));
        System.setProperty(
            OperationSetBasicTelephony.MAX_MEDIA_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(maxPort));

        System.setProperty(
            DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(minPort));
        System.setProperty(
            OperationSetBasicTelephony.MIN_MEDIA_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(minPort));

        System.setProperty(
            CallPeerJabberImpl.SKIP_DISCO_INFO_ON_SESSION_INITIATE,
            "true");
        System.setProperty(
            CallPeerJabberImpl.SKIP_RINGING_ON_SESSION_INITIATE,
            "true");

        String configDir
            = cmdLine.getOptionValue(
                    CONFIG_DIR_ARG_NAME, System.getProperty("user.dir"));

        System.setProperty(
            ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, configDir);

        String configDirName
            = cmdLine.getOptionValue(CONFIG_DIR_NAME_ARG_NAME, "diploma-home");

        System.setProperty(
            ConfigurationService.PNAME_SC_HOME_DIR_NAME,
            configDirName);

        boolean isConfigReadonly =
            !Boolean.parseBoolean(cmdLine.getOptionValue(CONFIG_WRITABLE_ARG_NAME));
        System.setProperty(
            ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
            Boolean.toString(isConfigReadonly));

        String logdir = cmdLine.getOptionValue(LOGDIR_ARG_NAME);
        if (StringUtils.isNotEmpty(logdir))
        {
            System.setProperty(PNAME_SC_LOG_DIR_LOCATION, logdir);
            System.setProperty(PNAME_SC_CACHE_DIR_LOCATION, logdir);
        }

        System.setProperty(ConfigurationActivator.PNAME_USE_PROPFILE_CONFIG,
            "true");

        System.setProperty("net.java.sip.communicator.impl.protocol.sip" +
                ".SKIP_REINVITE_ON_FOCUS_CHANGE_PROP",
            "true");

        String deviceSystemPackage = "org.jitsi.impl.neomedia.device";
        System.setProperty(deviceSystemPackage + ".MacCoreaudioSystem.disabled", "true");
        System.setProperty(deviceSystemPackage + ".PulseAudioSystem.disabled", "true");
        System.setProperty(deviceSystemPackage + ".PortAudioSystem.disabled", "true");

        System.setProperty("jdk.xml.entityExpansionLimit", "0");
        System.setProperty("jdk.xml.maxOccurLimit", "0");
        System.setProperty("jdk.xml.elementAttributeLimit", "524288");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.maxXMLNameLimit", "524288");
        System.setProperty("jdk.xml.entityReplacementLimit", "0");

        disableSmackProviders();

        ReflectionDebuggerFactory.setDebuggerClass(JulDebugger.class);
        SmackConfiguration.setDefaultReplyTimeout(15000);
        SmackConfiguration.TRUELY_ASYNC_SENDS = true;

        XMPPTCPConnection.setUseStreamManagementDefault(false);
        XMPPTCPConnection.setUseStreamManagementResumptionDefault(false);

        JigasiBundleConfig.setSystemPropertyDefaults();
        List<Class<? extends BundleActivator>> protocols = List.of(
            SipActivator.class,
            JabberActivator.class);
        var fw = start(protocols);

        fw.getBundleContext().registerService(
            ShutdownService.class, () -> {
                try
                {
                    fw.stop();

                    Thread.sleep(3000);

                    System.exit(0);
                }
                catch(Exception e)
                {
                    e.printStackTrace();

                    System.exit(-1);
                }
            }, null);
        fw.waitForStop(0);
    }

    public static Framework start(List<Class<? extends BundleActivator>> protocols)
        throws BundleException, InterruptedException
    {
        List<Class<? extends BundleActivator>> activators = new ArrayList<>(List.of(
            LibJitsiActivator.class,
            ConfigurationActivator.class,
            UtilActivator.class,
            DefaultResourcePackActivator.class,
            ResourceManagementActivator.class,
            NotificationServiceActivator.class,
            DnsUtilActivator.class,
            CredentialsStorageActivator.class,
            NetaddrActivator.class,
            PacketLoggingActivator.class,
            GuiServiceActivator.class,
            ProtocolMediaActivator.class,
            NeomediaActivator.class,
            CertificateVerificationActivator.class,
            VersionActivator.class,
            ProtocolProviderActivator.class,
            GlobalDisplayDetailsActivator.class,
            ReconnectPluginActivator.class,
            PhoneNumberServiceActivator.class,
            EmptyHidServiceActivator.class,
            EmptyUiServiceActivator.class,
            EmptyMasterPasswordInputServiceActivator.class
        ));
        activators.addAll(protocols);
        activators.addAll(List.of(
            JigasiBundleActivator.class,
            RESTBundleActivator.class,
            TranscriptServerBundleActivator.class,
            CallControlMucActivator.class,
            DdClientActivator.class
        ));
        var options = new HashMap<String, String>();
        options.put(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, "3");
        Framework fw = new FrameworkImpl(options, Main.class.getClassLoader());
        fw.init();
        var bundleContext = fw.getBundleContext();
        for (Class<? extends BundleActivator> activator : activators)
        {
            var url = activator.getProtectionDomain().getCodeSource().getLocation().toString();
            var bundle = bundleContext.installBundle(url);
            var startLevel = bundle.adapt(BundleStartLevel.class);
            startLevel.setStartLevel(2);
            var bundleActivator = bundle.adapt(BundleActivatorHolder.class);
            bundleActivator.addBundleActivator(activator);
        }
        fw.start();
        return fw;
    }

    private static void disableSmackProviders()
    {
        for (String classPackage: disabledSmackPackages)
        {
            SmackConfiguration.addDisabledSmackClass(classPackage);
        }
    }
}
