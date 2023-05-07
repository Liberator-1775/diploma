package org.diploma.transcription;

import org.jitsi.service.neomedia.device.*;

public interface TranscriptPublisher
{
    Promise getPublishPromise();

    interface Promise
    {
        boolean hasDescription();

        String getDescription();

        void publish(Transcript transcript);

        void maybeStartRecording(MediaDevice device);
    }
}
