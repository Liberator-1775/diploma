package org.diploma.transcription;

import net.java.sip.communicator.service.protocol.*;

public interface TranscriptionResultPublisher
{
    void publish(ChatRoom chatRoom, TranscriptionResult result);

    void publish(ChatRoom chatRoom, TranslationResult result);
}
