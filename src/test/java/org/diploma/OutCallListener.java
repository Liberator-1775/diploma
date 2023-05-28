package org.diploma;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

public class OutCallListener
    implements CallListener
{
    private Call outgoingCall;

    private OperationSetBasicTelephony<?> telephony;

    public void bind(OperationSetBasicTelephony<?> telephony)
    {
        this.telephony = telephony;

        telephony.addCallListener(this);
    }

    public Call getOutgoingCall(long timeout)
        throws InterruptedException
    {
        synchronized (this)
        {
            if (outgoingCall == null)
            {
                this.wait(timeout);
            }
            return outgoingCall;
        }
    }

    @Override
    public void incomingCallReceived(CallEvent event){ }

    @Override
    public void outgoingCallCreated(CallEvent event)
    {
        telephony.removeCallListener(this);

        synchronized (this)
        {
            outgoingCall = event.getSourceCall();

            this.notifyAll();
        }
    }

    @Override
    public void callEnded(CallEvent event){ }
}
