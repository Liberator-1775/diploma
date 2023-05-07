package org.diploma;

import org.diploma.transcription.*;
import org.diploma.transcription.action.ActionServicesHandler;
import org.diploma.transcription.*;
import org.diploma.transcription.action.*;
import org.jitsi.utils.logging.Logger;
import org.osgi.framework.*;

public class TranscriptionGateway
    extends AbstractGateway<TranscriptionGatewaySession>
{
    private final static Logger logger
        = Logger.getLogger(TranscriptionGateway.class);

    private static final String CUSTOM_TRANSCRIPTION_SERVICE_PROP
        = "org.diploma.transcription.customService";

    private TranscriptHandler handler = new TranscriptHandler();

    private ActionServicesHandler actionServicesHandler;

    public TranscriptionGateway(BundleContext context)
    {
        super(context);

        actionServicesHandler = ActionServicesHandler.init(context);
    }

    @Override
    public void stop()
    {
        if (actionServicesHandler != null)
        {
            actionServicesHandler.stop();
            actionServicesHandler = null;
        }
    }

    @Override
    public TranscriptionGatewaySession createOutgoingCall(CallContext ctx)
    {
        String customTranscriptionServiceClass
            = JigasiBundleActivator.getConfigurationService()
                .getString(
                    CUSTOM_TRANSCRIPTION_SERVICE_PROP,
                    null);
        TranscriptionService service = null;
        if (customTranscriptionServiceClass != null)
        {
            try
            {
                service = (TranscriptionService)Class.forName(
                    customTranscriptionServiceClass).getDeclaredConstructor().newInstance();
            }
            catch(Exception e)
            {
                logger.error("Cannot instantiate custom transcription service");
            }
        }

        if (service == null)
        {
            service = new GoogleCloudTranscriptionService();
        }

        TranscriptionGatewaySession outgoingSession =
                new TranscriptionGatewaySession(
                    this,
                    ctx,
                    service,
                    this.handler);
        outgoingSession.addListener(this);
        outgoingSession.createOutgoingCall();

        return outgoingSession;
    }

    public boolean isReady()
    {
        return true;
    }
}
