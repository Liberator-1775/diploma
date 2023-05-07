package org.diploma.transcription;

import net.java.sip.communicator.service.protocol.*;
import org.jitsi.utils.logging.*;

import java.time.*;
import java.time.format.*;
import java.util.*;

public class LocalTxtTranscriptHandler
    extends AbstractTranscriptPublisher<String>
{

    private static final Logger logger = Logger.getLogger(Transcript.class);

    private static final int MAX_LINE_WIDTH = 80;

    private static final char DELIMITER = '_';

    private static final String NEW_LINE = System.lineSeparator();

    private static String getDelimiter()
    {
        return String.join("", Collections.nCopies(MAX_LINE_WIDTH,
            Character.toString(DELIMITER))) + NEW_LINE;
    }

    private static final String UNFORMATTED_HEADER_ROOM_NAME_KNOWN
        = "Transcript of conference held at %s in room %s%n" +
        "Initial people present at %s:%n%s%n" +
        "Transcript, started at %s:%n";

    private static final String UNFORMATTED_HEADER
        = "Transcript of conference held at %s%n" +
        "Initial people present at %s:%n%n%s%n" +
        "Transcript, started at %s:%n";

    private static final String UNFORMATTED_FOOTER
        = "%nEnd of transcript at %s";

    private static final String UNFORMATTED_EVENT_BASE = "<%s> %s";

    private static final String UNFORMATTED_SPEECH = ": %s";

    private static final String UNFORMATTED_JOIN
        = UNFORMATTED_EVENT_BASE + " joined the conference";

    private static final String UNFORMATTED_LEAVE
        = UNFORMATTED_EVENT_BASE + " left the conference";

    private static final String UNFORMATTED_RAISED_HAND
        = UNFORMATTED_EVENT_BASE + " raised their hand";

    private static final DateTimeFormatter timeFormatter
        = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter dateFormatter
        = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter dateTimeFormatter
        = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.UTC);

    @Override
    public void publish(ChatRoom chatRoom, TranscriptionResult result)
    {
        if (result.isInterim())
        {
            return;
        }

        String name = result.getName();
        String transcription = result.getAlternatives().iterator()
            .next().getTranscription();

        String toSend = name + ": " + transcription;
        super.sendMessage(chatRoom, toSend);
    }

    @Override
    public void publish(ChatRoom chatRoom, TranslationResult result)
    {

    }

    @Override
    public Promise getPublishPromise()
    {
        return new TxtPublishPromise();
    }

    @Override
    public BaseFormatter getFormatter()
    {
        return new TxtFormatter();
    }

    @Override
    protected String formatSpeechEvent(SpeechEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());
        String transcription = e.getResult().getAlternatives().iterator().next()
            .getTranscription();

        String base = String.format(UNFORMATTED_EVENT_BASE, timeStamp, name);
        String speech = String.format(UNFORMATTED_SPEECH, transcription);
        String formatted
            = base + String.format(UNFORMATTED_SPEECH, transcription);

        return formatToMaximumLineLength(formatted, MAX_LINE_WIDTH,
            base.length() + (speech.length() - transcription.length()))
            + NEW_LINE;
    }

    @Override
    protected String formatJoinEvent(TranscriptEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());

        return String.format(UNFORMATTED_JOIN, timeStamp, name) + NEW_LINE;
    }

    @Override
    protected String formatLeaveEvent(TranscriptEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());

        return String.format(UNFORMATTED_LEAVE, timeStamp, name) + NEW_LINE;
    }

    @Override
    protected String formatRaisedHandEvent(TranscriptEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());

        return String.format(UNFORMATTED_RAISED_HAND, timeStamp, name)
            + NEW_LINE;
    }

    private String createHeader(Instant startInstant,
                                String roomName,
                                List<Participant> initialMembers)
    {
        String initialMembersString;
        if (initialMembers == null || initialMembers.isEmpty())
        {
            initialMembersString = "";
        }
        else
        {
            List<String> memberNames = new ArrayList<>();
            initialMembers.forEach((p) -> memberNames.add(p.getName()));

            initialMembersString = "\t" + String.join("\n\t", memberNames)
                + NEW_LINE;
        }

        String header;
        String dateString = dateFormatter.format(startInstant);
        String timeString = timeFormatter.format(startInstant);
        if (roomName == null)
        {
            header = String.format(UNFORMATTED_HEADER,
                dateString, timeString, initialMembersString,
                timeString);
        }
        else
        {
            header = String.format(UNFORMATTED_HEADER_ROOM_NAME_KNOWN,
                dateString, roomName, timeString, initialMembersString,
                timeString);
        }

        return header + getDelimiter();
    }

    private String createFooter(Instant endInstant)
    {
        String dateTimeString = dateTimeFormatter.format(endInstant);

        return getDelimiter() + NEW_LINE
            + String.format(UNFORMATTED_FOOTER, dateTimeString);
    }

    private static String formatToMaximumLineLength(String toFormat,
                                                    int maximumLength,
                                                    int spacesAfterEnter)
    {
        boolean endWithSeparator = toFormat.endsWith(NEW_LINE);

        String[] tokens = toFormat.split(" +");

        if (tokens.length == 0)
        {
            return "";
        }
        else if (tokens.length == 1)
        {
            return tokens[0];
        }

        StringBuilder formattedBuilder = new StringBuilder();
        int currentLineLength = 0;
        for (String currentToken: tokens)
        {
            if (currentLineLength + currentToken.length() > maximumLength ||
                NEW_LINE.equals(currentToken))
            {
                formattedBuilder.append(NEW_LINE);
                if (spacesAfterEnter > 0)
                {
                    formattedBuilder.append(
                        String.join("",
                            Collections.nCopies(spacesAfterEnter, " ")));
                }
                currentLineLength = spacesAfterEnter;
            }

            formattedBuilder.append(currentToken);
            currentLineLength += currentToken.length();

            if (currentLineLength < maximumLength)
            {
                formattedBuilder.append(" ");
                currentLineLength += 1;
            }
        }

        if (endWithSeparator)
        {
            formattedBuilder.append(NEW_LINE);
        }

        return formattedBuilder.toString();
    }

    private class TxtFormatter
        extends BaseFormatter
    {
        @Override
        public String finish()
        {
            String header = createHeader(super.startInstant, super.roomName,
                super.initialMembers);
            String footer = createFooter(super.endInstant);

            final StringBuilder builder = new StringBuilder();
            builder.append(header);

            List<TranscriptEvent> sortedKeys =
                new ArrayList<>(super.formattedEvents.keySet());
            Collections.sort(sortedKeys);

            for (String event : super.getSortedEvents())
            {
                builder.append(event);
            }

            builder.append(footer);

            return builder.toString();
        }
    }

    private class TxtPublishPromise
        extends BasePromise
    {
        private final String fileName
            = generateHardToGuessTimeString("transcript", ".txt");

        @Override
        protected void doPublish(Transcript transcript)
        {
            String t =
                transcript.getTranscript(LocalTxtTranscriptHandler.this);

            saveTranscriptStringToFile(getDirPath(), fileName, t);
        }

        @Override
        public String getDescription()
        {
            return String.format("Transcript will be saved in %s/%s/%s%n",
                getBaseURL(), getDirPath(), fileName);
        }
    }
}
