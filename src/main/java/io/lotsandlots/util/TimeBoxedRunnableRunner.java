package io.lotsandlots.util;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeBoxedRunnableRunner<T extends Runnable> implements Runnable {

    private final long period;
    private final T runnable;
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);

    public TimeBoxedRunnableRunner(T runnable, long initialDelay, long period, TimeUnit timeUnit) {
        this.period = period;
        this.runnable = runnable;
        scheduledExecutor.scheduleAtFixedRate(this, initialDelay, period, timeUnit);
    }

    public T getRunnable() {
        return runnable;
    }

    @Override
    public void run() {
        Future<?> future = scheduledExecutor.submit(runnable);
        scheduledExecutor.schedule(() -> {
            future.cancel(true);
        }, period, TimeUnit.SECONDS);
    }
}
