package org.diploma.rest;

import org.diploma.transcription.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.diploma.transcription.*;
import org.osgi.framework.*;

public class TranscriptServerBundleActivator
    extends AbstractJettyBundleActivator
{
    public static final String JETTY_PROPERTY_PREFIX
        = "org.diploma.transcription";

    public TranscriptServerBundleActivator()
    {
        super(JETTY_PROPERTY_PREFIX, JETTY_PROPERTY_PREFIX);
    }

    @Override
    protected Handler initializeHandlerList(BundleContext bundleContext,
                                            Server server)
    {
        ResourceHandler fileHandler = new ResourceHandler();

        fileHandler.setDirectoriesListed(false);
        fileHandler.setResourceBase(
            AbstractTranscriptPublisher.getLogDirPath());

        return fileHandler;
    }
}
