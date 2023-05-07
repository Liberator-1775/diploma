package org.diploma.transcription;

import net.java.sip.communicator.service.protocol.*;
import org.diploma.JigasiBundleActivator;
import org.diploma.*;

import java.util.*;

public class TranscriptHandler
{
    public final static String P_NAME_SAVE_JSON
        = "org.diploma.transcription.SAVE_JSON";

    public final static String P_NAME_SAVE_TXT
        = "org.diploma.transcription.SAVE_TXT";

    public final static String P_NAME_SEND_JSON
        = "org.diploma.transcription.SEND_JSON";

    public final static String P_NAME_SEND_TXT
        = "org.diploma.transcription.SEND_TXT";

    public final static String P_NAME_SEND_JSON_REMOTE
        = "org.diploma.transcription.SEND_JSON_REMOTE_URLS";

    private final static boolean SAVE_JSON = false;

    private final static boolean SAVE_TXT = false;

    private final static boolean SEND_JSON = true;

    private final static boolean SEND_TXT = false;

    private List<TranscriptionResultPublisher> resultPublishers
        = new LinkedList<>();

    private List<TranscriptPublisher> transcriptPublishers = new LinkedList<>();

    public TranscriptHandler()
    {
        LocalJsonTranscriptHandler jsonHandler
            = new LocalJsonTranscriptHandler();
        LocalTxtTranscriptHandler txtHandler = new LocalTxtTranscriptHandler();

        if (getStoreInJson())
        {
            this.add((TranscriptPublisher) jsonHandler);
        }
        if (getStoreInTxt())
        {
            this.add((TranscriptPublisher) txtHandler);
        }
        if (getSendInJSON())
        {
            this.add((TranscriptionResultPublisher) jsonHandler);
        }
        if (getSendInTxt())
        {
            this.add((TranscriptionResultPublisher) txtHandler);
        }
        String urls;
        if ((urls = getSendJSONToRemote()) != null)
        {
            this.add((TranscriptionResultPublisher)
                new RemotePublisherTranscriptionHandler(urls));
        }
    }

    public void publishTranscriptionResult(ChatRoom room,
                                           TranscriptionResult result)
    {
        for (TranscriptionResultPublisher p : resultPublishers)
        {
            p.publish(room, result);
        }
    }

    public void publishTranslationResult(ChatRoom room,
                                         TranslationResult result)
    {
        for (TranscriptionResultPublisher p : resultPublishers)
        {
            p.publish(room, result);
        }
    }

    public List<TranscriptPublisher.Promise> getTranscriptPublishPromises()
    {
        List<TranscriptPublisher.Promise> promises = new LinkedList<>();
        for (TranscriptPublisher p : transcriptPublishers)
        {
            promises.add(p.getPublishPromise());
        }

        return promises;
    }

    public List<TranscriptionResultPublisher> getTranscriptResultPublishers()
    {
        return resultPublishers;
    }

    public void add(TranscriptPublisher publisher)
    {
        transcriptPublishers.add(publisher);
    }

    public void remove(TranscriptPublisher publisher)
    {
        transcriptPublishers.remove(publisher);
    }

    public void add(TranscriptionResultPublisher publisher)
    {
        resultPublishers.add(publisher);
    }

    public void remove(TranscriptionResultPublisher publisher)
    {
        resultPublishers.remove(publisher);
    }

    private boolean getSendInJSON()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SEND_JSON, SEND_JSON);
    }

    private boolean getSendInTxt()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SEND_TXT, SEND_TXT);
    }

    private String getSendJSONToRemote()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_SEND_JSON_REMOTE);
    }

    private boolean getStoreInJson()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SAVE_JSON, SAVE_JSON);
    }

    private boolean getStoreInTxt()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SAVE_TXT, SAVE_TXT);
    }
}
