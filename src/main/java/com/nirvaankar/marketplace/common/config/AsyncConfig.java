package com.nirvaankar.marketplace.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Every executor in the application is declared here, bounded, and named.
 * <p>
 * Rules this class exists to enforce:
 * <ul>
 *   <li>Never the default SimpleAsyncTaskExecutor - it spawns an unbounded
 *       thread per task and dies under a traffic spike.</li>
 *   <li>Never one shared pool - a slow SMS provider must not be able to stall
 *       the outbox publisher.</li>
 *   <li>Bounded queue + CallerRunsPolicy = backpressure. An unbounded queue is
 *       an OutOfMemoryError waiting for a sale day.</li>
 *   <li>Every {@code @Async} must name its executor. A bare {@code @Async}
 *       falls back to the default and is a review rejection.</li>
 * </ul>
 * The total number of DB-touching worker threads across these pools is
 * documented against the Hikari pool size in application.yml. If you add a
 * pool here, update that comment.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String OUTBOX_EXECUTOR = "outboxExecutor";
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";
    public static final String MAIL_EXECUTOR = "mailExecutor";
    public static final String AUDIT_EXECUTOR = "auditExecutor";
    public static final String SEARCH_INDEX_EXECUTOR = "searchIndexExecutor";
    public static final String WEBHOOK_RETRY_EXECUTOR = "webhookRetryExecutor";
    public static final String REPORT_EXECUTOR = "reportExecutor";

    private final TaskDecorator taskDecorator = new ContextPropagatingTaskDecorator();

    @Bean(name = OUTBOX_EXECUTOR)
    public ThreadPoolTaskExecutor outboxExecutor() {
        return buildExecutor("outbox-", 4, 8, 500);
    }

    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        return buildExecutor("notify-", 2, 6, 1000);
    }

    /** Dedicated pool for SMTP / SES — must never share with outbox or WhatsApp. */
    @Bean(name = MAIL_EXECUTOR)
    public ThreadPoolTaskExecutor mailExecutor() {
        return buildExecutor("mail-", 2, 4, 500);
    }

    @Bean(name = AUDIT_EXECUTOR)
    public ThreadPoolTaskExecutor auditExecutor() {
        return buildExecutor("audit-", 2, 4, 2000);
    }

    @Bean(name = SEARCH_INDEX_EXECUTOR)
    public ThreadPoolTaskExecutor searchIndexExecutor() {
        return buildExecutor("search-", 2, 4, 1000);
    }

    @Bean(name = WEBHOOK_RETRY_EXECUTOR)
    public ThreadPoolTaskExecutor webhookRetryExecutor() {
        return buildExecutor("webhook-", 2, 4, 500);
    }

    @Bean(name = REPORT_EXECUTOR)
    public ThreadPoolTaskExecutor reportExecutor() {
        return buildExecutor("report-", 1, 2, 50);
    }

    private ThreadPoolTaskExecutor buildExecutor(String namePrefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(namePrefix);
        executor.setTaskDecorator(taskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Deliberately returns null so that a bare {@code @Async} has no default
     * to fall back to and fails loudly instead of quietly using a pool nobody
     * sized.
     */
    @Override
    public Executor getAsyncExecutor() {
        return null;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Uncaught exception in async method {}", method.getName(), throwable);
    }
}
