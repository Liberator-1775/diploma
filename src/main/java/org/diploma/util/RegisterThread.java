package org.diploma.util;

import net.java.sip.communicator.service.protocol.*;
import org.diploma.ServerSecurityAuthority;
import org.diploma.*;
import org.jitsi.utils.logging.Logger;

public class RegisterThread
    extends Thread
{
    private final static Logger logger
        = Logger.getLogger(RegisterThread.class);

    private final ProtocolProviderService pps;

    private final String password;

    public RegisterThread(ProtocolProviderService pps)
    {
        this(pps, null);
    }

    public RegisterThread(ProtocolProviderService pps, String password)
    {
        this.pps = pps;
        this.password = password;
    }

    @Override
    public void run()
    {
        try
        {
            pps.register(new ServerSecurityAuthority(pps, password));
        }
        catch (OperationFailedException e)
        {
            logger.error(e, e);
        }
    }
}
