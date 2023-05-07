package org.diploma.sounds;

import net.java.sip.communicator.service.protocol.*;
import org.diploma.CallContext;
import org.diploma.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jitsi.utils.logging.Logger;

class PlaybackQueue
    extends Thread
{
    private final static Logger logger = Logger.getLogger(PlaybackQueue.class);

    public interface PlaybackDelegate
    {
        void onPlaybackFinished();
    }

    private static class PlaybackData
    {
        private String playbackFileName;

        private PlaybackDelegate playbackDelegate;

        private Call playbackCall;

        public PlaybackData(String fileName,
                            PlaybackDelegate delegate,
                            Call call)
        {
            playbackFileName = fileName;
            playbackDelegate = delegate;
            playbackCall = call;
        }

        String getPlaybackFileName() { return playbackFileName; }

        PlaybackDelegate getPlaybackDelegate() { return playbackDelegate; }

        Call getPlaybackCall() { return playbackCall; }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (o != null && o instanceof String)
            {
                return playbackFileName.equals(o);
            }

            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            PlaybackData that = (PlaybackData) o;
            return playbackFileName.equals(that.playbackFileName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(playbackFileName, playbackDelegate, playbackCall);
        }
    }

    private final BlockingQueue<PlaybackData> playbackQueue = new ArrayBlockingQueue<>(100, true);

    private AtomicBoolean playbackQueueStopFlag = new AtomicBoolean(false);

    public void queueNext(Call call, String fileName) throws InterruptedException
    {
        this.queueNext(call, fileName, null);
    }

    public void queueNext(Call call,
                          String fileName,
                          PlaybackDelegate delegate)
        throws InterruptedException
    {
        if ((fileName.equals(SoundNotificationManager.PARTICIPANT_JOINED)
            || fileName.equals(SoundNotificationManager.PARTICIPANT_LEFT))
            && playbackQueue.contains(fileName))
        {
            return;
        }

        if (playbackQueue.remainingCapacity() == 0)
        {
            Object callContext = call.getData(CallContext.class);
            logger.warn(callContext + "Not playing sound to avoid blocking:" + fileName);
            return;
        }

        playbackQueue.put(new PlaybackData(fileName, delegate, call));
    }

    public void stopAtNextPlayback()
    {
        playbackQueue.clear();
        playbackQueueStopFlag.set(true);
        this.interrupt();
    }

    @Override
    public void run()
    {
        while (!playbackQueueStopFlag.get())
        {
            Call playbackCall = null;
            try
            {
                PlaybackData playbackData = playbackQueue.take();

                if (playbackData != null)
                {
                    playbackCall = playbackData.getPlaybackCall();

                    if (playbackCall != null)
                    {
                        SoundNotificationManager.injectSoundFile(playbackCall, playbackData.getPlaybackFileName());
                    }

                    final PlaybackDelegate playbackDelegate = playbackData.getPlaybackDelegate();
                    if (playbackDelegate != null)
                    {
                        playbackDelegate.onPlaybackFinished();
                    }
                }
            }
            catch(InterruptedException ex)
            {

            }
            catch (Exception ex)
            {
                if (playbackCall != null)
                {
                    Object callContext = playbackCall.getData(CallContext.class);
                    logger.error(callContext + " " + ex, ex);
                }
                else
                {
                    logger.error(ex);
                }
            }
        }
    }
}
