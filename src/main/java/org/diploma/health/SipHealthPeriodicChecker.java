package org.diploma.health;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.apache.commons.lang3.StringUtils;
import org.diploma.JigasiBundleActivator;
import org.diploma.SipGateway;
import org.diploma.sounds.SoundNotificationManager;
import org.diploma.transcription.TranscribingAudioMixerMediaDevice;
import org.diploma.*;
import org.diploma.sounds.*;
import org.diploma.transcription.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.utils.logging.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

class SipHealthPeriodicChecker
    extends PeriodicRunnableWithObject<SipGateway>
{
    private static final Logger logger
        = Logger.getLogger(SipHealthPeriodicChecker.class);

    private static final String PROP_HEALTH_CHECK_INTERVAL
        = "org.diploma.HEALTH_CHECK_INTERVAL";

    private static final String PROP_HEALTH_CHECK_TIMEOUT
        = "org.diploma.HEALTH_CHECK_TIMEOUT";

    private static final String PROP_HEALTH_CHECK_SIP_URI
        = "org.diploma.HEALTH_CHECK_SIP_URI";

    private static final long DEFAULT_HEALTH_CHECK_INTERVAL = 5*60*1000;

    private static final long DEFAULT_HEALTH_CHECK_TIMEOUT = 10*60*1000;

    private static final long CALL_ESTABLISH_TIMEOUT = 10;

    private static final long CHECK_RETRY_INTERVAL = 60;

    private static final String HEALTH_CHECK_DEBUG_PROP_NAME =
        "org.diploma.HEALTH_CHECK_DEBUG_ENABLED";

    private boolean healthChecksDebugEnabled = false;

    private static long timeout;

    private static String healthCheckSipUri;

    private Exception lastResult = null;

    private long lastResultMs = -1;

    private final static ExecutorService injectSoundExecutor
        = Executors.newSingleThreadExecutor(new CustomizableThreadFactory("SipHealthPeriodicChecker", false));


    private SipHealthPeriodicChecker(SipGateway o, long period)
    {
        super(o, period, true);

        healthChecksDebugEnabled
            = JigasiBundleActivator.getConfigurationService()
                .getBoolean(HEALTH_CHECK_DEBUG_PROP_NAME, false);
    }

    static SipHealthPeriodicChecker create(SipGateway gw)
    {
        ConfigurationService conf
            = JigasiBundleActivator.getConfigurationService();
        long healthCheckInterval = conf.getLong(
            PROP_HEALTH_CHECK_INTERVAL, DEFAULT_HEALTH_CHECK_INTERVAL);
        timeout = conf.getLong(
            PROP_HEALTH_CHECK_TIMEOUT, DEFAULT_HEALTH_CHECK_TIMEOUT);

        healthCheckSipUri = conf.getString(PROP_HEALTH_CHECK_SIP_URI);

        if (StringUtils.isEmpty(StringUtils.trim(healthCheckSipUri)))
        {
            logger.warn(
                "No health check started, no HEALTH_CHECK_SIP_URI prop.");
            return null;
        }

        return new SipHealthPeriodicChecker(gw, healthCheckInterval);
    }

    public void check()
        throws Exception
    {
        if (this.o.getSipProvider().getRegistrationState()
                != RegistrationState.REGISTERED)
        {
            throw new Exception("SIP provider not registered.");
        }

        Exception lastResult = this.lastResult;
        long lastResultMs = this.lastResultMs;
        long timeSinceLastResult = System.currentTimeMillis() - lastResultMs;

        if (timeSinceLastResult > timeout)
        {
            throw new Exception(
                "No health checks performed recently, the last result was "
                    + timeSinceLastResult + "ms ago.");
        }

        if (lastResult != null)
        {
            throw new Exception(lastResult);
        }
    }

    @Override
    protected void doRun()
    {
        this.doRunInternal(true);
    }

    private void doRunInternal(boolean retryOnFailure)
    {
        long start = System.currentTimeMillis();
        Exception exception = null;

        try
        {
            doCheck(this.o.getSipProvider(), healthChecksDebugEnabled);
        }
        catch (Exception e)
        {
            exception = e;
        }

        long duration = System.currentTimeMillis() - start;
        lastResult = exception;
        lastResultMs = start + duration;

        if (exception == null)
        {
            logger.info(
                "Performed a successful health check in " + duration
                    + "ms. ");
        }
        else
        {
            logger.error(
                "Health check failed in " + duration + "ms:", exception);

            if (retryOnFailure)
            {
                new Timer().schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        doRunInternal(false);
                    }
                }, CHECK_RETRY_INTERVAL*1000);
            }
        }
    }

    private static void doCheck(
        ProtocolProviderService pps, boolean debugEnabled)
        throws Exception
    {
        CountDownLatch hangupLatch = new CountDownLatch(1);

        Boolean[] receivedBuffer = new Boolean[1];

        OperationSetBasicTelephony tele
            = pps.getOperationSet(OperationSetBasicTelephony.class);

        CallPeerListener callPeerListener = new CallPeerAdapter()
        {
            @Override
            public void peerStateChanged(CallPeerChangeEvent evt)
            {
                super.peerStateChanged(evt);

                CallPeer peer = evt.getSourceCallPeer();

                CallPeerState peerState = peer.getState();

                if (CallPeerState.CONNECTED.equals(peerState))
                {
                    injectSoundExecutor.execute(() -> {
                        try
                        {
                            long startNano = System.nanoTime();
                            long maxDuration = TimeUnit.NANOSECONDS.convert(CALL_ESTABLISH_TIMEOUT, TimeUnit.SECONDS);
                            while (hangupLatch.getCount() > 0 && (System.nanoTime() - startNano) < maxDuration)
                            {
                                SoundNotificationManager.injectSoundFile(
                                    peer.getCall(), SoundNotificationManager.PARTICIPANT_ALONE);

                                Thread.sleep(1000);
                            }
                        }
                        catch(InterruptedException ex)
                        {}
                    });
                }
            }
        };

        Call call = tele.createCall(healthCheckSipUri,
            new MediaAwareCallConference()
            {
                TranscribingAudioMixerMediaDevice mixer
                    = new TranscribingAudioMixerMediaDevice(
                       (receiveStream, buffer) ->
                            {
                                receivedBuffer[0] = true;
                                hangupLatch.countDown();
                            });

                @Override
                public MediaDevice getDefaultDevice(MediaType mediaType,
                    MediaUseCase useCase)
                {
                    if (MediaType.AUDIO.equals(mediaType))
                    {
                        return mixer;
                    }
                    return super.getDefaultDevice(mediaType, useCase);
                }
            });
        CallPeer sipPeer = call.getCallPeers().next();
        sipPeer.addCallPeerListener(callPeerListener);

        call.addCallChangeListener(new CallChangeAdapter()
        {
            @Override
            public void callStateChanged(CallChangeEvent callChangeEvent)
            {
                if (callChangeEvent.getNewValue().equals(CallState.CALL_ENDED))
                {
                    hangupLatch.countDown();
                }
            }
        });

        hangupLatch.await(CALL_ESTABLISH_TIMEOUT, TimeUnit.SECONDS);
        sipPeer.removeCallPeerListener(callPeerListener);

        Iterator<? extends CallPeer> peerIter = call.getCallPeers();
        while (peerIter.hasNext())
        {
            try
            {
                tele.hangupCallPeer(peerIter.next());
            }
            catch (Throwable t){}
        }

        if (receivedBuffer[0] == null ||  receivedBuffer[0] != true)
        {
            logger.error("Outgoing health check failed. " + (debugEnabled ? getThreadDumb() : ""));

            String sessionInfo = (String)call.getData("X-session-info");

            throw new Exception("Health check call failed with no media! "
                + (sessionInfo != null ? "Session info:" + sessionInfo: ""));
        }
    }

    private static String getThreadDumb()
    {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(
            threadMXBean.getAllThreadIds(), 100);
        StringBuilder dbg = new StringBuilder();
        for (ThreadInfo threadInfo : threadInfos)
        {
            if (threadInfo == null)
            {
                continue;
            }
            dbg.append('"').append(threadInfo.getThreadName()).append('"');

            Thread.State state = threadInfo.getThreadState();
            dbg.append("\n   java.lang.Thread.State: ").append(state);

            if (threadInfo.getLockName() != null)
            {
                dbg.append(" on ").append(threadInfo.getLockName());
            }
            dbg.append('\n');

            StackTraceElement[] stackTraceElements
                = threadInfo.getStackTrace();
            for (int i = 0; i < stackTraceElements.length; i++)
            {
                StackTraceElement ste = stackTraceElements[i];
                dbg.append("\tat " + ste.toString());
                dbg.append('\n');
                if (i == 0 && threadInfo.getLockInfo() != null)
                {
                    Thread.State ts = threadInfo.getThreadState();
                    if (ts == Thread.State.BLOCKED
                        || ts == Thread.State.WAITING
                        || ts == Thread.State.TIMED_WAITING)
                    {
                        dbg.append("\t-  " + ts + " on "
                            + threadInfo.getLockInfo());
                        dbg.append('\n');
                    }
                }

                for (MonitorInfo mi
                    : threadInfo.getLockedMonitors())
                {
                    if (mi.getLockedStackDepth() == i)
                    {
                        dbg.append("\t-  locked " + mi);
                        dbg.append('\n');
                    }
                }
            }
            dbg.append("\n\n");
        }

        return dbg.toString();
    }
}
