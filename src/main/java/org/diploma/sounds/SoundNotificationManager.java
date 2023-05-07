package org.diploma.sounds;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.diploma.CallContext;
import org.diploma.CallManager;
import org.diploma.SipGatewaySession;
import org.diploma.util.Util;
import org.gagravarr.ogg.*;
import org.gagravarr.opus.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.diploma.*;
import org.diploma.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.jibri.*;
import org.jivesoftware.smack.packet.*;

import java.util.*;
import java.util.concurrent.*;

public class SoundNotificationManager
{
    private final static Logger logger = Logger.getLogger(
        SoundNotificationManager.class);

    private static final String REC_ON_SOUND = "sounds/RecordingOn.opus";

    private static final String REC_OFF_SOUND = "sounds/RecordingStopped.opus";

    private static final String LIVE_STREAMING_ON_SOUND
        = "sounds/LiveStreamingOn.opus";

    private static final String LIVE_STREAMING_OFF_SOUND
        = "sounds/LiveStreamingOff.opus";

    private static final String MAX_OCCUPANTS_SOUND
        = "sounds/MaxOccupants.opus";

    public static final String PARTICIPANT_ALONE = "sounds/Alone.opus";

    public static final String PARTICIPANT_LEFT = "sounds/ParticipantLeft.opus";

    public static final String PARTICIPANT_JOINED = "sounds/ParticipantJoined.opus";

    private static final String LOBBY_ACCESS_GRANTED = "sounds/LobbyAccessGranted.opus";

    private static final String LOBBY_ACCESS_DENIED = "sounds/LobbyAccessDenied.opus";

    private static final String LOBBY_MEETING_END = "sounds/LobbyMeetingEnd.opus";

    private static final String LOBBY_JOIN_REVIEW = "sounds/LobbyWait.opus";

    private static final int MAX_OCCUPANTS_SOUND_DURATION_SEC = 15;

    private JibriIq.Status currentJibriStatus = JibriIq.Status.OFF;

    private String currentJibriOnSound = null;

    private final SipGatewaySession gatewaySession;

    private boolean callMaxOccupantsLimitReached = false;

    private CountDownLatch hangupWait = null;

    private SoundRateLimiter participantLeftRateLimiterLazy = null;

    private SoundRateLimiter participantJoinedRateLimiterLazy = null;

    private SoundRateLimiter recordingOnRateLimiterLazy = null;

    private static final long PARTICIPANT_ALONE_TIMEOUT_MS = 15000;

    private static final long PARTICIPANT_LEFT_RATE_TIMEOUT_MS = 30000;

    private static final long RECORDING_ON_RATE_TIMEOUT_MS = 10000;

    private static final long PARTICIPANT_JOINED_RATE_TIMEOUT_MS = 30000;

    private Timer participantAloneNotificationTimerLazy = null;

    private TimerTask participantAloneNotificationTask = null;

    private final Object participantAloneNotificationSync = new Object();

    private PlaybackQueue playbackQueue = new PlaybackQueue();

    public SoundNotificationManager(SipGatewaySession gatewaySession)
    {
        this.gatewaySession = gatewaySession;
    }

    private CallContext getCallContext()
    {
        return this.gatewaySession.getCallContext();
    }

    public void process(Presence presence)
    {
        RecordingStatus rs = presence.getExtension(RecordingStatus.class);

        if (rs != null
            && gatewaySession.getFocusResourceAddr().equals(
                presence.getFrom().getResourceOrEmpty().toString()))
        {
            notifyRecordingStatusChanged(rs.getRecordingMode(), rs.getStatus());
        }
    }

    private void notifyRecordingStatusChanged(
        JibriIq.RecordingMode mode, JibriIq.Status status)
    {
        if (currentJibriStatus.equals(status))
        {
            return;
        }
        currentJibriStatus = status;

        String offSound;
        if (mode.equals(JibriIq.RecordingMode.FILE))
        {
            currentJibriOnSound = REC_ON_SOUND;
            offSound = REC_OFF_SOUND;
        }
        else if (mode.equals(JibriIq.RecordingMode.STREAM))
        {
            currentJibriOnSound = LIVE_STREAMING_ON_SOUND;
            offSound = LIVE_STREAMING_OFF_SOUND;
        }
        else
        {
            return;
        }

        try
        {
            if (JibriIq.Status.ON.equals(status) && !getRecordingOnRateLimiter().on())
            {
                playbackQueue.queueNext(gatewaySession.getSipCall(), currentJibriOnSound);
            }
            else if (JibriIq.Status.OFF.equals(status))
            {
                playbackQueue.queueNext(gatewaySession.getSipCall(), offSound);
            }
        }
        catch(InterruptedException ex)
        {
            logger.error(getCallContext() + " Error playing sound notification");
        }
    }

    public static void injectSoundFile(Call call, String fileName)
    {
        MediaStream stream = getMediaStream(call);

        if (stream == null
            || !call.getProtocolProvider().getAccountID().getAccountPropertyBoolean(
                    ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, false)
            || stream.getDynamicRTPPayloadType(Constants.OPUS) == -1
            || fileName == null)
        {
            return;
        }

        final MediaStream streamToPass = stream;

        try
        {
            injectSoundFileInStream(streamToPass, fileName);
        }
        catch (Throwable t)
        {
            logger.error(call.getData(CallContext.class) + " Error playing:" + fileName, t);
        }
    }

    static void injectSoundFileInStream(MediaStream stream, String fileName)
        throws Throwable
    {
        OpusFile of = new OpusFile(new OggPacketReader(
            Util.class.getClassLoader().getResourceAsStream(fileName)));

        OpusAudioData opusAudioData;
        int seq = new Random().nextInt(0xFFFF);
        long ts = new Random().nextInt(0xFFFF);
        long ssrc = new Random().nextInt(0xFFFF);
        byte pt = stream.getDynamicRTPPayloadType(Constants.OPUS);
        long timeForNextPacket = System.currentTimeMillis();
        long sentDuration = 0;

        while ((opusAudioData = of.getNextAudioPacket()) != null)
        {
            if (seq > AbstractCodec2.SEQUENCE_MAX)
            {
                seq = 0;
            }

            int nSamples = opusAudioData.getNumberOfSamples();
            ts += nSamples;
            if (ts > TimestampUtils.MAX_TIMESTAMP_VALUE)
            {
                ts = ts - TimestampUtils.MAX_TIMESTAMP_VALUE;
            }

            byte[] data = opusAudioData.getData();
            RawPacket rtp = Util.makeRTP(
                ssrc,
                pt,
                seq++,
                ts,
                data.length + RawPacket.FIXED_HEADER_SIZE
            );
            rtp.setSkipStats(true);

            System.arraycopy(
                data, 0, rtp.getBuffer(), rtp.getPayloadOffset(), data.length);
            int duration = nSamples/48;
            timeForNextPacket += duration;
            sentDuration += duration;
            if (stream instanceof MediaStreamImpl)
            {
                ((MediaStreamImpl)stream).injectPacket(rtp, true, null, true);
            }
            else
            {
                stream.injectPacket(rtp, true, null);
            }

            long sleep = timeForNextPacket - System.currentTimeMillis();
            if (sleep > 0 && sentDuration > 200)
            {
                Thread.sleep(sleep);
            }
        }
    }

    public void process(CallPeerState callPeerState)
    {
        long delayedHangupSeconds = -1;

        if (CallPeerState.BUSY.equals(callPeerState))
        {
            delayedHangupSeconds = 5 * 1000;
        }

        if (CallPeerState.CONNECTED.equals(callPeerState))
        {
            try
            {
                if (currentJibriStatus.equals(JibriIq.Status.ON) && !getRecordingOnRateLimiter().on())
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), currentJibriOnSound);
                }

                if (callMaxOccupantsLimitReached)
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), MAX_OCCUPANTS_SOUND);

                    delayedHangupSeconds = MAX_OCCUPANTS_SOUND_DURATION_SEC * 1000;
                }
                else
                {
                    playbackQueue.start();

                    playParticipantJoinedNotification();
                }
            }
            catch(InterruptedException ex)
            {
                logger.error(getCallContext() + " Error playing sound notification");
            }
        }
        else if (CallPeerState.DISCONNECTED.equals(callPeerState))
        {
            playbackQueue.stopAtNextPlayback();
        }

        if (delayedHangupSeconds != -1)
        {
            final long mills = delayedHangupSeconds;
            new Thread(() -> {
                try
                {
                    Thread.sleep(mills);
                }
                catch(InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                CallManager.hangupCall(gatewaySession.getSipCall());

                if (hangupWait != null)
                    hangupWait.countDown();
            }).start();
        }
    }

    public void stop()
    {
        this.playbackQueue.stopAtNextPlayback();
    }

    public void indicateMaxOccupantsLimitReached()
    {
        callMaxOccupantsLimitReached = true;

        hangupWait = new CountDownLatch(1);

        try
        {
            CallManager.acceptCall(gatewaySession.getSipCall());
        }
        catch(OperationFailedException e)
        {
            logger.error(getCallContext() + " Cannot answer call to play max occupants sound", e);
            return;
        }

        try
        {
            hangupWait.await(
                MAX_OCCUPANTS_SOUND_DURATION_SEC, TimeUnit.SECONDS);
        }
        catch(InterruptedException e)
        {
            logger.warn(getCallContext() + " Didn't finish waiting for hangup on max occupants");
        }
    }

    private void scheduleAloneNotification(long timeout)
    {
        synchronized(participantAloneNotificationSync)
        {
            this.cancelAloneNotification();

            this.participantAloneNotificationTask
                = new TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        playParticipantAloneNotification();
                    }
                    catch(Exception ex)
                    {
                        logger.error(getCallContext() + ex.getMessage(), ex);
                    }
                }
            };

            getParticipantAloneNotificationTimer().schedule(this.participantAloneNotificationTask, timeout);
        }
    }

    private void cancelAloneNotification()
    {
        synchronized(participantAloneNotificationSync)
        {
            if (this.participantAloneNotificationTask != null)
            {
                this.participantAloneNotificationTask.cancel();
            }
        }
    }

    public void onJvbCallEnded()
    {
        scheduleAloneNotification(0);

        if (this.participantJoinedRateLimiterLazy != null)
        {
            this.participantJoinedRateLimiterLazy.reset();
        }

        if (this.participantLeftRateLimiterLazy != null)
        {
            participantLeftRateLimiterLazy.reset();
        }

        if (this.recordingOnRateLimiterLazy != null)
        {
            this.recordingOnRateLimiterLazy.reset();
        }
    }

    public void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        boolean sendNotification = false;

        Call sipCall = gatewaySession.getSipCall();
        Call jvbCall = gatewaySession.getJvbCall();

        if (sipCall != null)
        {
            sendNotification
                = (sendNotification ||
                    sipCall.getCallState() == CallState.CALL_IN_PROGRESS);
        }

        if (jvbCall != null)
        {
            sendNotification
                = (sendNotification ||
                    jvbCall.getCallState() == CallState.CALL_IN_PROGRESS);
        }

        if (sendNotification)
        {
            playParticipantJoinedNotification();
        }

        this.cancelAloneNotification();
    }

    public void notifyChatRoomMemberLeft(ChatRoomMember member)
    {
        if (gatewaySession.getJvbConference().isStarted()
            && gatewaySession.getSipCall() != null)
        {
            playParticipantLeftNotification();
        }
    }

    public void notifyJvbRoomJoined()
    {
        int participantCount = gatewaySession.getParticipantsCount();

        if (participantCount <= 2)
        {
            scheduleAloneNotification(PARTICIPANT_ALONE_TIMEOUT_MS);
        }
    }

    private void playSoundFileIfPossible(String fileName)
    {
        try
        {
            if (gatewaySession.getSipCall() != null)
            {
                if (gatewaySession.getSipCall().getCallState()
                        != CallState.CALL_IN_PROGRESS)
                {
                    CallManager.acceptCall(gatewaySession.getSipCall());
                }

                if (fileName.equals(LOBBY_ACCESS_DENIED) || fileName.equals(LOBBY_MEETING_END))
                {
                        playbackQueue.queueNext(
                            gatewaySession.getSipCall(),
                            fileName,
                            () -> {
                                CallManager.hangupCall(gatewaySession.getSipCall());
                            });
                }
                else if (fileName.equals(LOBBY_JOIN_REVIEW))
                {
                    playbackQueue.queueNext(
                            gatewaySession.getSipCall(),
                            fileName,
                            () -> gatewaySession.notifyLobbyJoined());
                }
                else
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), fileName);
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(getCallContext() + " " + ex.toString(), ex);
        }
    }

    public void notifyLobbyWaitReview()
    {
        playSoundFileIfPossible(LOBBY_JOIN_REVIEW);
    }

    public void notifyLobbyAccessGranted()
    {
        playSoundFileIfPossible(LOBBY_ACCESS_GRANTED);
    }

    public void notifyLobbyAccessDenied()
    {
        playSoundFileIfPossible(LOBBY_ACCESS_DENIED);
    }

    public void notifyLobbyRoomDestroyed()
    {
        playSoundFileIfPossible(LOBBY_MEETING_END);
    }

    private void playParticipantAloneNotification()
    {
        try
        {
            Call sipCall = gatewaySession.getSipCall();

            if (sipCall != null)
            {
                playbackQueue.queueNext(sipCall, PARTICIPANT_ALONE);

                if (sipCall.getCallState() != CallState.CALL_IN_PROGRESS)
                {
                    CallManager.acceptCall(sipCall);
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(getCallContext() + " " + ex.getMessage(), ex);
        }
    }

    private void playParticipantLeftNotification()
    {
        try
        {
            if (!getParticipantLeftRateLimiter().on())
            {
                Call sipCall = gatewaySession.getSipCall();

                if (sipCall != null)
                {
                    playbackQueue.queueNext(sipCall, PARTICIPANT_LEFT);
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(getCallContext() + " " + ex.getMessage(), ex);
        }
    }

    private void playParticipantJoinedNotification()
    {
        try
        {
            this.cancelAloneNotification();

            if (!getParticipantJoinedRateLimiter().on())
            {
                Call sipCall = gatewaySession.getSipCall();

                if (sipCall != null)
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), PARTICIPANT_JOINED);
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(getCallContext() + " " + ex.getMessage());
        }
    }

    private Timer getParticipantAloneNotificationTimer()
    {
        if (this.participantAloneNotificationTimerLazy == null)
        {
            this.participantAloneNotificationTimerLazy
                = new Timer();
        }

        return participantAloneNotificationTimerLazy;
    }

    private SoundRateLimiter getParticipantLeftRateLimiter()
    {
        if (this.participantLeftRateLimiterLazy == null)
        {
            this.participantLeftRateLimiterLazy
                = new SoundRateLimiter(PARTICIPANT_LEFT_RATE_TIMEOUT_MS);
        }

        return this.participantLeftRateLimiterLazy;
    }

    private SoundRateLimiter getParticipantJoinedRateLimiter()
    {
        if (this.participantJoinedRateLimiterLazy == null)
        {
            this.participantJoinedRateLimiterLazy
                = new SoundRateLimiter(PARTICIPANT_JOINED_RATE_TIMEOUT_MS);
        }

        return this.participantJoinedRateLimiterLazy;
    }

    private SoundRateLimiter getRecordingOnRateLimiter()
    {
        if (this.recordingOnRateLimiterLazy == null)
        {
            this.recordingOnRateLimiterLazy = new SoundRateLimiter(RECORDING_ON_RATE_TIMEOUT_MS);
        }

        return this.recordingOnRateLimiterLazy;
    }

    static MediaStream getMediaStream(Call call)
    {
        CallPeer peer;
        if (call != null
            && call.getCallPeers() != null
            && call.getCallPeers().hasNext()
            && (peer = call.getCallPeers().next()) != null
            && peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler
                = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                return mediaHandler.getStream(MediaType.AUDIO);
            }
        }

        return null;
    }
}
