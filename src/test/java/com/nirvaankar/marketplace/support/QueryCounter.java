package com.nirvaankar.marketplace.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

/**
 * Turns "did this endpoint N+1?" into an assertion instead of an opinion.
 * Backed by Hibernate's own statistics, so it counts what actually reached
 * the database rather than what we think the code does.
 */
@Component
public class QueryCounter {

    private final Statistics statistics;

    public QueryCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    public void reset() {
        statistics.clear();
    }

    public long getQueryCount() {
        return statistics.getPrepareStatementCount();
    }
}
