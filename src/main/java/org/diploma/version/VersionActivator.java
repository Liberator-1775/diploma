package org.diploma.version;

import org.jitsi.utils.version.*;

public class VersionActivator
        extends AbstractVersionActivator
{
    @Override
    protected Version getCurrentVersion()
    {
        return CurrentVersionImpl.VERSION;
    }
}
