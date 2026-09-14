package com.nirvaankar.marketplace.common.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * Neither the SecurityContext nor the MDC crosses a thread boundary on its own.
 * Without this decorator, every async audit entry loses its actor and every
 * async log line loses its traceId, which makes production incidents
 * untraceable. Applied to every executor in {@link AsyncConfig}.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        SecurityContext callerSecurityContext = SecurityContextHolder.getContext();

        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try {
                if (callerMdc != null) {
                    MDC.setContextMap(callerMdc);
                }
                SecurityContextHolder.setContext(callerSecurityContext);
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
                MDC.clear();
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                }
            }
        };
    }
}
