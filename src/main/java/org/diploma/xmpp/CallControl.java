package org.diploma.xmpp;

import org.diploma.*;
import org.diploma.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

public class CallControl
{
    private final static Logger logger = Logger.getLogger(CallControl.class);

    public static final String ROOM_NAME_HEADER = "JvbRoomName";

    public static final String ROOM_PASSWORD_HEADER = "JvbRoomPassword";

    public static final String ALLOWED_JID_P_NAME
        = "org.diploma.ALLOWED_JID";

    public static final String TRANSCRIPTION_DIAL_IQ_DESTINATION
        = "jitsi_meet_transcribe";

    private SipGateway sipGateway;

    private TranscriptionGateway transcriptionGateway;

    private Jid allowedJid;

    public CallControl(SipGateway sipGateway, ConfigurationService config)
    {
        this(config);
        this.sipGateway = sipGateway;
    }

    public CallControl(TranscriptionGateway transcriptionGateway,
                       ConfigurationService config)
    {
        this(config);
        this.transcriptionGateway = transcriptionGateway;
    }

    public CallControl(ConfigurationService config)
    {
        Boolean always_trust_mode = config.getBoolean(
            "net.java.sip.communicator.service.gui.ALWAYS_TRUST_MODE_ENABLED",
            false);
        if (always_trust_mode)
        {
            logger.warn(
                "Always trust in remote TLS certificates mode is enabled");
        }

        String allowedJidString = config.getString(ALLOWED_JID_P_NAME, null);
        if (allowedJidString != null)
        {
            try
            {
                this.allowedJid = JidCreate.from(allowedJidString);
            }
            catch (XmppStringprepException e)
            {
                logger.error("Invalid call control JID", e);
            }
        }

        if (allowedJid != null)
        {
            logger.info("JID allowed to make outgoing calls: " + allowedJid);
        }
    }

    public RefIq handleDialIq(DialIq iq, CallContext ctx,
        AbstractGatewaySession[] createdSession)
        throws CallControlAuthorizationException
    {
        checkAuthorized(iq);

        String from = iq.getSource();
        String to = iq.getDestination();

        ctx.setDestination(to);

        String roomName = null;

        for (ExtensionElement ext: iq.getExtensions())
        {
            if (ext instanceof HeaderExtension)
            {
                HeaderExtension header = (HeaderExtension) ext;
                String name = header.getName();
                String value = header.getValue();

                if (ROOM_NAME_HEADER.equals(name))
                {
                    roomName = value;
                    try
                    {
                        ctx.setRoomName(roomName);
                    }
                    catch(XmppStringprepException e)
                    {
                        throw new RuntimeException("Malformed JvbRoomName header found " + roomName, e);
                    }
                }
                else if (ROOM_PASSWORD_HEADER.equals(name))
                {
                    ctx.setRoomPassword(value);
                }
                else
                {
                    ctx.addExtraHeader(name, value);
                }
            }
        }

        if (roomName == null)
            throw new RuntimeException("No JvbRoomName header found");

        logger.info(ctx +
            " Got dial request " + from + " -> " + to + " room: " + roomName);

        AbstractGatewaySession session = null;
        if (TRANSCRIPTION_DIAL_IQ_DESTINATION.equals(to))
        {
            if (transcriptionGateway == null)
            {
                logger.error(ctx
                    + " Cannot accept dial request " + to + " because"
                    + " the TranscriptionGateway is disabled");
                return RefIq.createResult(iq,
                    StanzaError.Condition.not_acceptable.toString());
            }

            session = transcriptionGateway.createOutgoingCall(ctx);
        }
        else
        {
            if (sipGateway == null)
            {
                logger.error(ctx
                    + " Cannot accept dial request " + to + " because"
                    + " the SipGateway is disabled");
                return RefIq.createResult(iq,
                    StanzaError.Condition.not_acceptable.toString());
            }

            session = sipGateway.createOutgoingCall(ctx);
        }

        if (createdSession != null && createdSession.length == 1)
        {
            createdSession[0] = session;
        }

        return RefIq.createResult(iq, "xmpp:" + ctx.getCallResource());
    }

    public IQ handleHangUp(HangUp iq)
        throws CallControlAuthorizationException
    {
        checkAuthorized(iq);

        Jid callResource = iq.getTo();
        SipGatewaySession session = sipGateway.getSession(callResource);
        if (session == null)
            throw new RuntimeException(
                "No sipGateway for call: " + callResource);

        session.hangUp();
        return IQ.createResultIQ(iq);
    }

    private void checkAuthorized(IQ iq)
        throws CallControlAuthorizationException
    {
        Jid fromBareJid = iq.getFrom().asBareJid();
        if (allowedJid != null && !allowedJid.equals(fromBareJid))
        {
            throw new CallControlAuthorizationException(iq);
        }
        else if (allowedJid == null)
        {
            logger.warn("Requests are not secured by JID filter!");
        }
    }

    public SipGateway getSipGateway()
    {
        return sipGateway;
    }

    public void setSipGateway(SipGateway sipGateway)
    {
        this.sipGateway = sipGateway;
    }

    public TranscriptionGateway getTranscriptionGateway()
    {
        return transcriptionGateway;
    }

    public void setTranscriptionGateway(TranscriptionGateway transcriptionGw)
    {
        this.transcriptionGateway = transcriptionGw;
    }

    public AbstractGatewaySession getSession(Jid callResource)
    {
        AbstractGatewaySession result
            = this.sipGateway.getSession(callResource);
        if (result == null)
        {
            result
                = this.transcriptionGateway.getSession(callResource);
        }

        return result;
    }

    public void addGatewayListener(GatewayListener listener)
    {
        this.sipGateway.addGatewayListener(listener);
        this.transcriptionGateway.addGatewayListener(listener);
    }

    public void removeGatewayListener(GatewayListener listener)
    {
        this.sipGateway.removeGatewayListener(listener);
        this.transcriptionGateway.removeGatewayListener(listener);
    }
}
