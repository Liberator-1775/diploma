package org.diploma;

import net.java.sip.communicator.service.protocol.media.*;
import org.diploma.util.Util;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.diploma.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;

import java.util.*;

public class SipCallKeepAliveTransformer
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    private Set<Long> seenSSRCs = Collections.synchronizedSet(new HashSet<>());

    private static final RecurringRunnableExecutor EXECUTOR
        = new RecurringRunnableExecutor(KeepAliveIncomingMedia.class.getName());

    private KeepAliveIncomingMedia recurringMediaChecker
        = new KeepAliveIncomingMedia(15000);

    private long lastOutgoingActivity;

    private final CallPeerMediaHandler handler;

    private final MediaStream stream;

    public SipCallKeepAliveTransformer(
        CallPeerMediaHandler handler, MediaStream stream)
    {
        super(RTPPacketPredicate.INSTANCE);

        this.handler = handler;
        this.stream = stream;

        EXECUTOR.registerRecurringRunnable(recurringMediaChecker);
    }

    void dispose()
    {
        EXECUTOR.deRegisterRecurringRunnable(recurringMediaChecker);
    }

    @Override
    public RawPacket transform(RawPacket pkt)
    {
        lastOutgoingActivity = System.currentTimeMillis();
        seenSSRCs.add(pkt.getSSRCAsLong());

        return pkt;
    }

    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        seenSSRCs.add(pkt.getSSRCAsLong());

        return pkt;
    }

    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    public class RTCPTransformer
        extends SinglePacketTransformerAdapter
    {
        RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            RTCPIterator it = new RTCPIterator(pkt);
            while (it.hasNext())
            {
                ByteArrayBuffer baf = it.next();
                int type = RTCPUtils.getPacketType(baf);
                long ssrc = RawPacket.getRTCPSSRC(baf);

                if (!seenSSRCs.contains(ssrc) && type == 203)
                {
                    it.remove();
                }
            }

            if (pkt.getLength() > 0)
            {
                lastOutgoingActivity = System.currentTimeMillis();
                return pkt;
            }

            return null;
        }
    }

    private class KeepAliveIncomingMedia
        extends PeriodicRunnable
    {
        private long ts = new Random().nextInt() & 0xFFFFFFFFL;

        private int seqNum = new Random().nextInt(0xFFFF);

        private long NO_MEDIA_THRESHOLD = 20000;

        public KeepAliveIncomingMedia(long period)
        {
            super(period);
        }

        @Override
        public void run()
        {
            super.run();

            try
            {
                if (System.currentTimeMillis() - lastOutgoingActivity
                        > NO_MEDIA_THRESHOLD)
                {
                    for (int i=0; i < 3; i++)
                    {
                        RawPacket packet = Util.makeRTP(
                            stream.getLocalSourceID(),
                            13,
                            seqNum++,
                            ts,
                            RawPacket.FIXED_HEADER_SIZE + 1);

                        stream.injectPacket(
                            packet, true, SipCallKeepAliveTransformer.this);

                        ts += 160;
                    }
                    lastOutgoingActivity = System.currentTimeMillis();
                }
            }
            catch(Throwable e)
            {}
        }
    }
}
