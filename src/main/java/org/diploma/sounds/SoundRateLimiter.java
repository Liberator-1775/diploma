package org.diploma.sounds;

import java.time.*;
import java.util.concurrent.atomic.*;

class SoundRateLimiter
{
    private final AtomicReference<Instant> startTimePoint = new AtomicReference<>(null);

    private long limiterTimeout;

    SoundRateLimiter(long maxTimeout)
    {
        this.limiterTimeout = maxTimeout;
    }

    public boolean on()
    {
        if (this.startTimePoint.compareAndSet(null, Instant.now()))
        {
            return false;
        }

        Instant prevTimePoint = this.startTimePoint.get();

        if (prevTimePoint == null)
        {
            return false;
        }

        long elapsedMs =
            Instant.now().toEpochMilli()
                - prevTimePoint.toEpochMilli();

        if (elapsedMs >= this.limiterTimeout)
        {
            this.startTimePoint.set(Instant.now());
            return false;
        }

        return true;
    }

    public void reset()
    {
        this.startTimePoint.set(null);
    }
}
