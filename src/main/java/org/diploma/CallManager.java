package org.diploma;

import net.java.sip.communicator.plugin.reconnectplugin.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.diploma.util.Util;
import org.diploma.util.*;
import org.jitsi.service.configuration.*;

import java.util.*;
import java.util.concurrent.*;
import org.jitsi.utils.logging.Logger;

public class CallManager
{
    private final static Logger logger = Logger.getLogger(CallManager.class);

    private static final String POOL_THREADS_PREFIX = "jigasi-callManager";

    private static ExecutorService threadPool = Util.createNewThreadPool(POOL_THREADS_PREFIX);

    private static boolean healthy = true;

    public static boolean isHealthy()
    {
        return healthy;
    }

    private static Future<?> submit(Runnable task)
    {
        try
        {
            return threadPool.submit(task);
        }
        catch(RejectedExecutionException e)
        {
            logger.error("Failed to submit task for execution:" + task, e);

            CallManager.healthy = false;

            return null;
        }
    }

    public synchronized static void acceptCall(Call incomingCall)
        throws OperationFailedException
    {
        Future result = submit(new AnswerCallThread(incomingCall, false));

        if (result == null)
        {
            throw new OperationFailedException(
                "Failed to answer",
                OperationFailedException.INTERNAL_SERVER_ERROR);
        }
    }

    public synchronized static void inviteToConferenceCall(
        Map<ProtocolProviderService, List<String>> callees,
        Call call)
    {
        submit(new InviteToConferenceCallThread(callees, call));
    }

    private static class InviteToConferenceCallThread
        implements Runnable
    {
        private final Map<ProtocolProviderService, List<String>>
            callees;

        private final Call call;

        public InviteToConferenceCallThread(
            Map<ProtocolProviderService, List<String>> callees,
            Call call)
        {
            this.callees = callees;
            this.call = call;
        }

        @Override
        public void run()
        {
            CallConference conference
                = (call == null) ? null : call.getConference();

            for (Map.Entry<ProtocolProviderService, List<String>> entry
                : callees.entrySet())
            {
                ProtocolProviderService pps = entry.getKey();

                if (pps != null)
                {
                    OperationSetBasicTelephony<?> basicTelephony
                        = pps.getOperationSet(OperationSetBasicTelephony.class);

                    if  (basicTelephony == null)
                        continue;
                }

                List<String> contactList = entry.getValue();
                String[] contactArray
                    = contactList.toArray(new String[contactList.size()]);

                Call ppsCall;

                if ((call != null) && call.getProtocolProvider().equals(pps))
                    ppsCall = call;
                else
                {
                    ppsCall = null;
                    if (conference != null)
                    {
                        List<Call> conferenceCalls = conference.getCalls();

                        if (pps == null)
                        {
                            if (call == null)
                            {
                                if (!conferenceCalls.isEmpty())
                                {
                                    ppsCall = conferenceCalls.get(0);
                                    pps = ppsCall.getProtocolProvider();
                                }
                            }
                            else
                            {
                                ppsCall = call;
                                pps = ppsCall.getProtocolProvider();
                            }
                        }
                        else
                        {
                            for (Call conferenceCall : conferenceCalls)
                            {
                                if (pps.equals(
                                    conferenceCall.getProtocolProvider()))
                                {
                                    ppsCall = conferenceCall;
                                    break;
                                }
                            }
                        }
                    }
                }

                OperationSetTelephonyConferencing telephonyConferencing
                    = pps.getOperationSet(
                            OperationSetTelephonyConferencing.class);

                try
                {
                    if (ppsCall == null)
                    {
                        ppsCall
                            = telephonyConferencing.createConfCall(
                            contactArray,
                            conference);
                        if (conference == null)
                            conference = ppsCall.getConference();
                    }
                    else
                    {
                        for (String contact : contactArray)
                        {
                            telephonyConferencing.inviteCalleeToCall(
                                contact,
                                ppsCall);
                        }
                    }
                }
                catch(Exception e)
                {
                    logger.error(
                        "Failed to invite callees: "
                            + Arrays.toString(contactArray),
                        e);
                }
            }
        }
    }

    private static class AnswerCallThread
        implements Runnable
    {
        private final Call call;

        private final boolean video;

        public AnswerCallThread(Call call, boolean video)
        {
            this.call = call;
            this.video = video;
        }

        @Override
        public void run()
        {
            ProtocolProviderService pps = call.getProtocolProvider();
            Iterator<? extends CallPeer> peers = call.getCallPeers();

            while (peers.hasNext())
            {
                CallPeer peer = peers.next();

                if (video)
                {
                    OperationSetVideoTelephony telephony
                        = pps.getOperationSet(OperationSetVideoTelephony.class);

                    try
                    {
                        telephony.answerVideoCallPeer(peer);
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.error(
                            "Could not answer " + peer + " with video"
                                + " because of the following exception: "
                                + ofe);
                    }
                }
                else
                {
                    OperationSetBasicTelephony<?> telephony
                        = pps.getOperationSet(OperationSetBasicTelephony.class);

                    try
                    {
                        telephony.answerCallPeer(peer);
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.error(
                            "Could not answer " + peer
                                + " because of the following exception: ",
                            ofe);
                    }
                }
            }
        }
    }

    public synchronized static void mergeExistingCalls(
        CallConference conference,
        Collection<Call> calls)
    {
        submit(new MergeExistingCalls(conference, calls));
    }

    private static class MergeExistingCalls
        implements Runnable
    {
        private final CallConference conference;

        private final Collection<Call> calls;

        public MergeExistingCalls(
            CallConference conference,
            Collection<Call> calls)
        {
            this.conference = conference;
            this.calls = calls;
        }

        private void putOffHold(Call call)
        {
            Iterator<? extends CallPeer> peers = call.getCallPeers();
            OperationSetBasicTelephony<?> telephony
                = call.getProtocolProvider().getOperationSet(
                OperationSetBasicTelephony.class);

            while (peers.hasNext())
            {
                CallPeer callPeer = peers.next();
                boolean putOffHold = true;

                if (callPeer instanceof MediaAwareCallPeer)
                {
                    putOffHold
                        = ((MediaAwareCallPeer<?, ?, ?>) callPeer)
                        .getMediaHandler()
                        .isLocallyOnHold();
                }
                if (putOffHold)
                {
                    try
                    {
                        telephony.putOffHold(callPeer);
                        Thread.sleep(400);
                    }
                    catch(Exception ofe)
                    {
                        logger.error("Failed to put off hold.", ofe);
                    }
                }
            }
        }

        @Override
        public void run()
        {
            for (Call call : conference.getCalls())
                putOffHold(call);

            if (!calls.isEmpty())
            {
                for (Call call : calls)
                {
                    if (conference.containsCall(call))
                        continue;

                    putOffHold(call);

                    call.setConference(conference);
                }
            }
        }
    }

    public synchronized static void hangupCall(Call call)
    {
        hangupCall(call, false);
    }

    public synchronized static void hangupCall(Call call, boolean unloadAccount)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Hanging up :" + call, new Throwable());
        }

        submit(new HangupCallThread(call, unloadAccount));
    }

    public synchronized static void hangupCall(Call   call,
                                               int    reasonCode,
                                               String reason)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Hanging up :" + call, new Throwable());
        }

        HangupCallThread hangupCallThread = new HangupCallThread(call, false);

        hangupCallThread.reasonCode = reasonCode;
        hangupCallThread.reason = reason;

        if (!healthy)
        {
            new Thread(hangupCallThread).start();
        }
        else
        {
            submit(hangupCallThread);
        }
    }

    private static class HangupCallThread
        implements Runnable
    {
        private final static Logger logger
            = Logger.getLogger(HangupCallThread.class);

        private final Call call;

        private final CallConference conference;

        private final CallPeer peer;

        private final boolean unloadAccount;

        private int reasonCode
            = OperationSetBasicTelephony.HANGUP_REASON_NORMAL_CLEARING;

        private String reason = null;

        public HangupCallThread(Call call, boolean unloadAccount)
        {
            this(call, null, null, unloadAccount);
        }

        private HangupCallThread(
            Call call,
            CallConference conference,
            CallPeer peer,
            boolean unloadAccount)
        {
            this.call = call;
            this.conference = conference;
            this.peer = peer;
            this.unloadAccount = unloadAccount;
        }

        @Override
        public void run()
        {
            Set<CallPeer> peers = new HashSet<CallPeer>();

            if (call != null)
            {
                Iterator<? extends CallPeer> peerIter = call.getCallPeers();

                while (peerIter.hasNext())
                    peers.add(peerIter.next());
            }

            if (conference != null)
                peers.addAll(conference.getCallPeers());

            if (peer != null)
                peers.add(peer);

            for (CallPeer peer : peers)
            {
                OperationSetBasicTelephony<?> basicTelephony
                    = peer.getProtocolProvider().getOperationSet(
                    OperationSetBasicTelephony.class);

                try
                {
                    basicTelephony.hangupCallPeer(peer, reasonCode, reason);
                }
                catch (Exception ofe)
                {
                    logger.error("Could not hang up: " + peer, ofe);
                }
            }

            if (this.unloadAccount)
            {
                ProtocolProviderService pp = call.getProtocolProvider();

                try
                {
                    pp.unregister(true);
                }
                catch(OperationFailedException e)
                {
                    logger.error("Cannot unregister");
                }

                ProtocolProviderFactory factory = ProtocolProviderFactory.getProtocolProviderFactory(
                    JigasiBundleActivator.osgiContext, pp.getProtocolName());

                if (factory != null)
                {
                    logger.info(
                        call.getData(CallContext.class)
                            + " Removing account " + pp.getAccountID());

                    factory.unloadAccount(pp.getAccountID());

                    ConfigurationService config = JigasiBundleActivator.getConfigurationService();
                    config.removeProperty(ReconnectPluginActivator.ATLEAST_ONE_CONNECTION_PROP
                        + "." + pp.getAccountID().getAccountUniqueID());
                }
            }
        }
    }

    public static synchronized void restartPool()
        throws InterruptedException, TimeoutException
    {
        threadPool.shutdown();

        threadPool.awaitTermination(5, TimeUnit.SECONDS);

        if (!threadPool.isTerminated())
            throw new TimeoutException();

        threadPool = Util.createNewThreadPool(POOL_THREADS_PREFIX);
    }
}
