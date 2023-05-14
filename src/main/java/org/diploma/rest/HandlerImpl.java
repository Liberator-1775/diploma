package org.diploma.rest;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import net.java.sip.communicator.service.protocol.*;
import org.diploma.*;
import org.diploma.health.Health;
import org.diploma.stats.Statistics;
import org.diploma.xmpp.CallControlMucActivator;
import org.diploma.*;
import org.diploma.stats.*;
import org.diploma.xmpp.*;

import org.eclipse.jetty.server.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.json.simple.*;
import org.json.simple.parser.*;
import org.osgi.framework.*;

public class HandlerImpl
    extends AbstractJSONHandler
    implements GatewayListener
{
    private final static Logger logger = Logger.getLogger(HandlerImpl.class);

    private static final String CONFIGURE_MUC_TARGET
        = "/configure/call-control-muc";

    private static final String SHUTDOWN_TARGET = "/about/shutdown";

    private static final String STATISTICS_TARGET = "/about/stats";

    private static final String DEBUG_TARGET = "/debug";

    private final boolean shutdownEnabled;

    protected HandlerImpl(BundleContext bundleContext, boolean enableShutdown)
    {
        super(bundleContext);

        shutdownEnabled = enableShutdown;

        List<AbstractGateway> gatewayList
            = JigasiBundleActivator.getAvailableGateways();
        gatewayList.forEach(gw -> gw.addGatewayListener(this));

        if (gatewayList.isEmpty())
        {
            logger.error("No gateways found. "
                + "Total statistics count will be missing!");
        }
    }

    @Override
    protected void doGetHealthJSON(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException
    {
        beginResponse(null, baseRequest, request, response);

        if (JigasiBundleActivator.getAvailableGateways()
            .stream().anyMatch(g -> !g.isReady()))
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        else
        {
            sendJSON(response);
        }

        endResponse(null, baseRequest, request, response);
    }

    @Override
    protected void handleJSON(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException, ServletException
    {
        super.handleJSON(target, baseRequest, request, response);

        if (baseRequest.isHandled())
            return;

        int oldResponseStatus = response.getStatus();
        beginResponse(target, baseRequest, request, response);

        if (SHUTDOWN_TARGET.equals(target))
        {
            if (!shutdownEnabled)
            {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            if (POST_HTTP_METHOD.equals(request.getMethod()))
            {
                response.setStatus(HttpServletResponse.SC_OK);

                new Thread(JigasiBundleActivator::enableGracefulShutdownMode).start();
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }
        else if (STATISTICS_TARGET.equals(target))
        {
            if (GET_HTTP_METHOD.equals(request.getMethod()))
            {
                doGetStatisticsJSON(baseRequest, request, response);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }
        else if (DEBUG_TARGET.equals(target))
        {
            if (GET_HTTP_METHOD.equals(request.getMethod()))
            {
                doGetDebugJSON(baseRequest, request, response);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }
        else if (target.startsWith(CONFIGURE_MUC_TARGET + "/"))
        {
            doHandleConfigureMucRequest(
                target.substring((CONFIGURE_MUC_TARGET + "/").length()),
                request,
                response);
        }

        int newResponseStatus = response.getStatus();

        if (newResponseStatus == HttpServletResponse.SC_NOT_IMPLEMENTED)
        {
            response.setStatus(oldResponseStatus);
        }
        else
        {
            endResponse(target, baseRequest, request, response);
        }
    }

    private void doGetDebugJSON(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException
    {
        OrderedJsonObject debugState = new OrderedJsonObject();
        JSONObject gatewaysJson = new JSONObject();
        debugState.put("gateways", gatewaysJson);
        List<AbstractGateway> gateways
            = JigasiBundleActivator.getAvailableGateways();
        gateways.forEach(gw -> gatewaysJson.put(gw.hashCode(), gw.getDebugState()));

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.print(debugState.toJSONString());
    }
    private void doGetStatisticsJSON(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException
    {
        if (JigasiBundleActivator.getAvailableGateways().isEmpty())
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        else
        {
            Statistics.sendJSON(baseRequest, request, response);
        }
    }

    @Override
    public void onSessionRemoved(AbstractGatewaySession session)
    {
        Statistics.addTotalConferencesCount(1);
        Statistics.addTotalParticipantsCount(
            session.getParticipantsCount() - 1);
        Statistics.addCumulativeConferenceSeconds(
            TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis()
                    - session.getCallContext().getTimestamp()));
    }

    static synchronized void sendJSON(
        HttpServletResponse response)
        throws IOException
    {
        int status;
        String reason = null;
        Map<String, Object> responseMap = new HashMap<>();
        try
        {
            Health.check();
            status = HttpServletResponse.SC_OK;
        }
        catch (Exception e)
        {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            reason = e.getMessage();

            logger.error("Health check failed", e);
        }

        if (reason != null)
        {
            responseMap.put("reason", reason);
        }
        response.setStatus(status);
        new JSONObject(responseMap).writeJSONString(response.getWriter());
    }

    private void doHandleConfigureMucRequest(
        String target,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException
    {
        if (GET_HTTP_METHOD.equals(request.getMethod())
            && "list".equals(target))
        {
            response.setStatus(HttpServletResponse.SC_OK);
            JSONArray.writeJSONString(
                CallControlMucActivator.listCallControlMucAccounts(),
                response.getWriter());
            return;
        }

        if (!POST_HTTP_METHOD.equals(request.getMethod()))
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (!RESTUtil.isJSONContentType(request.getContentType()))
        {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        JSONObject requestJSONObject;
        try
        {
            Object o = new JSONParser().parse(request.getReader());
            if (o instanceof JSONObject)
            {
                requestJSONObject = (JSONObject) o;
            }
            else
            {
                requestJSONObject = null;
            }
        }
        catch (Exception e)
        {
            requestJSONObject = null;
        }

        if (requestJSONObject == null
            || !(requestJSONObject.get("id") instanceof String))
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String id = (String) requestJSONObject.get("id");

        if ("add".equals(target))
        {
            try
            {
                CallControlMucActivator.addCallControlMucAccount(
                    id, requestJSONObject);
            }
            catch(OperationFailedException e)
            {
                logger.error("Failed to add account:" + id, e);
                response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }
        else if ("remove".equals(target))
        {
            if (CallControlMucActivator.removeCallControlMucAccount(id))
            {
                response.setStatus(HttpServletResponse.SC_OK);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
