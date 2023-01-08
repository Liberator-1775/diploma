package org.diploma;

import net.java.sip.communicator.service.protocol.*;
import org.apache.commons.lang3.*;
import org.jitsi.utils.logging.*;

public class ServerSecurityAuthority
    implements SecurityAuthority
{
    private final static Logger logger
        = Logger.getLogger(ServerSecurityAuthority.class);

    private final String password;

    private final ProtocolProviderService provider;

    public ServerSecurityAuthority(
        ProtocolProviderService provider, String password)
    {
        this.password = password;
        this.provider = provider;
    }

    @Override
    public UserCredentials obtainCredentials(String realm,
                                             UserCredentials defaultValues,
                                             int reasonCode)
    {
        if (reasonCode == WRONG_PASSWORD
            || reasonCode == WRONG_USERNAME)
        {
            logger.error(
                "Wrong username or password for provider:" + this.provider);
            return null;
        }

        if (StringUtils.isNotEmpty(StringUtils.trim(password)))
        {
            defaultValues.setPassword(password.toCharArray());
        }

        return defaultValues;
    }

    @Override
    public UserCredentials obtainCredentials(String realm,
                                             UserCredentials defaultValues)
    {
        return defaultValues;
    }

    @Override
    public void setUserNameEditable(boolean isUserNameEditable)
    {

    }

    @Override
    public boolean isUserNameEditable()
    {
        return false;
    }
}
