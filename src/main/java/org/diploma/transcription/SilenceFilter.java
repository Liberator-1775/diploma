package org.diploma.transcription;

import org.jitsi.webrtcvadwrapper.*;
import org.jitsi.webrtcvadwrapper.audio.*;

public class SilenceFilter
{
    private static final int VAD_MODE = 1;

    private static final int VAD_AUDIO_HZ = 48000;

    private static final int VAD_SEGMENT_SIZE_MS = 20;

    private static final int VAD_WINDOW_SIZE_MS = 200;

    private static final int VAD_THRESHOLD = 8;

    private SpeechDetector<ByteSignedPcmAudioSegment> speechDetector
        = new SpeechDetector<>(
            VAD_AUDIO_HZ,
            VAD_MODE,
            VAD_SEGMENT_SIZE_MS,
            VAD_WINDOW_SIZE_MS,
            VAD_THRESHOLD);

    private boolean previousSegmentWasSpeech;

    private boolean isCurrentlySpeech;

    public void giveSegment(byte[] audio)
    {
        ByteSignedPcmAudioSegment segment
            = new ByteSignedPcmAudioSegment(audio);

        speechDetector.nextSegment(segment);
        previousSegmentWasSpeech = isCurrentlySpeech;
        isCurrentlySpeech = speechDetector.isSpeech();
    }

    public byte[] getSpeechWindow()
    {
        return ByteSignedPcmAudioSegment
            .merge(speechDetector.getLatestSegments())
            .getAudio();
    }

    public boolean shouldFilter()
    {
        return !speechDetector.isSpeech();
    }

    public boolean newSpeech()
    {
        return !previousSegmentWasSpeech && isCurrentlySpeech;
    }
}
