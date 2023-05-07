package org.diploma.transcription;

import com.timgroup.statsd.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.diploma.JigasiBundleActivator;
import org.diploma.transcription.action.ActionServicesHandler;
import org.jitsi.impl.neomedia.device.*;
import org.diploma.*;
import org.diploma.transcription.action.*;
import org.jitsi.utils.logging.*;
import org.jitsi.xmpp.extensions.jitsimeet.TranscriptionLanguageExtension;
import org.jitsi.xmpp.extensions.jitsimeet.TranslationLanguageExtension;
import org.jivesoftware.smack.packet.Presence;

import javax.media.Buffer;
import javax.media.rtp.*;
import java.util.*;
import java.util.concurrent.*;

public class Transcriber
    implements ReceiveStreamBufferListener
{
    private final static Logger logger = Logger.getLogger(Transcriber.class);

    private final static String DD_ASPECT_FAILED = "failed_transcriber";

    private final static String DD_ASPECT_START = "start_transcriber";

    private final static String DD_ASPECT_STOP = "stop_transcriber";

    public final static String P_NAME_ENABLE_TRANSLATION
        = "org.diploma.transcription.ENABLE_TRANSLATION";

    public final static boolean ENABLE_TRANSLATION_DEFAULT_VALUE = false;

    public final static String P_NAME_FILTER_SILENCE
        = "org.diploma.transcription.FILTER_SILENCE";

    public final static boolean FILTER_SILENCE_DEFAULT_VALUE = false;

    private enum State
    {
        NOT_STARTED,
        TRANSCRIBING,
        FINISHING_UP,
        FINISHED
    }

    private State state = State.NOT_STARTED;

    private final Map<String, org.diploma.transcription.Participant> participants = new HashMap<>();

    private Transcript transcript = new Transcript();

    private TranscribingAudioMixerMediaDevice mediaDevice
        = new TranscribingAudioMixerMediaDevice(this);

    private static final String CUSTOM_TRANSLATION_SERVICE_PROP
            = "org.diploma.transcription.translationService";

    private TranslationManager translationManager = null;

    private ArrayList<TranscriptionListener> listeners = new ArrayList<>();

    private ArrayList<TranscriptionEventListener> transcriptionEventListeners
        = new ArrayList<>();

    private TranscriptionService transcriptionService;

    ExecutorService executorService;

    private String roomName;

    private String roomUrl;

    private boolean filterSilence = shouldFilterSilence();

    public Transcriber(String roomName,
                       String roomUrl,
                       TranscriptionService service)
    {
        if (!service.supportsStreamRecognition())
        {
            throw new IllegalArgumentException(
                    "Currently only services which support streaming "
                    + "recognition are supported");
        }
        this.transcriptionService = service;
        addTranscriptionListener(this.transcript);

        configureTranslationManager();
        if (isTranslationEnabled())
        {
            addTranscriptionListener(this.translationManager);
        }
        this.roomName = roomName;
        this.roomUrl = roomUrl;
    }

    public Transcriber(TranscriptionService service)
    {
        this(null, null, service);
    }

    public void configureTranslationManager()
    {
        String customTranslationServiceClass = JigasiBundleActivator.getConfigurationService()
                .getString(CUSTOM_TRANSLATION_SERVICE_PROP, null);

        TranslationService translationService = null;
        if (customTranslationServiceClass != null)
        {
            try
            {
                translationService = (TranslationService)Class
                        .forName(customTranslationServiceClass)
                        .getDeclaredConstructor()
                        .newInstance();
            }
            catch(Exception e)
            {
                logger.error("Cannot instantiate custom translation service");
            }
        }

        if (translationService == null)
        {
            translationService = new GoogleCloudTranslationService();
        }

        translationManager = new TranslationManager(translationService);
    }

    private String getDebugName() {
        return roomName;
    }

    public void participantJoined(String identifier)
    {
        org.diploma.transcription.Participant participant = getParticipant(identifier);

        if (participant != null)
        {
            participant.joined();

            TranscriptEvent event = transcript.notifyJoined(participant);
            if (event != null)
            {
                fireTranscribeEvent(event);
            }

            if (logger.isDebugEnabled())
                logger.debug(
                    getDebugName()
                        + ": added participant with identifier " + identifier);

            return;
        }

        logger.warn(
            getDebugName() + ": participant with identifier " + identifier
                +  " joined while it did not exist");

    }

    public void maybeAddParticipant(String identifier)
    {
        synchronized (this.participants)
        {
            this.participants.computeIfAbsent(identifier,
                key -> new org.diploma.transcription.Participant(this, identifier, filterSilence));
        }
    }

    public void updateParticipant(String identifier,
                                  ChatRoomMember chatRoomMember)
    {
        maybeAddParticipant(identifier);

        org.diploma.transcription.Participant participant = getParticipant(identifier);
        if (participant != null)
        {
            participant.setChatMember(chatRoomMember);

            if (chatRoomMember instanceof ChatRoomMemberJabberImpl)
            {
                Presence presence = ((ChatRoomMemberJabberImpl) chatRoomMember).getLastPresence();

                TranscriptionLanguageExtension transcriptionLanguageExtension
                    = presence.getExtension(TranscriptionLanguageExtension.class);

                TranslationLanguageExtension translationLanguageExtension
                    = presence.getExtension(TranslationLanguageExtension.class);

                if (transcriptionLanguageExtension != null)
                {
                    String language
                        = transcriptionLanguageExtension.getTranscriptionLanguage();

                    this.updateParticipantSourceLanguage(identifier,
                        language);
                }

                if (translationLanguageExtension != null)
                {
                    String language
                        = translationLanguageExtension.getTranslationLanguage();

                    if (participant.getSourceLanguage() != null &&
                            !participant.getSourceLanguage().equals(language))
                    {
                        this.updateParticipantTargetLanguage(identifier, language);
                    }
                }
                else
                {
                    this.updateParticipantTargetLanguage(identifier, null);
                }
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": asked to set chatroom member of participant"
                    + " with identifier " + identifier
                    + " while it wasn't added before");
        }
    }

    public void updateParticipant(String identifier,
                                  ConferenceMember conferenceMember)
    {
        maybeAddParticipant(identifier);

        org.diploma.transcription.Participant participant = getParticipant(identifier);
        if (participant != null)
        {
            participant.setConfMember(conferenceMember);
        }
    }

    public void updateParticipantSourceLanguage(String identifier,
                                                String language)
    {
        org.diploma.transcription.Participant participant = getParticipant(identifier);

        if (participant != null)
        {
            participant.setSourceLanguage(language);
        }
    }

    public void updateParticipantTargetLanguage(String identifier,
                                                String language)
    {
        org.diploma.transcription.Participant participant = getParticipant(identifier);

        if (participant != null)
        {
            String previousLanguage = participant.getTranslationLanguage();

            translationManager.addLanguage(language);
            translationManager.removeLanguage(previousLanguage);
            participant.setTranslationLanguage(language);
        }
    }

    public void participantLeft(String identifier)
    {
        org.diploma.transcription.Participant participant;
        synchronized (this.participants)
        {
            participant = this.participants.remove(identifier);
        }

        if (participant != null)
        {
            translationManager.removeLanguage(
                participant.getTranslationLanguage());
            participant.left();
            TranscriptEvent event = transcript.notifyLeft(participant);
            if (event != null)
            {
                fireTranscribeEvent(event);
            }

            if (logger.isDebugEnabled())
            {
                logger.debug(
                    getDebugName() + ": removed participant with identifier "
                        + identifier);
            }

            return;
        }

        logger.warn(
            getDebugName() + ": participant with identifier "
                + identifier +  " left while it did not exist");
    }

    public void start()
    {
        if (State.NOT_STARTED.equals(this.state))
        {
            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": transcriber is now transcribing");

            updateDDClient(DD_ASPECT_START);

            this.state = State.TRANSCRIBING;
            this.executorService = Executors.newSingleThreadExecutor();

            TranscriptEvent event
                = this.transcript.started(roomName, roomUrl, getParticipants());
            if (event != null)
            {
                fireTranscribeEvent(event);
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": trying to start Transcriber while it is"
                    + " already started");
        }
    }

    public void stop(TranscriptionListener.FailureReason reason)
    {
        if (State.TRANSCRIBING.equals(this.state))
        {
            if (logger.isDebugEnabled())
                logger.debug(
                    getDebugName() + ": transcriber is now finishing up");

            updateDDClient(reason == null ? DD_ASPECT_STOP : DD_ASPECT_FAILED);

            this.state = reason == null ? State.FINISHING_UP : State.FINISHED;
            this.executorService.shutdown();

            TranscriptEvent event = this.transcript.ended();
            fireTranscribeEvent(event);
            ActionServicesHandler.getInstance()
                .notifyActionServices(this, event);

            if (reason == null)
            {
                checkIfFinishedUp();
            }
            else
            {
                for (TranscriptionListener listener : listeners)
                {
                    listener.failed(reason);
                }
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": trying to stop Transcriber while it is "
                    + " already stopped");
        }
    }

    private void updateDDClient(String ddAspectStop)
    {
        StatsDClient dClient = JigasiBundleActivator.getDataDogClient();
        if (dClient != null)
        {
            dClient.increment(ddAspectStop);
            if (logger.isDebugEnabled())
            {
                logger.debug(getDebugName() + " thrown stat: " + ddAspectStop);
            }
        }
    }

    public void willStop()
    {
        if (State.TRANSCRIBING.equals(this.state))
        {
            TranscriptEvent event = this.transcript.willEnd();
            fireTranscribeEvent(event);
            ActionServicesHandler.getInstance()
                .notifyActionServices(this, event);
        }
        else
        {
            logger.warn(
                getDebugName() + ": trying to notify Transcriber for a while"
                    + " it is already stopped");
        }
    }

    public boolean isTranscribing()
    {
        return State.TRANSCRIBING.equals(this.state);
    }

    public boolean finished()
    {
        return State.FINISHED.equals(this.state);
    }

    public boolean finishingUp()
    {
        return State.FINISHING_UP.equals(this.state);
    }

    public Transcript getTranscript()
    {
        return transcript;
    }

    public void addTranscriptionListener(TranscriptionListener listener)
    {
        listeners.add(listener);
    }

    public void addTranslationListener(TranslationResultListener listener)
    {
        translationManager.addListener(listener);
    }

    public void removeTranscriptionListener(TranscriptionListener listener)
    {
        listeners.remove(listener);
    }

    public void addTranscriptionEventListener(
        TranscriptionEventListener listener)
    {
        transcriptionEventListeners.add(listener);
    }

    public void removeTranscriptionEventListener(
        TranscriptionEventListener listener)
    {
        transcriptionEventListeners.remove(listener);
    }

    @Override
    public void bufferReceived(ReceiveStream receiveStream, Buffer buffer)
    {
        if (!isTranscribing())
        {
            logger.trace(
                getDebugName() + ": receiving audio while not transcribing");
            return;
        }

        long ssrc = receiveStream.getSSRC() & 0xffffffffL;

        org.diploma.transcription.Participant p = findParticipant(ssrc);

        if (p != null)
        {
            if (p.hasValidSourceLanguage())
            {
                logger.trace(getDebugName() + ": gave audio to buffer");
                p.giveBuffer(buffer);
            }
        }
        else
        {
            logger.warn(
                getDebugName() + ": reading from SSRC " + ssrc
                    + " while it is not known as a participant");
        }
    }

    private org.diploma.transcription.Participant findParticipant(long ssrc)
    {
        synchronized (this.participants)
        {
            for (org.diploma.transcription.Participant p : this.participants.values())
            {
                if (p.getSSRC() == ssrc)
                {
                    return p;
                }
            }

            return null;
        }
    }

    private org.diploma.transcription.Participant getParticipant(String identifier)
    {
        synchronized (this.participants)
        {
            return this.participants.get(identifier);
        }
    }

    public boolean isAnyParticipantRequestingTranscription()
    {

        return getParticipants()
            .stream()
            .anyMatch(org.diploma.transcription.Participant::isRequestingTranscription);
    }

    public List<org.diploma.transcription.Participant> getParticipants()
    {
        List<org.diploma.transcription.Participant> participantsCopy;
        synchronized (this.participants)
        {
            participantsCopy = new ArrayList<>(this.participants.values());
        }

        return participantsCopy;
    }

    public AudioMixerMediaDevice getMediaDevice()
    {
        return this.mediaDevice;
    }

    void checkIfFinishedUp()
    {
        if (State.FINISHING_UP.equals(this.state))
        {
            synchronized (this.participants)
            {
                for (org.diploma.transcription.Participant participant : participants.values())
                {
                    if (!participant.isCompleted())
                    {
                        logger.debug(
                            participant.getDebugName()
                                + " is still not finished");
                        return;
                    }
                }
            }

            if (logger.isDebugEnabled())
                logger.debug(getDebugName() + ": transcriber is now finished");

            this.state = State.FINISHED;
            for (TranscriptionListener listener : listeners)
            {
                listener.completed();
            }
        }
    }

    public TranscriptionService getTranscriptionService()
    {
        return transcriptionService;
    }

    void notify(TranscriptionResult result)
    {
        for (TranscriptionListener listener : listeners)
        {
            listener.notify(result);
        }
    }

    public String getRoomName()
    {
        return roomName;
    }

    public void setRoomName(String roomName)
    {
        this.roomName = roomName;
    }

    public String getRoomUrl()
    {
        return this.roomName;
    }

    public void setRoomUrl(String roomUrl)
    {
        this.roomUrl = roomUrl;
    }

    private void fireTranscribeEvent(TranscriptEvent event)
    {
        for (TranscriptionEventListener listener : transcriptionEventListeners)
        {
            listener.notify(this, event);
        }
    }

    private boolean isTranslationEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_TRANSLATION,
                    ENABLE_TRANSLATION_DEFAULT_VALUE);
    }

    private boolean shouldFilterSilence()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_FILTER_SILENCE, FILTER_SILENCE_DEFAULT_VALUE);
    }
}
