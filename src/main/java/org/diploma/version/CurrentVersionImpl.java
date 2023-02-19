package org.diploma.version;

import org.jitsi.utils.version.*;

import java.util.regex.*;

public class CurrentVersionImpl
{
    private static int parsedMajor = 1;
    private static int parsedMinor = 1;
    private static String parsedBuildId = null;

    static {
        String version = CurrentVersionImpl.class.getPackage()
            .getImplementationVersion();
        if (version != null)
        {
            Matcher m
                = Pattern.compile("(\\d*)\\.(\\d*)-(.*)").matcher(version);
            if (m.find())
            {
                try
                {
                    parsedMajor = Integer.parseInt(m.group(1));
                }
                catch (NumberFormatException nfe) {}

                try
                {
                    parsedMinor = Integer.parseInt(m.group(2));
                }
                catch (NumberFormatException nfe) {}

                parsedBuildId = m.group(3);
            }
        }
    }

    public static final int VERSION_MAJOR = parsedMajor;

    public static final int VERSION_MINOR = parsedMinor;

    public static final String PRE_RELEASE_ID = null;

    public static final String NIGHTLY_BUILD_ID
        = parsedBuildId != null ? parsedBuildId : "build.git";

    public static final Version VERSION
            = new VersionImpl(
            "Jigasi",
            VERSION_MAJOR,
            VERSION_MINOR,
            NIGHTLY_BUILD_ID,
            PRE_RELEASE_ID);
}
