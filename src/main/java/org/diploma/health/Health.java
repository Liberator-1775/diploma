package org.diploma.health;

import org.diploma.*;
import org.diploma.*;
import org.jitsi.utils.concurrent.*;

public class Health
{
    private static final RecurringRunnableExecutor executor
        = new RecurringRunnableExecutor(Health.class.getName());

    private static SipHealthPeriodicChecker sipChecker = null;

    private static boolean hasSipGw = false;

    public static void start()
    {
        SipGateway sipGateway = null;
        for (AbstractGateway gw : JigasiBundleActivator.getAvailableGateways())
        {
            if (gw instanceof SipGateway)
                sipGateway = (SipGateway) gw;
        }

        if (sipGateway != null)
        {
            hasSipGw = true;

            if (sipGateway.isReady())
            {
                sipChecker = SipHealthPeriodicChecker.create(sipGateway);
                if (sipChecker != null)
                {
                    executor.registerRecurringRunnable(sipChecker);
                }
            }
            else
            {
                final SipGateway finalSipGw = sipGateway;
                sipGateway.addGatewayListener(new GatewayListener()
                {
                    @Override
                    public void onReady()
                    {
                        finalSipGw.removeGatewayListener(this);
                        start();
                    }
                });
            }
        }
    }

    public static void stop()
    {
        if (sipChecker != null)
        {
            executor.deRegisterRecurringRunnable(sipChecker);
            sipChecker = null;
        }
    }

    public static void check()
        throws Exception
    {
        if (!CallManager.isHealthy())
        {
            throw new Exception("CallManager is not healthy.");
        }

        if (sipChecker != null)
        {
            sipChecker.check();
        }
        else
        {
            if (hasSipGw)
            {
                throw new Exception("GW not ready.");
            }

            if (JigasiBundleActivator.getAvailableGateways().isEmpty())
            {
                throw new Exception("No gateways configured.");
            }
        }
    }
}
