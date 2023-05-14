package org.diploma.rest;

import org.eclipse.jetty.server.*;
import org.osgi.framework.*;

public class RESTBundleActivator
    extends AbstractJettyBundleActivator
{
    public static final String REST_API = "rest";

    public static final String REST_API_PNAME
        = "org.diploma." + REST_API;

    private static final String ENABLE_REST_SHUTDOWN_PNAME
        = "org.diploma.ENABLE_REST_SHUTDOWN";

    public RESTBundleActivator()
    {
        super(REST_API_PNAME, REST_API_PNAME);
    }

    @Override
    protected Handler initializeHandlerList(BundleContext bundleContext,
        Server server)
    {
        return new HandlerImpl(bundleContext,
            getConfigService().getBoolean(ENABLE_REST_SHUTDOWN_PNAME, false));
    }
}
