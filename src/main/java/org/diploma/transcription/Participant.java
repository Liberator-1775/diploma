package org.diploma.transcription;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.diploma.util.Util;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.utils.logging.*;
import org.jivesoftware.smack.packet.*;

import javax.media.format.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public class Participant
    implements TranscriptionListener
{
    private final static Logger logger = Logger.getLogger(Participant.class);

    private static final int EXPECTED_AUDIO_LENGTH = 1920;

    private static final int BUFFER_SIZE = EXPECTED_AUDIO_LENGTH * 25;

    private static final boolean USE_LOCAL_BUFFER = true;

    public static final String UNKNOWN_NAME = "Fellow Jitser";

    public static final long DEFAULT_UNKNOWN_AUDIO_SSRC = -1;

    private final static String GRAVARAR_URL_FORMAT
        = "https://www.gravatar.com/avatar/%s?d=wavatar&size=200";

    private final static String MEEPLE_URL_FORMAT
        = "https://abotars.jitsi.net/meeple/%s";

    private Transcriber transcriber;

    private ChatRoomMember chatMember;

    private ConferenceMember confMember;

    private HashMap<String, TranscriptionService.StreamingRecognitionSession> sessions = new HashMap<>();

    private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

    private AudioFormat audioFormat;

    private boolean isCompleted = false;

    private String identifier;

    private Locale sourceLanguageLocale = Locale.forLanguageTag("en-US");

    private String translationLanguage = null;

    private SilenceFilter silenceFilter = null;

    Participant(Transcriber transcriber, String identifier)
    {
        this(transcriber, identifier, false);
    }

    Participant(Transcriber transcriber, String identifier, boolean filterAudio)
    {
        this.transcriber = transcriber;
        this.identifier = identifier;

        if (filterAudio)
        {
            silenceFilter = new SilenceFilter();
        }
    }

    String getDebugName() {
        ChatRoomMember _chatMember = this.chatMember;

        if (_chatMember == null)
        {
            return null;
        }

        String roomId = chatMember.getChatRoom().getIdentifier();

        if (roomId.contains("@"))
        {
            roomId = roomId.substring(0, roomId.indexOf("@"));
        }

        return roomId + "/" + _chatMember.getName();
    }

    public String getName()
    {
        if (chatMember == null)
        {
            return UNKNOWN_NAME;
        }

        String name = chatMember.getDisplayName();
        if (name != null && !name.isEmpty())
        {
            return name;
        }

        return UNKNOWN_NAME;
    }

    public String getEmail()
    {
        if (chatMember == null)
        {
            return null;
        }
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        return ((ChatRoomMemberJabberImpl) chatMember).getEmail();
    }

    public String getAvatarUrl()
    {
        if (chatMember == null)
        {
            return null;
        }
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        ChatRoomMemberJabberImpl memberJabber
            = ((ChatRoomMemberJabberImpl) this.chatMember);

        IdentityPacketExtension ipe = getIdentityExtensionOrNull(
            memberJabber.getLastPresence());

        String url;
        if (ipe != null && (url = ipe.getUserAvatarUrl()) != null)
        {
            return url;
        }
        else if ((url = memberJabber.getAvatarUrl()) != null)
        {
            return url;
        }

        String email;
        if ((email = getEmail()) != null)
        {
            return String.format(GRAVARAR_URL_FORMAT,
                Util.stringToMD5hash(email));
        }

        AvatarIdPacketExtension avatarIdExtension = getAvatarIdExtensionOrNull(
            memberJabber.getLastPresence());
        String avatarId;
        if (avatarIdExtension != null &&
            (avatarId = avatarIdExtension.getAvatarId()) != null)
        {
            return String.format(MEEPLE_URL_FORMAT,
                Util.stringToMD5hash(avatarId));
        }
        else
        {
            return String.format(MEEPLE_URL_FORMAT,
                Util.stringToMD5hash(identifier));
        }
    }

    public String getIdentityUserName()
    {
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        IdentityPacketExtension ipe
            = getIdentityExtensionOrNull(
                ((ChatRoomMemberJabberImpl) chatMember).getLastPresence());

        return ipe != null ?
            ipe.getUserName():
            null;
    }

    public String getIdentityUserId()
    {
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        IdentityPacketExtension ipe
            = getIdentityExtensionOrNull(
                ((ChatRoomMemberJabberImpl) chatMember).getLastPresence());

        return ipe != null ?
            ipe.getUserId():
            null;
    }

    public String getLanguageKey()
    {
        if (transcriber.getTranscriptionService().supportsLanguageRouting())
        {
            return this.getSourceLanguage();
        }
        return "global";
    }

    public String getIdentityGroupId()
    {
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        IdentityPacketExtension ipe
            = getIdentityExtensionOrNull(
                ((ChatRoomMemberJabberImpl) chatMember).getLastPresence());

        return ipe != null ?
            ipe.getGroupId():
            null;
    }

    private IdentityPacketExtension getIdentityExtensionOrNull(Presence p)
    {
        return p.getExtension(IdentityPacketExtension.class);
    }

    private AvatarIdPacketExtension getAvatarIdExtensionOrNull(Presence p)
    {
        return p.getExtension(AvatarIdPacketExtension.class);
    }

    private TranscriptionRequestExtension
                getTranscriptionRequestExtensionOrNull(Presence p)
    {
        return p != null
                ? p.getExtension(TranscriptionRequestExtension.class)
                : null;
    }

    public long getSSRC()
    {
        if (confMember == null)
        {
            return DEFAULT_UNKNOWN_AUDIO_SSRC;
        }
        return getConferenceMemberAudioSSRC(confMember);
    }

    public String getSourceLanguage()
    {
        return sourceLanguageLocale == null ?
            null :
            sourceLanguageLocale.getLanguage();
    }

    public String getTranslationLanguage()
    {
        return translationLanguage;
    }

    public void setSourceLanguage(String language)
    {
        if (language == null)
        {
            sourceLanguageLocale = null;
        }
        else
        {
            sourceLanguageLocale = Locale.forLanguageTag(language);
        }
    }

    public void setTranslationLanguage(String language)
    {
        translationLanguage = language;
    }

    public void setConfMember(ConferenceMember confMember)
    {
        this.confMember = confMember;
    }

    public void setChatMember(ChatRoomMember chatMember)
    {
        this.chatMember = chatMember;
    }

    public String getId()
    {
        return identifier;
    }

    void joined()
    {
        TranscriptionService.StreamingRecognitionSession
                session = sessions.getOrDefault(getLanguageKey(), null);

        if (session != null && !session.ended())
        {
            return;
        }

        if (transcriber.getTranscriptionService().supportsStreamRecognition())
        {
            session = transcriber.getTranscriptionService()
                .initStreamingSession(this);
            session.addTranscriptionListener(this);
            sessions.put(getLanguageKey(), session);
            isCompleted = false;
        }
    }

    public void left()
    {
        TranscriptionService.StreamingRecognitionSession session = sessions.getOrDefault(getLanguageKey(), null);
        if (session != null)
        {
            session.end();
        }
    }

    void giveBuffer(javax.media.Buffer buffer)
    {
        if (audioFormat == null)
        {
            audioFormat = (AudioFormat) buffer.getFormat();
        }

        byte[] audio = (byte[]) buffer.getData();

        if (USE_LOCAL_BUFFER)
        {
            buffer(audio);
        }
        else
        {
            sendRequest(audio);
        }
    }

    @Override
    public void notify(TranscriptionResult result)
    {
        result.setParticipant(this);
        if (logger.isDebugEnabled())
            logger.debug(result);
        transcriber.notify(result);
    }

    @Override
    public void completed()
    {
        isCompleted = true;
        transcriber.checkIfFinishedUp();
    }

    @Override
    public void failed(FailureReason reason)
    {
        isCompleted = true;
        logger.error(getDebugName() + " transcription failed: " + reason);
        transcriber.stop(reason);
    }

    public boolean isCompleted()
    {
        return isCompleted;
    }

    private void buffer(byte[] audio)
    {
        transcriber.executorService.execute(() ->
           {
               byte[] toBuffer;
               if (silenceFilter != null)
               {
                   silenceFilter.giveSegment(audio);
                   if (silenceFilter.shouldFilter())
                   {
                       return;
                   }
                   else if (silenceFilter.newSpeech())
                   {
                       ((Buffer) buffer).clear();
                       toBuffer = silenceFilter.getSpeechWindow();
                   }
                   else
                   {
                       toBuffer = audio;
                   }
               }
               else
               {
                   toBuffer = audio;
               }

               try
               {
                   buffer.put(toBuffer);
               }
               catch (BufferOverflowException | ReadOnlyBufferException e)
               {
                   sendRequest(audio);
               }

               int spaceLeft = buffer.limit() - buffer.position();
               if (spaceLeft < EXPECTED_AUDIO_LENGTH)
               {
                   sendRequest(buffer.array());
                   ((Buffer) buffer).clear();
               }
           });
    }

    private void sendRequest(byte[] audio)
    {
        transcriber.executorService.execute(() ->
        {
            TranscriptionService.StreamingRecognitionSession session = sessions.getOrDefault(getLanguageKey(), null);
            TranscriptionRequest request
                = new TranscriptionRequest(audio,
                                           audioFormat,
                                           sourceLanguageLocale);

            if (session != null && !session.ended())
            {
                session.sendRequest(request);
            }
            else
            {
                transcriber.getTranscriptionService().sendSingleRequest(
                        request,
                        this::notify);
            }
        });
    }

    public Transcriber getTranscriber()
    {
        return transcriber;
    }

    private static long getConferenceMemberAudioSSRC(
        ConferenceMember confMember)
    {
        return confMember.getAudioSsrc() & 0xffffffffL;
    }

    public boolean hasValidSourceLanguage()
    {
        String lang = this.getSourceLanguage();

        return lang != null && !lang.isEmpty();
    }

    public boolean isRequestingTranscription()
    {
        ChatRoomMemberJabberImpl memberJabber
            = ((ChatRoomMemberJabberImpl) this.chatMember);
        TranscriptionRequestExtension ext
            = getTranscriptionRequestExtensionOrNull(
                memberJabber != null ? memberJabber.getLastPresence() : null);

        return ext != null && Boolean.parseBoolean(ext.getText());
    }
}
