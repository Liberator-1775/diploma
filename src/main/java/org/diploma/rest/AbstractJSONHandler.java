package org.diploma.rest;

import net.java.sip.communicator.util.osgi.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.jitsi.utils.version.*;
import org.jivesoftware.smack.packet.*;
import org.json.simple.*;
import org.osgi.framework.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;

public abstract class AbstractJSONHandler
    extends AbstractHandler
{
    private static final String DEFAULT_JSON_TARGET = null;

    protected static final String GET_HTTP_METHOD = "GET";

    private static final String HEALTH_TARGET = "/about/health";

    protected static final String PATCH_HTTP_METHOD = "PATCH";

    protected static final String POST_HTTP_METHOD = "POST";

    private static final String VERSION_TARGET = "/about/version";

    protected static int getHttpStatusCodeForResultIq(IQ responseIQ)
    {
        StanzaError.Condition condition = responseIQ.getError().getCondition();

        if (StanzaError.Condition.not_authorized.equals(condition))
        {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        else if (StanzaError.Condition.service_unavailable.equals(
            condition))
        {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        }
        else
        {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    protected final BundleContext bundleContext;

    private final String jsonTarget;

    protected AbstractJSONHandler(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;

        String jsonTarget = DEFAULT_JSON_TARGET;

        if (jsonTarget != null && !jsonTarget.startsWith("."))
            jsonTarget = "." + jsonTarget;
        this.jsonTarget = jsonTarget;
    }

    protected void beginResponse(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
    {
        beginResponse(
            target,
            baseRequest,
            request,
            response,
            RESTUtil.JSON_CONTENT_TYPE_WITH_CHARSET);
    }

    protected void beginResponse(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response,
        String contentType)
    {
        response.setContentType(contentType);
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    protected void doGetHealthJSON(
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
    }

    protected void doGetVersionJSON(
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        beginResponse(/*target*/ null, baseRequest, request, response);

        BundleContext bundleContext = getBundleContext();
        int status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;

        if (bundleContext != null)
        {
            VersionService versionService
                = ServiceUtils.getService(bundleContext, VersionService.class);

            if (versionService != null)
            {
                org.jitsi.utils.version.Version version
                    = versionService.getCurrentVersion();
                JSONObject versionJSONObject = new JSONObject();

                versionJSONObject.put(
                    "name",
                    version.getApplicationName());
                versionJSONObject.put("version", version.toString());
                versionJSONObject.put("os", System.getProperty("os.name"));

                Writer writer = response.getWriter();

                response.setStatus(status = HttpServletResponse.SC_OK);
                versionJSONObject.writeJSONString(writer);
            }
        }

        if (response.getStatus() != status)
            response.setStatus(status);

        endResponse(null, baseRequest, request, response);
    }

    protected void endResponse(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
    {
        if (!baseRequest.isHandled())
        {
            if (response.getStatus() == 0)
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            baseRequest.setHandled(true);
        }
    }

    public BundleContext getBundleContext()
    {
        return bundleContext;
    }

    public <T> T getService(Class<T> serviceClass)
    {
        BundleContext bundleContext = getBundleContext();
        T service = ServiceUtils.getService(bundleContext, serviceClass);

        return service;
    }

    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (target != null)
        {
            int jsonTargetLength
                = (jsonTarget == null) ? 0 : jsonTarget.length();

            if (jsonTargetLength == 0 || target.endsWith(jsonTarget))
            {
                target
                    = target.substring(0, target.length() - jsonTargetLength);

                handleJSON(target, baseRequest, request, response);
            }
        }
    }

    protected void handleHealthJSON(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (GET_HTTP_METHOD.equals(request.getMethod()))
        {
            doGetHealthJSON(baseRequest, request, response);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    protected void handleJSON(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (HEALTH_TARGET.equals(target))
        {
            target = target.substring(HEALTH_TARGET.length());

            handleHealthJSON(target, baseRequest, request, response);
        }
        else if (VERSION_TARGET.equals(target))
        {
            target = target.substring(VERSION_TARGET.length());

            handleVersionJSON(target, baseRequest, request, response);
        }
    }

    protected void handleVersionJSON(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (GET_HTTP_METHOD.equals(request.getMethod()))
        {
            doGetVersionJSON(baseRequest, request, response);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }
}
