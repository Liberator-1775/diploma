package org.diploma.osgi;

import java.util.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.service.configuration.*;

public class JigasiBundleConfig
{
    private JigasiBundleConfig()
    {
        // prevent instances
    }

    private static Map<String, String> getSystemPropertyDefaults()
    {
        Map<String, String> defaults = new HashMap<>();
        String true_ = Boolean.toString(true);
        String false_ = Boolean.toString(false);

        defaults.put(
            "net.java.sip.communicator.service.media.DISABLE_VIDEO_SUPPORT",
            true_);

        defaults.put(
            MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME,
            false_);

        defaults.put(
            DeviceConfiguration.PROP_AUDIO_SYSTEM,
            AudioSystem.LOCATOR_PROTOCOL_AUDIOSILENCE);
        defaults.put(
            "org.jitsi.impl.neomedia.device.PortAudioSystem.disabled",
            true_);
        defaults.put(
            "org.jitsi.impl.neomedia.device.PulseAudioSystem.disabled",
            true_);

        defaults.put(
            OperationSetTelephonyConferencingJabberImpl.DISABLE_COIN_PROP_NAME,
            true_);

        defaults.put(
            DeviceConfiguration.PROP_VIDEO_RTP_PACING_THRESHOLD,
            Integer.toString(Integer.MAX_VALUE));

        defaults.put(
            SsrcTransformEngine
                .DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
            true_);

        defaults.put(
            ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
            System.getProperty(
                ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
                true_));

        return defaults;
    }

    public static void setSystemPropertyDefaults()
    {
        Map<String, String> defaults = getSystemPropertyDefaults();

        for (Map.Entry<String, String> e : defaults.entrySet())
        {
            String key = e.getKey();

            if (System.getProperty(key) == null)
                System.setProperty(key, e.getValue());
        }
    }
}
