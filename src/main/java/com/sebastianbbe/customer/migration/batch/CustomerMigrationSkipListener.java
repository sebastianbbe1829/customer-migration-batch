package com.sebastianbbe.customer.migration.batch;

import com.sebastianbbe.customer.migration.domain.LegacyCustomerEntity;
import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CustomerMigrationSkipListener
        implements SkipListener<LegacyCustomerEntity, TargetCustomerEntity>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerMigrationSkipListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private Long jobExecutionId;
    private Long stepExecutionId;

    public CustomerMigrationSkipListener(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.jobExecutionId = stepExecution.getJobExecutionId();
        this.stepExecutionId = stepExecution.getId();
    }

    @Override
    public void onSkipInRead(Throwable t) {
        logAndPersist(null, null, "READ", t);
    }

    @Override
    public void onSkipInProcess(LegacyCustomerEntity item, Throwable t) {
        Long legacyId = item != null ? item.getId() : null;
        String document = item != null ? item.getDocumentNumber() : null;

        logAndPersist(legacyId, document, "PROCESS", t);
    }

    @Override
    public void onSkipInWrite(TargetCustomerEntity item, Throwable t) {
        String document = item != null ? item.getDocumentNumber() : null;

        logAndPersist(null, document, "WRITE", t);
    }

    private void logAndPersist(Long legacyId, String document, String stage, Throwable t) {
        String errorType = t != null ? t.getClass().getSimpleName() : "UnknownException";
        String errorMessage = t != null && t.getMessage() != null
                ? t.getMessage()
                : "No error message available";

        log.warn(
                "Customer skipped. stage={}, legacyId={}, document={}, errorType={}, reason={}",
                stage, legacyId, document, errorType, errorMessage);

        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                INSERT INTO target.migration_errors (
                    job_execution_id,
                    step_execution_id,
                    legacy_customer_id,
                    document_number,
                    stage,
                    error_type,
                    error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                jobExecutionId,
                stepExecutionId,
                legacyId,
                document,
                stage,
                errorType,
                errorMessage));
    }
}
