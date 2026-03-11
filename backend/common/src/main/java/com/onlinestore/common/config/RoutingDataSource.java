package com.onlinestore.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(RoutingDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        DataSourceType selected = readOnly
            ? DataSourceType.REPLICA
            : DataSourceType.PRIMARY;
        log.debug("Routing datasource decision: selected={}, readOnly={}", selected, readOnly);
        return selected;
    }
}
