package org.diploma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

public class CallStateListener
    extends CallChangeAdapter
{
    private CallState targetState;

    @Override
    public void callStateChanged(CallChangeEvent evt)
    {
        Call call = evt.getSourceCall();
        if (targetState.equals(call.getCallState()))
        {
            synchronized (this)
            {
                this.notifyAll();
            }
        }
    }

    public void waitForState(Call      watchedCall,
                             CallState targetState,
                             long      timeout)
        throws InterruptedException
    {
        this.targetState = targetState;

        if (!targetState.equals(watchedCall.getCallState()))
        {
            synchronized (this)
            {
                watchedCall.addCallChangeListener(this);

                this.wait(timeout);
            }
        }

        watchedCall.removeCallChangeListener(this);

        assertEquals(targetState, watchedCall.getCallState());
    }
}
