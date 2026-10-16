package com.example.provider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    /** Runs the timed status changes and callback retries. Tasks here never block on HTTP. */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService providerScheduler() {
        return Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("provider-scheduler-", 0).daemon().factory());
    }

    /** Sends callbacks over HTTP. One virtual thread per send, so slow backends don't delay the scheduler. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService callbackExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("callback-", 0).factory());
    }
}
