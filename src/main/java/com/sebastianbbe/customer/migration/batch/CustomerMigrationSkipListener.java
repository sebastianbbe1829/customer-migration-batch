package com.sebastianbbe.customer.migration.batch;

import com.sebastianbbe.customer.migration.domain.LegacyCustomerEntity;
import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

@Component
public class CustomerMigrationSkipListener implements SkipListener<LegacyCustomerEntity, TargetCustomerEntity> {

    private static final Logger log = LoggerFactory.getLogger(CustomerMigrationSkipListener.class);

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Customer skipped while reading: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(LegacyCustomerEntity item, Throwable t) {
        log.warn("Customer skipped during processing. legacyId={}, document={}, reason={}",
                item.getId(), item.getDocumentNumber(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(TargetCustomerEntity item, Throwable t) {
        log.warn("Customer skipped during writing. document={}, reason={}",
                item.getDocumentNumber(), t.getMessage());
    }
}
