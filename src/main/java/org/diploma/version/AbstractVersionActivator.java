package org.diploma.version;

import org.jitsi.utils.version.*;
import org.jitsi.utils.version.Version;

import org.jitsi.utils.logging.*;
import org.osgi.framework.*;

import java.util.regex.*;

public abstract class AbstractVersionActivator
    implements BundleActivator
{
    private final Logger logger
        = Logger.getLogger(AbstractVersionActivator.class);

    private static final Pattern PARSE_VERSION_STRING_PATTERN
        = Pattern.compile("(\\d+)\\.(\\d+)\\.([\\d\\.]+)");

    abstract protected Version getCurrentVersion();

    public void start(BundleContext context) throws Exception
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Started.");
        }

        Version currentVersion = getCurrentVersion();

        VersionServiceImpl versionServiceImpl
            = new VersionServiceImpl(currentVersion);

        context.registerService(
            VersionService.class.getName(),
            versionServiceImpl,
            null);

        logger.info("VersionService registered: "
            + currentVersion.getApplicationName() + " " + currentVersion);
    }

    public void stop(BundleContext context) throws Exception
    {
    }

    static class VersionServiceImpl
        implements VersionService
    {
        private final Version version;

        private VersionServiceImpl(Version version)
        {
            this.version = version;
        }

        @Override
        public Version parseVersionString(String versionString)
        {
            Matcher matcher
                = PARSE_VERSION_STRING_PATTERN.matcher(versionString);

            if (matcher.matches() && matcher.groupCount() == 3)
            {
                return new VersionImpl(
                    version.getApplicationName(),
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3),
                    version.getPreReleaseID());
            }

            return null;
        }

        @Override
        public Version getCurrentVersion()
        {
            return version;
        }
    }
}
