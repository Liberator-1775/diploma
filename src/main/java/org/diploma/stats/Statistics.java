package org.diploma.stats;

import java.io.*;
import java.lang.management.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import jakarta.servlet.http.*;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.util.osgi.ServiceUtils;
import org.apache.commons.lang3.*;
import org.diploma.AbstractGatewaySession;
import org.diploma.JigasiBundleActivator;
import org.diploma.SipGateway;
import org.diploma.TranscriptionGateway;
import org.diploma.version.CurrentVersionImpl;
import org.diploma.xmpp.CallControlMucActivator;
import org.eclipse.jetty.server.*;
import org.diploma.version.*;
import org.jitsi.utils.logging.Logger;
import org.osgi.framework.*;
import org.json.simple.*;

import org.jitsi.xmpp.extensions.colibri.*;
import static org.jitsi.xmpp.extensions.colibri.ColibriStatsExtension.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.*;

import org.diploma.*;
import org.diploma.xmpp.*;

import static org.diploma.JvbConference.*;

public class Statistics
{
    private final static Logger logger
        = Logger.getLogger(Statistics.class);

    public static final String TOTAL_CALLS_WITH_CONNECTION_FAILED
        = "total_calls_with_connection_failed";

    public static final String TOTAL_CALLS_WITH_SIP_CALL_WAITING
        = "total_calls_with_sip_call_waiting";

    public static final String TOTAL_CALLS_WITH_SIP_CALL_RECONNECTED
        = "total_calls_with_sip_call_reconnected";

    public static final String TOTAL_CALLS_WITH_JVB_MIGRATE = "total_calls_with_jvb_migrate";

    public static final String TOTAL_CALLS_JVB_NO_MEDIA = "total_calls_jvb_no_media";

    public static final String TOTAL_CALLS_NO_HEARTBEAT = "total_calls_no_heartbeat_response";

    private static final String CONFERENCES_THRESHOLD_PNAME = "org.diploma.CONFERENCES_THRESHOLD";

    private static final double CONFERENCES_THRESHOLD_DEFAULT = 100;

    private static final double CONFERENCES_THRESHOLD = JigasiBundleActivator
            .getConfigurationService()
            .getDouble(CONFERENCES_THRESHOLD_PNAME, CONFERENCES_THRESHOLD_DEFAULT);

    private static final String STRESS_LEVEL = "stress_level";

    private static int totalParticipantsCount = 0;

    private static int totalConferencesCount = 0;

    private static AtomicLong totalCallsWithMediaDroppedCount
        = new AtomicLong();

    private static AtomicLong totalCallsWithConnectionFailedCount
        = new AtomicLong();

    private static AtomicLong totalCallsWithSipCallWaiting
        = new AtomicLong();

    private static AtomicLong totalCallsWithSipCalReconnected
        = new AtomicLong();

    private static AtomicLong totalCallsWithJvbMigrate = new AtomicLong();

    private static AtomicLong totalCallsJvbNoMedia = new AtomicLong();

    private static AtomicLong totalCallsWithNoHeartBeatResponse = new AtomicLong();

    private static long cumulativeConferenceSeconds = 0;

    private static final DateFormat dateFormat;

    static
    {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final int CONFERENCE_SIZE_BUCKETS = 22;

    private static ExecutorService threadPool = Executors.newFixedThreadPool(3);

    public static synchronized void sendJSON(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException
    {
        Map<String, Object> stats = new HashMap<>();

        stats.putAll(getSessionStats());

        stats.put(THREADS,
            ManagementFactory.getThreadMXBean().getThreadCount());

        stats.put(TIMESTAMP, currentTimeMillis());

        stats.put(TOTAL_CONFERENCES_COMPLETED, totalConferencesCount);
        stats.put(TOTAL_PARTICIPANTS, totalParticipantsCount);
        stats.put(TOTAL_CONFERENCE_SECONDS, cumulativeConferenceSeconds);
        stats.put(TOTAL_CALLS_WITH_DROPPED_MEDIA,
            totalCallsWithMediaDroppedCount.get());
        stats.put(TOTAL_CALLS_WITH_CONNECTION_FAILED,
            totalCallsWithConnectionFailedCount.get());
        stats.put(TOTAL_CALLS_WITH_SIP_CALL_WAITING,
            totalCallsWithSipCallWaiting.get());
        stats.put(TOTAL_CALLS_WITH_SIP_CALL_RECONNECTED,
            totalCallsWithSipCalReconnected.get());
        stats.put(TOTAL_CALLS_WITH_JVB_MIGRATE, totalCallsWithJvbMigrate.get());
        stats.put(TOTAL_CALLS_JVB_NO_MEDIA, totalCallsJvbNoMedia.get());
        stats.put(TOTAL_CALLS_NO_HEARTBEAT, totalCallsWithNoHeartBeatResponse.get());

        stats.put(SHUTDOWN_IN_PROGRESS,
            JigasiBundleActivator.isShutdownInProgress());

        response.setStatus(HttpServletResponse.SC_OK);
        new JSONObject(stats).writeJSONString(response.getWriter());
    }

    private static Map<String, Object> getSessionStats()
    {
        List<AbstractGatewaySession> sessions = new ArrayList<>();
        JigasiBundleActivator.getAvailableGateways().forEach(
            gw -> sessions.addAll(gw.getActiveSessions()));

        int[] conferenceSizes = new int[CONFERENCE_SIZE_BUCKETS];
        Map<String, Object> stats = new HashMap<>();

        int participants = 0;
        int conferences = 0;

        for (AbstractGatewaySession ses : sessions)
        {
            if (ses.getJvbChatRoom() == null)
            {
                continue;
            }

            int conferenceEndpoints
                = ses.getJvbChatRoom().getMembersCount() - 1;
            participants += conferenceEndpoints;
            int idx
                = conferenceEndpoints < conferenceSizes.length
                ? conferenceEndpoints
                : conferenceSizes.length - 1;
            if (idx >= 0)
            {
                conferenceSizes[idx]++;
            }
            conferences++;
        }

        stats.put(CONFERENCES, conferences);

        JSONArray conferenceSizesJson = new JSONArray();
        for (int size : conferenceSizes)
            conferenceSizesJson.add(size);
        stats.put(CONFERENCE_SIZES, conferenceSizesJson);

        stats.put(PARTICIPANTS, participants);

        double stressLevel = conferences / CONFERENCES_THRESHOLD;
        stats.put(STRESS_LEVEL, stressLevel);

        return stats;
    }

    private static String currentTimeMillis()
    {
        return dateFormat.format(new Date());
    }

    public static void addTotalParticipantsCount(int value)
    {
        totalParticipantsCount += value;
    }

    public static void addTotalConferencesCount(int value)
    {
        totalConferencesCount += value;
    }

    public static void incrementTotalCallsWithMediaDropped()
    {
        totalCallsWithMediaDroppedCount.incrementAndGet();
    }

    public static void incrementTotalCallsWithConnectionFailed()
    {
        totalCallsWithConnectionFailedCount.incrementAndGet();
    }

    public static void incrementTotalCallsWithSipCallWaiting()
    {
        totalCallsWithSipCallWaiting.incrementAndGet();
    }

    public static void incrementTotalCallsWithSipCallReconnected()
    {
        totalCallsWithSipCalReconnected.incrementAndGet();
    }

    public static void incrementTotalCallsWithJvbMigrate()
    {
        totalCallsWithJvbMigrate.incrementAndGet();
    }

    public static void incrementTotalCallsJvbNoMedia()
    {
        totalCallsJvbNoMedia.incrementAndGet();
    }

    public static void incrementTotalCallsWithNoSipHeartbeat()
    {
        totalCallsWithNoHeartBeatResponse.incrementAndGet();
    }

    public static void addCumulativeConferenceSeconds(long value)
    {
        cumulativeConferenceSeconds += value;
    }

    public static void updatePresenceStatusForXmppProviders()
    {
        BundleContext osgiContext = JigasiBundleActivator.osgiContext;
        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                osgiContext,
                ProtocolProviderService.class);

        List<ProtocolProviderService> ppss
            = refs.stream()
            .map(ref -> osgiContext.getService(ref))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        updatePresenceStatusForXmppProviders(ppss);
    }

    public static void updatePresenceStatusForXmppProviders(
        List<ProtocolProviderService> ppss)
    {
        final Map<String, Object> stats = getSessionStats();

        ppss.forEach(pps ->
            updatePresenceStatusForXmppProvider(
                pps,
                (int)stats.get(PARTICIPANTS),
                (int)stats.get(CONFERENCES),
                (double)stats.get(STRESS_LEVEL),
                JigasiBundleActivator.isShutdownInProgress()));
    }

    private static void updatePresenceStatusForXmppProvider(
        ProtocolProviderService pps,
        int participants,
        int conferences,
        double stressLevel,
        boolean shutdownInProgress)
    {
        if (ProtocolNames.JABBER.equals(pps.getProtocolName())
            && pps.getAccountID() instanceof JabberAccountID
            && !((JabberAccountID)pps.getAccountID()).isAnonymousAuthUsed()
            && pps.isRegistered())
        {
            try
            {
                String roomName = pps.getAccountID().getAccountPropertyString(
                    CallControlMucActivator.ROOM_NAME_ACCOUNT_PROP);
                if (roomName == null)
                {
                    return;
                }

                OperationSetMultiUserChat muc
                    = pps.getOperationSet(OperationSetMultiUserChat.class);
                ChatRoom mucRoom = muc.findRoom(roomName);

                if (mucRoom == null)
                {
                    return;
                }

                ColibriStatsExtension stats = new ColibriStatsExtension();
                stats.addStat(new ColibriStatsExtension.Stat(
                    CONFERENCES,
                    conferences));
                stats.addStat(new ColibriStatsExtension.Stat(
                    PARTICIPANTS,
                    participants));
                stats.addStat(new ColibriStatsExtension.Stat(
                    SHUTDOWN_IN_PROGRESS,
                    shutdownInProgress));
                stats.addStat((new ColibriStatsExtension.Stat(
                    STRESS_LEVEL,
                    stressLevel
                )));

                String region = JigasiBundleActivator.getConfigurationService()
                    .getString(LOCAL_REGION_PNAME);
                if (StringUtils.isNotEmpty(StringUtils.trim(region)))
                {
                    stats.addStat(new ColibriStatsExtension.Stat(
                        REGION,
                        region));
                }

                stats.addStat(new ColibriStatsExtension.Stat(
                    VERSION,
                    CurrentVersionImpl.VERSION));

                ColibriStatsExtension.Stat transcriberStat =
                    new ColibriStatsExtension.Stat(
                        SUPPORTS_TRANSCRIPTION, false);
                ColibriStatsExtension.Stat sipgwStat =
                    new ColibriStatsExtension.Stat(SUPPORTS_SIP, false);

                JigasiBundleActivator.getAvailableGateways().forEach(gw ->
                    {
                        if (gw instanceof TranscriptionGateway)
                        {
                            transcriberStat.setValue(true);
                        }
                        if (gw instanceof SipGateway)
                        {
                            sipgwStat.setValue(true);
                        }
                    });
                stats.addStat(transcriberStat);
                stats.addStat(sipgwStat);

                threadPool.execute(
                    () -> pps.getOperationSet(OperationSetJitsiMeetToolsJabber.class)
                            .sendPresenceExtension(mucRoom, stats));
            }
            catch (Exception e)
            {
                logger.error("Error updating presence for:" + pps, e);
            }
        }
    }
}
