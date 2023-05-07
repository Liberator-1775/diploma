package org.diploma.transcription;

import com.fasterxml.uuid.*;
import com.google.api.gax.rpc.*;
import com.google.auth.oauth2.*;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.*;
import com.timgroup.statsd.*;
import org.diploma.JigasiBundleActivator;
import org.diploma.transcription.action.ActionServicesHandler;
import org.diploma.*;
import org.diploma.transcription.action.*;
import org.jitsi.utils.logging.*;

import javax.media.format.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class GoogleCloudTranscriptionService
    implements TranscriptionService
{
    public final static String[] SUPPORTED_LANGUAGE_TAGS = new String[]
        {
            "af-ZA",
            "id-ID",
            "ms-MY",
            "ca-ES",
            "cs-CZ",
            "da-DK",
            "de-DE",
            "en-AU",
            "en-CA",
            "en-GB",
            "en-IN",
            "en-IE",
            "en-NZ",
            "en-PH",
            "en-ZA",
            "en-US",
            "es-AR",
            "es-BO",
            "es-CL",
            "es-CO",
            "es-CR",
            "es-EC",
            "es-SV",
            "es-ES",
            "es-US",
            "es-GT",
            "es-HN",
            "es-MX",
            "es-NI",
            "es-PA",
            "es-PY",
            "es-PE",
            "es-PR",
            "es-DO",
            "es-UY",
            "es-VE",
            "eu-ES",
            "fil-PH",
            "fr-CA",
            "fr-FR",
            "gl-ES",
            "hr-HR",
            "zu-ZA",
            "is-IS",
            "it-IT",
            "lt-LT",
            "hu-HU",
            "nl-NL",
            "nb-NO",
            "pl-PL",
            "pt-BR",
            "pt-PT",
            "ro-RO",
            "sk-SK",
            "sl-SI",
            "fi-FI",
            "sv-SE",
            "vi-VN",
            "tr-TR",
            "el-GR",
            "bg-BG",
            "ru-RU",
            "sr-RS",
            "uk-UA",
            "he-IL",
            "ar-IL",
            "ar-JO",
            "ar-AE",
            "ar-BH",
            "ar-DZ",
            "ar-SA",
            "ar-IQ",
            "ar-KW",
            "ar-MA",
            "ar-TN",
            "ar-OM",
            "ar-PS",
            "ar-QA",
            "ar-LB",
            "ar-EG",
            "fa-IR",
            "hi-IN",
            "th-TH",
            "ko-KR",
            "zh-TW",
            "ja-JP",
            "zh",
        };

    private final static Logger logger
            = Logger.getLogger(GoogleCloudTranscriptionService.class);

    private final static int MAXIMUM_DESIRED_ALTERNATIVES = 0;

    private final static boolean RETRIEVE_INTERIM_RESULTS = true;

    private final static boolean SINGLE_UTTERANCE_ONLY = true;

    private final static int STREAMING_SESSION_TIMEOUT_MS = 2000;

    private final static String P_NAME_USE_VIDEO_MODEL
        = "org.diploma.transcription.USE_VIDEO_MODEL";

    private final static boolean DEFAULT_VALUE_USE_VIDEO_MODEL = false;

    private static void validateLanguageTag(String tag)
        throws UnsupportedOperationException
    {
        for (String supportedTag : SUPPORTED_LANGUAGE_TAGS)
        {
            if (supportedTag.equals(tag))
            {
                return;
            }
        }
        throw new UnsupportedOperationException(tag + " is not a language " +
                                                    "supported by the Google " +
                                                    "Cloud speech-to-text API");
    }

    public boolean supportsLanguageRouting()
    {
        return false;
    }

    private List<SpeechContext> speechContexts = null;

    private boolean useVideoModel;

    private RecognitionConfig getRecognitionConfig(TranscriptionRequest request)
        throws UnsupportedOperationException
    {
        RecognitionConfig.Builder builder = RecognitionConfig.newBuilder();

        AudioFormat format = request.getFormat();
        builder.setSampleRateHertz(Double.valueOf(format.getSampleRate()).intValue());
        switch(format.getEncoding())
        {
            case "LINEAR":
                builder.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16);
                break;
            default:
                throw new IllegalArgumentException("Given AudioFormat" +
                    "has unexpected" +
                    "encoding");
        }

        if (useVideoModel)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Using the more expensive video model");
            }
            builder.setModel("video");
        }

        String languageTag = request.getLocale().toLanguageTag();
        validateLanguageTag(languageTag);
        builder.setLanguageCode(languageTag);

        addSpeechContexts(builder);

        builder.setMaxAlternatives(MAXIMUM_DESIRED_ALTERNATIVES);

        return builder.build();
    }

    public GoogleCloudTranscriptionService()
    {
        useVideoModel = JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_USE_VIDEO_MODEL, DEFAULT_VALUE_USE_VIDEO_MODEL);
    }

    public boolean isConfiguredProperly()
    {
        try
        {
            GoogleCredentials.getApplicationDefault();
            return true;
        }
        catch (IOException e)
        {
            logger.warn("Google Credentials are not properly set", e);
            return false;
        }
    }

    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                            final Consumer<TranscriptionResult> resultConsumer)
    {
        try
        {
            Instant timeRequestReceived = Instant.now();
            SpeechClient client = SpeechClient.create();

            RecognitionConfig config = getRecognitionConfig(request);

            ByteString audioBytes = ByteString.copyFrom(request.getAudio());
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build();

            RecognizeResponse recognizeResponse =
                    client.recognize(config, audio);

            client.close();

            StringBuilder builder = new StringBuilder();
            for (SpeechRecognitionResult result :
                    recognizeResponse.getResultsList())
            {
                builder.append(result.toString());
                builder.append(" ");
            }

            String transcription = builder.toString().trim();
            resultConsumer.accept(
                    new TranscriptionResult(
                            null,
                            Generators.timeBasedReorderedGenerator().generate(),
                            timeRequestReceived,
                            false,
                            request.getLocale().toLanguageTag(),
                            0,
                            new TranscriptionAlternative(transcription)));
        }
        catch (Exception e)
        {
            logger.error("Error sending single req", e);
        }
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(
            Participant participant)
        throws UnsupportedOperationException
    {
        return new GoogleCloudStreamingRecognitionSession(participant.getDebugName());
    }

    @Override
    public boolean supportsFragmentTranscription()
    {
        return true;
    }

    @Override
    public boolean supportsStreamRecognition()
    {
        return true;
    }

    private void addSpeechContexts(RecognitionConfig.Builder builder)
    {
        if (speechContexts == null)
        {
            speechContexts = new ArrayList<>();
            ActionServicesHandler.getInstance().getPhrases()
                .stream().map(ph -> speechContexts.add(
                    SpeechContext.newBuilder().addPhrases(ph).build()));
        }

        speechContexts.stream().map(ctx -> builder.addSpeechContexts(ctx));
    }

    public class GoogleCloudStreamingRecognitionSession
        implements StreamingRecognitionSession
    {
        private SpeechClient client;

        private final String debugName;

        private RequestApiStreamObserverManager requestManager;

        private ExecutorService service = Executors.newSingleThreadExecutor();

        private GoogleCloudStreamingRecognitionSession(String debugName)
        {
            this.debugName = debugName;

            try
            {
                this.client = SpeechClient.create();
                this.requestManager
                    = new RequestApiStreamObserverManager(client, debugName);
            }
            catch(Exception e)
            {
                logger.error(debugName + ": error creating stream observer", e);
            }
        }

        @Override
        public void sendRequest(final TranscriptionRequest request)
        {
            this.service.execute(() -> {
                try
                {
                    requestManager.sentRequest(request);
                }
                catch(Exception e)
                {
                    logger.warn(debugName + ": not able to send request", e);
                }
            });
            logger.trace(debugName + ": queued request");
        }

        @Override
        public boolean ended()
        {
            return service.isShutdown();
        }

        @Override
        public void end()
        {
            try
            {
                client.close();
                requestManager.stop();
                service.shutdown();
            }
            catch(Exception e)
            {
                logger.error(debugName + ": error ending session", e);
            }
        }

        @Override
        public void addTranscriptionListener(TranscriptionListener listener)
        {
            requestManager.addListener(listener);
        }
    }

    private static class GoogleCloudCostLogger
    {
        private final static int INTERVAL_LENGTH_MS = 15000;

        private final static String ASPECT_INTERVAL
            = "google_cloud_speech_15s_intervals";

        private final static String ASPECT_TOTAL_REQUESTS
            = "google_cloud_speech_requests";

        private final StatsDClient client = JigasiBundleActivator.getDataDogClient();

        private final String debugName;

        private long requestsCount = 0;

        private long summedTime = 0;

        GoogleCloudCostLogger(String debugName)
        {
            this.debugName = debugName;
        }

        synchronized void increment(long ms)
        {
            if (ms < 0)
            {
                return;
            }

            summedTime += ms;
        }

        synchronized void incrementRequestsCounter()
        {
            requestsCount += 1;
        }

        synchronized void sessionEnded()
        {
            int intervals15s = 1 + (int) (summedTime  / INTERVAL_LENGTH_MS);

            if (client != null)
            {
                client.count(ASPECT_INTERVAL, intervals15s);
                client.count(ASPECT_TOTAL_REQUESTS, requestsCount);
            }

            logger.info(debugName + ": sent " + summedTime + "ms to speech API, " +
                            "for a total of " + intervals15s + " intervals " +
                            "with a total of " + requestsCount + " requests.");

            summedTime = 0;
            requestsCount = 0;
        }

    }

    private class RequestApiStreamObserverManager
    {
        private SpeechClient client;

        private final String debugName;

        private final List<TranscriptionListener> listeners = new ArrayList<>();

        private ApiStreamObserver<StreamingRecognizeRequest>
            currentRequestObserver;

        private final Object currentRequestObserverLock = new Object();

        private TerminatingSessionThread terminatingSessionThread;

        private boolean stopped = false;

        private final GoogleCloudCostLogger costLogger;

        RequestApiStreamObserverManager(SpeechClient client, String debugName)
        {
            this.client = client;
            this.debugName = debugName;
            this.costLogger = new GoogleCloudCostLogger(debugName);
        }

        private ApiStreamObserver<StreamingRecognizeRequest> createObserver(
            RecognitionConfig config)
        {
            ResponseApiStreamingObserver<StreamingRecognizeResponse>
                responseObserver =
                new ResponseApiStreamingObserver<StreamingRecognizeResponse>(
                    this,
                    config.getLanguageCode(),
                    debugName);

            StreamingRecognitionConfig streamingRecognitionConfig =
                StreamingRecognitionConfig.newBuilder()
                    .setConfig(config)
                    .setInterimResults(RETRIEVE_INTERIM_RESULTS)
                    .setSingleUtterance(!useVideoModel &&
                                            SINGLE_UTTERANCE_ONLY)
                    .build();

            BidiStreamingCallable<StreamingRecognizeRequest,
                StreamingRecognizeResponse> callable = client
                .streamingRecognizeCallable();

            ApiStreamObserver<StreamingRecognizeRequest> requestObserver
                = callable.bidiStreamingCall(responseObserver);

            requestObserver.onNext(
                StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingRecognitionConfig)
                    .build());

            terminatingSessionThread
                = new TerminatingSessionThread(this,
                                                STREAMING_SESSION_TIMEOUT_MS);
            terminatingSessionThread.start();

            return requestObserver;
        }

        void sentRequest(TranscriptionRequest request)
        {
            if (stopped)
            {
                logger.warn(debugName + ": not able to send request because" +
                    " manager was already stopped");
                return;
            }

            byte[] audio = request.getAudio();
            ByteString audioBytes = ByteString.copyFrom(audio);

            synchronized(currentRequestObserverLock)
            {
                if (currentRequestObserver == null)
                {
                    if (logger.isDebugEnabled())
                        logger.debug(debugName + ": created a new session");

                    currentRequestObserver
                        = createObserver(getRecognitionConfig(request));
                }

                costLogger.increment(request.getDurationInMs());
                costLogger.incrementRequestsCounter();

                currentRequestObserver.onNext(
                    StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(audioBytes)
                        .build());

                terminatingSessionThread.interrupt();
            }
            logger.trace(debugName + ": sent a request");
        }

        void addListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        public List<TranscriptionListener> getListeners()
        {
            return listeners;
        }

        public void stop()
        {
            stopped = true;
            terminateCurrentSession();
        }

        void terminateCurrentSession()
        {
            synchronized(currentRequestObserverLock)
            {
                if (currentRequestObserver != null)
                {
                    if (logger.isDebugEnabled())
                        logger.debug(debugName + ": terminated current session");

                    currentRequestObserver.onCompleted();
                    currentRequestObserver = null;

                    costLogger.sessionEnded();
                }

                if (terminatingSessionThread != null &&
                    terminatingSessionThread.isAlive())
                {
                    terminatingSessionThread.setStopIfInterrupted(true);
                    terminatingSessionThread.interrupt();
                    terminatingSessionThread = null;
                }
            }
        }
    }

    private static class ResponseApiStreamingObserver
        <T extends StreamingRecognizeResponse>
        implements ApiStreamObserver<T>
    {
        private String debugName;

        private RequestApiStreamObserverManager requestManager;

        private String languageTag;

        private final UUID messageID;

        final private Instant timeStamp;

        private String latestTranscript = "";

        ResponseApiStreamingObserver(RequestApiStreamObserverManager manager,
                                     String languageTag,
                                     String debugName)
        {
            this.requestManager = manager;
            this.languageTag = languageTag;
            this.debugName = debugName;
            this.messageID = Generators.timeBasedReorderedGenerator().generate();
            this.timeStamp = Instant.now();
        }

        @Override
        public void onNext(StreamingRecognizeResponse message)
        {
            if (logger.isDebugEnabled())
                logger.debug(debugName + ": received a StreamingRecognizeResponse");
            if (message.hasError())
            {
                if (logger.isDebugEnabled())
                    logger.debug(
                        debugName + ": received error from StreamingRecognizeResponse: "
                        + message.getError().getMessage());
                requestManager.terminateCurrentSession();
                return;
            }

            if (isEndOfSingleUtteranceMessage(message) ||
                message.getResultsCount() == 0)
            {
                if (logger.isDebugEnabled())
                    logger.debug(
                        debugName + ": received a message with an empty results list");

                requestManager.terminateCurrentSession();
                return;
            }

            List<StreamingRecognitionResult> results = message.getResultsList();
            StreamingRecognitionResult result = results.get(0);

            if (result.getAlternativesList().isEmpty())
            {
                logger.warn(
                    debugName + ": received a list of alternatives which"
                              + " was empty");
                requestManager.terminateCurrentSession();
                return;
            }

            if (result.getIsFinal())
            {
                handleResult(result);
                requestManager.terminateCurrentSession();
            }
            else
            {
                if (result.getStability() > 0.8)
                {
                    handleResult(result);
                }
                else if (logger.isDebugEnabled())
                {
                    logger.debug(
                            debugName + " dropping unstable results: "
                                    + result.getStability());
                }
            }
        }

        private boolean isEndOfSingleUtteranceMessage(
            StreamingRecognizeResponse message)
        {
            return message.getSpeechEventType().
                equals(StreamingRecognizeResponse.SpeechEventType.
                    END_OF_SINGLE_UTTERANCE);
        }

        private void handleResult(StreamingRecognitionResult result)
        {
            if (result.getAlternativesList().isEmpty())
            {
                throw new IllegalArgumentException(
                        "The alternatives list must not be empty");
            }

            SpeechRecognitionAlternative alternative = result.getAlternatives(0);
            String newTranscript = alternative.getTranscript();

            if (this.latestTranscript.equals(newTranscript) &&
                (!result.getIsFinal() || newTranscript.length() == 0))
            {
                if  (logger.isDebugEnabled())
                {
                    logger.debug(
                            debugName + ": dropping result without any change to"
                                    + " the stable part");
                }

                return;
            }

            this.latestTranscript = newTranscript;

            TranscriptionResult transcriptionResult = new TranscriptionResult(
                null,
                this.messageID,
                this.timeStamp,
                !result.getIsFinal(),
                this.languageTag,
                result.getStability(),
                new TranscriptionAlternative(
                        newTranscript,
                        alternative.getConfidence()
                ));

            sent(transcriptionResult);
        }

        @Override
        public void onError(Throwable t)
        {
            logger.warn(debugName + ": received an error from the Google Cloud API", t);

            if (t instanceof ResourceExhaustedException)
            {
                for (TranscriptionListener l : requestManager.getListeners())
                {
                    l.failed(TranscriptionListener.FailureReason.RESOURCES_EXHAUSTED);
                }

                requestManager.stop();
            }
            else
            {
                requestManager.terminateCurrentSession();
            }
        }

        @Override
        public void onCompleted()
        {
            for (TranscriptionListener listener : requestManager.getListeners())
            {
                listener.completed();
            }
        }

        private void sent(TranscriptionResult result)
        {
            for (TranscriptionListener listener : requestManager.getListeners())
            {
                listener.notify(result);
            }

            if (!result.isInterim())
            {
                ActionServicesHandler.getInstance()
                    .notifyActionServices(result);
            }
        }
    }

    private static class TerminatingSessionThread
        extends Thread
    {

        private RequestApiStreamObserverManager manager;

        private int terminateAfter;

        private boolean stopIfInterrupted = false;

        TerminatingSessionThread(RequestApiStreamObserverManager manager,
                                        int ms)
        {
            this.manager = manager;
            this.terminateAfter = ms;
        }

        @Override
        public void run()
        {
            try
            {
                sleep(terminateAfter);
                manager.terminateCurrentSession();
            }
            catch(InterruptedException e)
            {
                if (!stopIfInterrupted)
                {
                    run();
                }
            }
        }

        void setStopIfInterrupted(boolean stopIfInterrupted)
        {
            this.stopIfInterrupted = stopIfInterrupted;
        }
    }
}
