package org.diploma.transcription;

import org.jitsi.impl.neomedia.device.*;

public class TranscribingAudioMixerMediaDevice
    extends AudioMixerMediaDevice
{
    public TranscribingAudioMixerMediaDevice(
        ReceiveStreamBufferListener listener)
    {
        super(new AudioSilenceMediaDevice());
        super.setReceiveStreamBufferListener(listener);
    }
}
