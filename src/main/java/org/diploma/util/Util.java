package org.diploma.util;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.bosh.*;
import org.jivesoftware.smack.packet.*;

import java.lang.reflect.*;
import java.util.*;

import java.math.*;
import java.security.*;
import java.util.concurrent.*;

public class Util
{
    public static MediaFormat getFirstPeerMediaFormat(Call call)
    {
        if (!(call instanceof MediaAwareCall))
            return null;

        MediaAwareCall mediaCall = (MediaAwareCall) call;
        if (mediaCall.getCallPeerCount() == 0)
            return null;

        CallPeer peer
            = (CallPeer) mediaCall.getCallPeerList().get(0);
        if (!(peer instanceof MediaAwareCallPeer))
            return null;

        MediaAwareCallPeer mediaPeer
            = (MediaAwareCallPeer) peer;

        CallPeerMediaHandler peerMediaHndl
            = mediaPeer.getMediaHandler();
        if (peerMediaHndl == null)
            return null;

        MediaStream peerStream
            = peerMediaHndl.getStream(MediaType.AUDIO);

        if (peerStream == null)
            return null;

        return peerStream.getFormat();
    }

    public static String stringToMD5hash(String toHash)
    {
        try
        {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.reset();
            m.update(toHash.getBytes());
            byte[] digest = m.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            String hashtext = bigInt.toString(16);

            if (hashtext.length() < 32)
            {
                int padLength = 32 - hashtext.length();
                String pad = String.join("",
                    Collections.nCopies(padLength, "0"));
                hashtext = pad + hashtext;
            }

            return hashtext;
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static Object getConnSessionId(Object connection)
    {
        Field myField = getField(XMPPBOSHConnection.class, "sessionID");

        if (myField != null)
        {
            myField.setAccessible(true);
            try
            {
                return myField.get(connection);
            }
            catch( Exception e)
            {
                Logger.getLogger(Util.class).error("cannot read it", e);
            }
        }

        return null;
    }

    private static Field getField(Class clazz, String fieldName)
    {
        try
        {
            return clazz.getDeclaredField(fieldName);
        }
        catch (NoSuchFieldException e)
        {
            Class superClass = clazz.getSuperclass();
            if (superClass == null)
            {
                return null;
            }
            else
            {
                return getField(superClass, fieldName);
            }
        }
    }

    public static RawPacket makeRTP(
        long ssrc, int pt, int seqNum, long ts, int len)
    {
        byte[] buf = new byte[len];

        RawPacket pkt = new RawPacket(buf, 0, buf.length);

        pkt.setVersion();
        pkt.setPayloadType((byte) pt);
        pkt.setSSRC((int) ssrc);
        pkt.setTimestamp(ts);
        pkt.setSequenceNumber(seqNum);

        return pkt;
    }

    public static ExecutorService createNewThreadPool(String name)
    {
        return new ThreadPoolExecutor(
            1, 1000,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new CustomizableThreadFactory(name, true));
    }

    public static ExtensionElement createFeature(String var)
    {
        FeatureExtension feature = new FeatureExtension();
        feature.setAttribute("var", var);

        return feature;
    }
}
