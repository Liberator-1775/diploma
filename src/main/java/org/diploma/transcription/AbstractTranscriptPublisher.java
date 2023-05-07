package org.diploma.transcription;

import com.timgroup.statsd.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.Message;
import org.diploma.JigasiBundleActivator;
import org.diploma.TranscriptionGatewaySession;
import org.diploma.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.utils.logging.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public abstract class AbstractTranscriptPublisher<T>
    implements TranscriptPublisher,
               TranscriptionResultPublisher
{
    public final static String P_NAME_TRANSCRIPT_DIRECTORY
        = "org.diploma.transcription.DIRECTORY";

    public final static String P_NAME_TRANSCRIPT_BASE_URL
        = "org.diploma.transcription.BASE_URL";

    public final static String P_NAME_ADVERTISE_URL
        =  "org.diploma.transcription.ADVERTISE_URL";

    public final static String P_NAME_RECORD_AUDIO
        = "org.diploma.transcription.RECORD_AUDIO";

    public final static String P_NAME_RECORD_AUDIO_FORMAT
        = "org.diploma.transcription.RECORD_AUDIO_FORMAT";

    public final static String P_NAME_EXECUTE_SCRIPTS
        = "org.diploma.transcription.EXECUTE_SCRIPTS";

    public final static String P_NAME_SCRIPTS_TO_EXECUTE_LIST_SEPARATOR
        = "org.diploma.transcription.SCRIPTS_TO_EXECUTE_LIST_SEPARATOR";

    public final static String P_NAME_SCRIPTS_TO_EXECUTE_LIST
        = "org.diploma.transcription.SCRIPTS_TO_EXECUTE_LIST";

    public final static String TRANSCRIPT_BASE_URL_DEFAULT_VALUE
        = "http://localhost/";

    public final static String TRANSCRIPT_DIRECTORY_DEFAULT_VALUE
        = "/var/lib/jigasi/transcripts";

    public final static boolean ADVERTISE_URL_DEFAULT_VALUE = false;

    public final static boolean RECORD_AUDIO_DEFAULT_VALUE = false;

    public final static String RECORD_AUDIO_FORMAT_DEFAULT_VALUE = "wav";

    public final static boolean EXECUTE_SCRIPTS_DEFAULT_VALUE = false;

    public final static String SCRIPTS_TO_EXECUTE_LIST_SEPARATOR_DEFAULT_VALUE
        = ",";

    public final static String SCRIPTS_TO_EXECUTE_LIST_DEFAULT_VALUE
        = "script/example_handle_transcript_directory.sh";

    private static final Logger logger
        = Logger.getLogger(AbstractTranscriptPublisher.class);

    private static final String DD_ASPECT_SUCCESS = "upload_success";

    private static final String DD_ASPECT_FAIL = "upload_fail";

    protected static String generateHardToGuessTimeString(String prefix,
                                                          String suffix)
    {
        prefix = prefix == null || prefix.isEmpty() ?
            "":
            prefix + "_";

        suffix = suffix == null || suffix.isEmpty()?
            "":
            suffix;

        return String.format("%s%s_%s%s", prefix, Instant.now(),
            UUID.randomUUID(), suffix);
    }

    protected void sendMessage(ChatRoom chatRoom, T message)
    {
        if (chatRoom == null)
        {
            logger.error("Cannot sent message as chatRoom is null");
            return;
        }

        String messageString = message.toString();
        Message chatRoomMessage = chatRoom.createMessage(messageString);
        try
        {
            chatRoom.sendMessage(chatRoomMessage);
            if (logger.isTraceEnabled())
                logger.trace("Sending message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send message " + messageString, e);
        }
    }

    protected void sendJsonMessage(ChatRoom chatRoom, T jsonMessage)
    {
        if (chatRoom == null)
        {
            logger.error("Cannot sent message as chatRoom is null");
            return;
        }

        if (!(chatRoom instanceof ChatRoomJabberImpl))
        {
            logger.error("Cannot sent message as chatRoom is not an" +
                "instance of ChatRoomJabberImpl");
            return;
        }

        if (!chatRoom.isJoined())
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Skip sending message to room which we left!");
            }
            return;
        }

        String messageString = jsonMessage.toString();
        try
        {
            ((ChatRoomJabberImpl)chatRoom).sendJsonMessage(messageString);
            if (logger.isTraceEnabled())
                logger.trace("Sending json message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send json message " + messageString, e);
        }
    }
    protected void saveTranscriptStringToFile(String directoryName,
                                        String fileName,
                                        String transcript)
    {
        Path rootDirPath = Paths.get(getLogDirPath());
        Path subDirectoryPath = Paths.get(rootDirPath.toString(),
            directoryName);

        if (!createDirectoryIfNotExist(rootDirPath))
        {
            return;
        }

        if (!createDirectoryIfNotExist(subDirectoryPath))
        {
            return;
        }

        File t = new File(subDirectoryPath.toString(), fileName);
        try(FileWriter writer = new FileWriter(t, StandardCharsets.UTF_8))
        {
            writer.write(transcript);
            logger.info("Wrote final transcript to " + t);
        }
        catch(IOException e)
        {
            logger.warn("Unable to write transcript to file " + t, e);
        }
    }

    protected static boolean createDirectoryIfNotExist(Path path)
    {
        File dir = path.toFile();

        if (!dir.exists())
        {
            if (!dir.mkdirs())
            {
                logger.warn("Was unable to make a directory called " + dir);
                return false;
            }
        }

        if (dir.exists() && !dir.isDirectory())
        {
            logger.warn("Was unable to make a directory because" +
                " there is a file called " + dir);
            return false;
        }

        return true;
    }

    public static String getLogDirPath()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_TRANSCRIPT_DIRECTORY,
                TRANSCRIPT_DIRECTORY_DEFAULT_VALUE);
    }

    protected String getBaseURL()
    {
       return JigasiBundleActivator.getConfigurationService()
           .getString(P_NAME_TRANSCRIPT_BASE_URL,
               TRANSCRIPT_BASE_URL_DEFAULT_VALUE);
    }

    protected boolean advertiseURL()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ADVERTISE_URL,
                ADVERTISE_URL_DEFAULT_VALUE);
    }

    protected boolean shouldRecordAudio()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_RECORD_AUDIO,
                RECORD_AUDIO_DEFAULT_VALUE);
    }

    protected String getRecordingAudioFormat()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_RECORD_AUDIO_FORMAT,
                RECORD_AUDIO_FORMAT_DEFAULT_VALUE);
    }

    protected boolean shouldExecuteScripts()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_EXECUTE_SCRIPTS,
                EXECUTE_SCRIPTS_DEFAULT_VALUE);
    }

    protected List<String> getPathsToScriptsToExecute()
    {
        String paths = JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_SCRIPTS_TO_EXECUTE_LIST,
                SCRIPTS_TO_EXECUTE_LIST_DEFAULT_VALUE);

        return Arrays.asList(paths.split(
            getPathsToScriptsToExecuteSeparator()));
    }

    protected String getPathsToScriptsToExecuteSeparator()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_SCRIPTS_TO_EXECUTE_LIST_SEPARATOR,
                SCRIPTS_TO_EXECUTE_LIST_SEPARATOR_DEFAULT_VALUE);
    }

    abstract BaseFormatter getFormatter();

    protected abstract T formatSpeechEvent(SpeechEvent e);

    protected abstract T formatJoinEvent(TranscriptEvent e);

    protected abstract T formatLeaveEvent(TranscriptEvent e);

    protected abstract T formatRaisedHandEvent(TranscriptEvent e);

    public abstract class BaseFormatter
    {
        protected Instant startInstant;

        protected Instant endInstant;
        protected String roomName;

        protected String roomUrl;

        protected List<Participant> initialMembers = new LinkedList<>();

        protected Map<TranscriptEvent, T> formattedEvents = new HashMap<>();

        BaseFormatter startedOn(TranscriptEvent event)
        {
            if (event != null && event.getEvent().equals(
                Transcript.TranscriptEventType.START))
            {
                this.startInstant = event.getTimeStamp();
            }
            return this;
        }

        BaseFormatter tookPlaceInRoom(String roomName)
        {
            if (roomName != null)
            {
                this.roomName = roomName;
            }
            return this;
        }

        BaseFormatter tookPlaceAtUrl(String url)
        {
            if (url != null)
            {
                this.roomUrl = url;
            }
            return this;
        }

        BaseFormatter initialParticipants(List<Participant> participants)
        {
            this.initialMembers.addAll(participants);
            return this;
        }

        BaseFormatter speechEvents(List<SpeechEvent> events)
        {
            for (SpeechEvent e : events)
            {
                if (e.getEvent().equals(Transcript.TranscriptEventType.SPEECH))
                {
                    formattedEvents.put(e, formatSpeechEvent(e));
                }
            }
            return this;
        }

        BaseFormatter joinEvents(List<TranscriptEvent> events)
        {
            for (TranscriptEvent e : events)
            {
                if (e.getEvent().equals(Transcript.TranscriptEventType.JOIN))
                {
                    formattedEvents.put(e, formatJoinEvent(e));
                }
            }
            return this;
        }

        BaseFormatter leaveEvents(List<TranscriptEvent> events)
        {
            for (TranscriptEvent e : events)
            {
                if (e.getEvent().equals(Transcript.TranscriptEventType.LEAVE))
                {
                    formattedEvents.put(e, formatLeaveEvent(e));
                }
            }
            return this;
        }


        BaseFormatter raiseHandEvents(List<TranscriptEvent> events)
        {
            for (TranscriptEvent e : events)
            {
                if (e.getEvent().equals(
                    Transcript.TranscriptEventType.RAISE_HAND))
                {
                    formattedEvents.put(e, formatRaisedHandEvent(e));
                }
            }
            return this;
        }

        BaseFormatter endedOn(TranscriptEvent event)
        {
            if (event != null && event.getEvent().equals(
                Transcript.TranscriptEventType.END))
            {
                this.endInstant = event.getTimeStamp();
            }
            return this;
        }

        protected List<T> getSortedEvents()
        {
            List<TranscriptEvent> sortedKeys =
                new ArrayList<>(formattedEvents.keySet());
            Collections.sort(sortedKeys);

            List<T> sortedEvents = new ArrayList<>(sortedKeys.size());
            for (TranscriptEvent event : sortedKeys)
            {
                sortedEvents.add(formattedEvents.get(event));
            }

            return sortedEvents;
        }

        abstract T finish();
    }

    public abstract class BasePromise
        implements Promise
    {
        private boolean published = false;

        private final String dirName = generateHardToGuessTimeString("", "");

        private String audioFileName;

        private Recorder recorder;

        @Override
        public boolean hasDescription()
        {
            return advertiseURL();
        }

        protected String getDirPath()
        {
            return dirName;
        }

        @Override
        public void maybeStartRecording(MediaDevice device)
        {
            if (shouldRecordAudio())
            {
                createDirectoryIfNotExist(Paths.get(getLogDirPath(), dirName));

                String format = getRecordingAudioFormat();
                this.audioFileName =
                   generateHardToGuessTimeString("",
                       String.format(".%s", format));

                String audioFilePath = Paths.get(getLogDirPath(), dirName,
                    audioFileName).toString();

                this.recorder
                    = LibJitsi.getMediaService().createRecorder(device);
                try
                {
                    this.recorder.start(format, audioFilePath);
                }
                catch (MediaException | IOException e)
                {
                    logger.error("Could not start recording", e);
                }
            }
        }

        @Override
        public final synchronized void publish(Transcript transcript)
        {
            if (published)
            {
                return;
            }
            else
            {
                published = true;
            }

            if (this.recorder != null)
            {
                this.recorder.stop();
            }

            doPublish(transcript);

            maybeExecuteBashScripts();
        }

        protected abstract void doPublish(Transcript transcript);

        public String getAudioRecordingFileName()
        {
            return audioFileName;
        }

        private void maybeExecuteBashScripts()
        {
            if (shouldExecuteScripts())
            {
                Path absDirPath =
                    Paths.get(getLogDirPath(), dirName).toAbsolutePath();

                for (String scriptPath : getPathsToScriptsToExecute())
                {
                    try
                    {
                        logger.info("executing " + scriptPath +
                        " with arguments '" + absDirPath + "'");

                        Process p = new ProcessBuilder(scriptPath.toString(),
                            absDirPath.toString()).start();

                        StatsDClient dClient
                            = JigasiBundleActivator.getDataDogClient();
                        if (dClient != null)
                        {
                            int returnValue;

                            try
                            {
                                returnValue = p.waitFor();
                            }
                            catch (InterruptedException e)
                            {
                                logger.error("", e);
                                returnValue = -1;
                            }

                            if (returnValue == 0)
                            {
                                dClient.increment(DD_ASPECT_SUCCESS);
                                if (logger.isDebugEnabled())
                                {
                                    logger.debug("thrown stat: " +
                                        DD_ASPECT_SUCCESS
                                    );
                                }
                            }
                            else
                            {
                                dClient.increment(DD_ASPECT_FAIL);
                                if (logger.isDebugEnabled())
                                {
                                    logger.debug("thrown stat: " +
                                        DD_ASPECT_FAIL
                                    );
                                }
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        logger.error("Could not execute " + scriptPath, e);
                    }
                }
            }
        }
    }
}
