package org.diploma.transcription;

import javax.media.format.*;
import java.util.*;
import java.util.concurrent.*;

public class TranscriptionRequest
{
    private byte[] audio;

    private AudioFormat format;

    private Locale locale;

    public TranscriptionRequest(byte[] audio, AudioFormat format,
                                Locale locale)
    {
        this.audio = audio;
        this.format = format;
        this.locale = locale;
    }

    public long getDurationInMs()
    {
        if (this.format == null)
        {
            return -1;
        }

        return TimeUnit.NANOSECONDS.toMillis(
            this.format.computeDuration(this.audio.length));
    }

    public byte[] getAudio()
    {
        return audio;
    }

    public AudioFormat getFormat()
    {
        return format;
    }

    public Locale getLocale()
    {
        return locale;
    }
}
