package org.diploma;

import org.apache.commons.lang3.StringUtils;
import com.google.common.base.*;

import org.jitsi.utils.logging.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import java.util.*;

public class CallContext
{
    private final static Logger logger = Logger.getLogger(CallContext.class);

    private static final String P_NAME_MUC_SERVICE_ADDRESS = "org.diploma.MUC_SERVICE_ADDRESS";

    private static final String P_NAME_BOSH_HOST_OVERRIDE = "org.diploma.BOSH_HOST_OVERRIDE";

    private static Map<String, String> boshHostsOverrides = null;

    public static final String BOSH_URL_ACCOUNT_PROP = "BOSH_URL_PATTERN";

    public static final String MUC_DOMAIN_PREFIX_PROP = "MUC_DOMAIN_PREFIX";

    public static final String DOMAIN_BASE_ACCOUNT_PROP = "DOMAIN_BASE";

    private static final Random RANDOM = new Random();

    private EntityBareJid roomJid;

    private String roomJidDomain;

    private String domain;

    private String tenant;

    private String roomPassword;

    private String authToken;

    private String authUserId;

    private String boshURL;

    private String destination;

    private List<String> mucAddressPrefixes = Arrays.asList(new String[]{"conference", "muc"}) ;

    private final long timestamp;

    private Jid callResource;

    private Jid customCallResource = null;

    private final Object source;

    private final String ctxId;

    private final Map<String, String> extraHeaders = new HashMap<>();

    public CallContext(Object source)
    {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
        this.ctxId = this.timestamp + String.valueOf(super.hashCode());

        if (boshHostsOverrides == null)
        {
            String stringMap = JigasiBundleActivator.getConfigurationService()
                .getString(P_NAME_BOSH_HOST_OVERRIDE, null);
            if (stringMap != null)
            {
                boshHostsOverrides = Splitter.on(",").withKeyValueSeparator("=").split(stringMap);
            }
            else
            {
                boshHostsOverrides = new HashMap<>();
            }
        }
    }

    public EntityBareJid getRoomJid()
    {
        return this.roomJid;
    }

    public void setRoomName(String roomName)
        throws XmppStringprepException
    {
        if (!roomName.contains("@"))
        {
            String mucService = JigasiBundleActivator.getConfigurationService()
                .getString(P_NAME_MUC_SERVICE_ADDRESS, null);
            if (mucService != null)
            {
                roomName = roomName + "@" + mucService;
            }
        }

        this.roomJid = JidCreate.entityBareFrom(roomName);

        update();
    }

    public String getConferenceName()
    {
        return this.roomJid.getLocalpart().toString();
    }

    public String getDomain()
    {
        return domain;
    }

    public void setDomain(String domain)
    {
        if (domain == null)
            return;

        this.domain = domain;
        update();
    }

    public String getRoomJidDomain()
    {
        return this.roomJidDomain;
    }

    public void setTenant(String tenant)
    {
        this.tenant = tenant;
        update();
    }

    public String getTenant()
    {
        return tenant;
    }

    public String getRoomPassword()
    {
        return roomPassword;
    }

    public void setRoomPassword(String roomPassword)
    {
        this.roomPassword = roomPassword;
    }

    public void setAuthToken(String token)
    {
        this.authToken = token;
    }

    public boolean hasAuthToken()
    {
        return this.authToken != null;
    }

    public void setAuthUserId(String userId)
    {
        this.authUserId = userId;
    }

    public String getAuthUserId()
    {
        return this.authUserId;
    }

    public String getBoshURL()
    {
        return boshURL;
    }

    public void setBoshURL(String boshURL)
    {
        this.boshURL = boshURL;
        update();
    }

    public String getDestination()
    {
        return destination;
    }

    public void setDestination(String destination)
    {
        this.destination = destination;
    }

    public void setMucAddressPrefix(String mucAddressPrefix)
    {
        if (mucAddressPrefix == null)
        {
            return;
        }

        this.mucAddressPrefixes = Arrays.asList(mucAddressPrefix.split(","));

        update();
    }

    public Jid getCallResource()
    {
        if (customCallResource != null)
            return customCallResource;

        return callResource;
    }

    public void setCustomCallResource(Jid customCallResource)
    {
        this.customCallResource = customCallResource;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    void updateCallResource()
    {
        if (this.roomJidDomain != null)
        {
            try
            {
                long random = RANDOM.nextInt() & 0xffff_ffff;
                this.callResource = JidCreate.entityBareFrom(
                    String.format("%8h", random).replace(' ', '0')
                    + "@"
                    + (this.tenant != null ? this.tenant + "." : "")
                    + this.roomJidDomain);
            }
            catch (XmppStringprepException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private void update()
    {
        String subdomain = "";

        if (this.getRoomJid() != null)
        {

            String mucAddress = this.getRoomJid().getDomain().toString();

            mucAddressPrefixes.forEach(prefix -> {
                if (mucAddress.startsWith(prefix))
                {
                    String strippedMucAddress =  mucAddress.substring(prefix.length() + 1);

                    if (this.tenant != null && strippedMucAddress.startsWith(this.tenant))
                    {
                        this.roomJidDomain = strippedMucAddress.substring(this.tenant.length() + 1);
                    }
                    else if (this.domain != null && strippedMucAddress.endsWith(this.domain)
                        && strippedMucAddress.length() > this.domain.length() + 1)
                    {
                        this.roomJidDomain = this.domain;
                        this.tenant = strippedMucAddress.substring(
                            0, strippedMucAddress.indexOf(this.domain) - 1);
                    }
                    else
                    {
                        this.roomJidDomain = strippedMucAddress;
                    }
                }
            });

            if (this.tenant != null)
            {
                subdomain = "/" + this.tenant;
            }

            if (this.roomJidDomain == null)
            {
                logger.warn("No roomJidDomain extracted from roomJid:" + this.getRoomJid() + ", tenant:" + this.tenant);
                this.roomJidDomain = this.domain;
            }

            if (boshURL != null && StringUtils.isNotEmpty(StringUtils.trim(domain)))
            {
                String boshHost = domain;

                String override = boshHostsOverrides.get(domain);
                if (override != null && override.length() > 0)
                {
                    boshHost = override;
                }

                boshURL = boshURL.replace("{host}", boshHost);

                boshURL = boshURL.replace("{subdomain}", subdomain);
            }
        }

        if (this.authToken != null && boshURL != null && !boshURL.contains("&token="))
        {
            boshURL = boshURL + "&token=" + this.authToken;
        }

        this.updateCallResource();
    }

    public Object getSource()
    {
        return source;
    }

    public String getMeetingUrl()
    {
        String url = getBoshURL();
        String room = getConferenceName();

        if (url == null || room == null)
        {
            return null;
        }

        url = url.substring(0, url.indexOf("/http-bind"));

        return url + "/" + room;
    }

    @Override
    public String toString()
    {
        return "[ctx=" + ctxId + ']';
    }

    public synchronized void addExtraHeader(String name, String value)
    {
        if (!this.extraHeaders.containsKey(name))
        {
            this.extraHeaders.put(name, value);
        }
    }

    public Map<String, String> getExtraHeaders()
    {
        return Collections.unmodifiableMap(this.extraHeaders);
    }
}
