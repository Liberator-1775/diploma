package org.diploma.transcription;

import org.diploma.JigasiBundleActivator;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.json.*;
import org.diploma.*;
import org.jitsi.utils.logging.*;

import javax.media.format.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class VoskTranscriptionService
    implements TranscriptionService
{
    private final static Logger logger
            = Logger.getLogger(VoskTranscriptionService.class);

    public final static String WEBSOCKET_URL
            = "org.diploma.transcription.vosk.websocket_url";

    public final static String DEFAULT_WEBSOCKET_URL = "ws://localhost:2700";

    private final static String EOF_MESSAGE = "{\"eof\" : 1}";

    private String websocketUrlConfig;

    private String websocketUrl;

    private void generateWebsocketUrl(Participant participant)
        throws org.json.simple.parser.ParseException
    {
        if (!supportsLanguageRouting())
        {
            websocketUrl = websocketUrlConfig;
            return;
        }

        org.json.simple.parser.JSONParser jsonParser = new org.json.simple.parser.JSONParser();
        Object obj = jsonParser.parse(websocketUrlConfig);
        org.json.simple.JSONObject languageMap = (org.json.simple.JSONObject) obj;
        String language = participant.getSourceLanguage() != null ? participant.getSourceLanguage() : "en";
        Object urlObject = languageMap.get(language);
        if (!(urlObject instanceof String))
        {
            logger.error("No websocket URL configured for language " + language);
            websocketUrl = null;
            return;
        }
        websocketUrl = (String) urlObject;
    }

    public VoskTranscriptionService()
    {
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
    }

    public boolean isConfiguredProperly()
    {
        return true;
    }

    public boolean supportsLanguageRouting()
    {
        return websocketUrlConfig.trim().startsWith("{");
    }

    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                                  final Consumer<TranscriptionResult> resultConsumer)
    {
        try
        {
            AudioFormat format = request.getFormat();
            if (!format.getEncoding().equals("LINEAR"))
            {
                throw new IllegalArgumentException("Given AudioFormat" +
                        "has unexpected" +
                        "encoding");
            }
            Instant timeRequestReceived = Instant.now();

            WebSocketClient ws = new WebSocketClient();
            VoskWebsocketSession socket = new VoskWebsocketSession(request);
            ws.start();
            ws.connect(socket, new URI(websocketUrl));
            socket.awaitClose();
            resultConsumer.accept(
                    new TranscriptionResult(
                            null,
                            UUID.randomUUID(),
                            timeRequestReceived,
                            false,
                            request.getLocale().toLanguageTag(),
                            0,
                            new TranscriptionAlternative(socket.getResult())));
        }
        catch (Exception e)
        {
            logger.error("Error sending single req", e);
        }
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(Participant participant)
        throws UnsupportedOperationException
    {
        try
        {
            generateWebsocketUrl(participant);
            VoskWebsocketStreamingSession streamingSession = new VoskWebsocketStreamingSession(
                    participant.getDebugName());
            streamingSession.transcriptionTag = participant.getTranslationLanguage();
            if (streamingSession.transcriptionTag == null)
            {
                streamingSession.transcriptionTag = participant.getSourceLanguage();
            }
            return streamingSession;
        }
        catch (Exception e)
        {
            throw new UnsupportedOperationException("Failed to create streaming session", e);
        }
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

    @WebSocket
    public class VoskWebsocketStreamingSession
        implements StreamingRecognitionSession
    {
        private Session session;

        private final String debugName;

        private double sampleRate = -1.0;

        private String lastResult = "";

        private String transcriptionTag = "en-US";

        private final List<TranscriptionListener> listeners = new ArrayList<>();

        private UUID uuid = UUID.randomUUID();

        VoskWebsocketStreamingSession(String debugName)
            throws Exception
        {
            this.debugName = debugName;
            WebSocketClient ws = new WebSocketClient();
            ws.start();
            ws.connect(this, new URI(websocketUrl));
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason)
        {
            this.session = null;
        }

        @OnWebSocketConnect
        public void onConnect(Session session)
        {
            this.session = session;
        }

        @OnWebSocketMessage
        public void onMessage(String msg)
        {
            boolean partial = true;
            String result = "";
            if (logger.isDebugEnabled())
                logger.debug(debugName + "Recieved response: " + msg);
            JSONObject obj = new JSONObject(msg);
            if (obj.has("partial"))
            {
                result = obj.getString("partial");
            }
            else
            {
                partial = false;
                result = obj.getString("text");
            }

            if (!result.isEmpty() && (!partial || !result.equals(lastResult)))
            {
                lastResult = result;
                for (TranscriptionListener l : listeners)
                {
                    l.notify(new TranscriptionResult(
                            null,
                            uuid,
                            Instant.now(),
                            partial,
                            transcriptionTag,
                            1.0,
                            new TranscriptionAlternative(result)));
                }
            }

            if (!partial)
            {
                this.uuid = UUID.randomUUID();
            }
        }

        @OnWebSocketError
        public void onError(Throwable cause)
        {
            logger.error("Error while streaming audio data to transcription service" , cause);
        }

        public void sendRequest(TranscriptionRequest request)
        {
            try
            {
                if (sampleRate < 0)
                {
                    sampleRate = request.getFormat().getSampleRate();
                    session.getRemote().sendString("{\"config\" : {\"sample_rate\" : " + sampleRate + " }}");
                }
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                session.getRemote().sendBytes(audioBuffer);
            }
            catch (Exception e)
            {
                logger.error("Error to send websocket request for participant " + debugName, e);
            }
        }

        public void addTranscriptionListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        public void end()
        {
            try
            {
                session.getRemote().sendString(EOF_MESSAGE);
            }
            catch (Exception e)
            {
                logger.error("Error to finalize websocket connection for participant " + debugName, e);
            }
        }

        public boolean ended()
        {
            return session == null;
        }
    }

    @WebSocket
    public class VoskWebsocketSession
    {
        private final CountDownLatch closeLatch;

        private final TranscriptionRequest request;

        private StringBuilder result;

        VoskWebsocketSession(TranscriptionRequest request)
        {
            this.closeLatch = new CountDownLatch(1);
            this.request = request;
            this.result = new StringBuilder();
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason)
        {
            this.closeLatch.countDown(); // trigger latch
        }

        @OnWebSocketConnect
        public void onConnect(Session session)
        {
            try
            {
                AudioFormat format = request.getFormat();
                session.getRemote().sendString("{\"config\" : {\"sample_rate\" : " + format.getSampleRate() + "}}");
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                session.getRemote().sendBytes(audioBuffer);
                session.getRemote().sendString(EOF_MESSAGE);
            }
            catch (IOException e)
            {
                logger.error("Error to transcribe audio", e);
            }
        }

        @OnWebSocketMessage
        public void onMessage(String msg)
        {
            result.append(msg);
            result.append('\n');
        }

        @OnWebSocketError
        public void onError(Throwable cause)
        {
            logger.error("Websocket connection error", cause);
        }

        public String getResult()
        {
            return result.toString();
        }

        void awaitClose()
            throws InterruptedException
        {
            closeLatch.await();
        }
    }
}
