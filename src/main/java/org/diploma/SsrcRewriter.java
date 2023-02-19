package org.diploma;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

public class SsrcRewriter
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    private final long ssrc;

    public SsrcRewriter(long ssrc)
    {
        super(RTPPacketPredicate.INSTANCE);
        this.ssrc = ssrc;
    }

    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (pkt.getLength() >= 12)
            pkt.setSSRC((int)this.ssrc);
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
            if (pkt.getLength() >= 8)
                pkt.writeInt(4, (int)SsrcRewriter.this.ssrc);
            return pkt;
        }
    }
}
