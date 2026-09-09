package com.sebastianbbe.customer.migration.batch;

import com.sebastianbbe.customer.migration.domain.LegacyCustomerEntity;
import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CustomerMigrationPerformanceListener implements
        ItemReadListener<LegacyCustomerEntity>,
        ItemProcessListener<LegacyCustomerEntity, TargetCustomerEntity>,
        ItemWriteListener<TargetCustomerEntity>,
        StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerMigrationPerformanceListener.class);

    private final JdbcTemplate jdbcTemplate;

    private final AtomicLong readNanos = new AtomicLong();
    private final AtomicLong processNanos = new AtomicLong();
    private final AtomicLong writeNanos = new AtomicLong();
    private final AtomicLong insertCount = new AtomicLong();
    private final AtomicLong updateCount = new AtomicLong();

    private final ThreadLocal<Long> readStart = new ThreadLocal<>();
    private final ThreadLocal<Long> processStart = new ThreadLocal<>();
    private final ThreadLocal<Long> writeStart = new ThreadLocal<>();
    private final ThreadLocal<Set<String>> existingDocumentsBeforeWrite = new ThreadLocal<>();

    public CustomerMigrationPerformanceListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void beforeRead() {
        readStart.set(System.nanoTime());
    }

    @Override
    public void afterRead(LegacyCustomerEntity item) {
        addElapsed(readNanos, readStart);
    }

    @Override
    public void onReadError(Exception ex) {
        addElapsed(readNanos, readStart);
    }

    @Override
    public void beforeProcess(LegacyCustomerEntity item) {
        processStart.set(System.nanoTime());
    }

    @Override
    public void afterProcess(LegacyCustomerEntity item, TargetCustomerEntity result) {
        addElapsed(processNanos, processStart);
    }

    @Override
    public void onProcessError(LegacyCustomerEntity item, Exception e) {
        addElapsed(processNanos, processStart);
    }

    @Override
    public void beforeWrite(Chunk<? extends TargetCustomerEntity> items) {
        writeStart.set(System.nanoTime());

        Set<String> documents = new HashSet<>();
        for (TargetCustomerEntity item : items) {
            if (item.getDocumentNumber() != null) {
                documents.add(item.getDocumentNumber());
            }
        }

        if (documents.isEmpty()) {
            existingDocumentsBeforeWrite.set(Set.of());
            return;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(documents.size(), "?"));
        Set<String> existing = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT document_number FROM target.customers WHERE document_number IN (" + placeholders + ")",
                String.class,
                documents.toArray()));
        existingDocumentsBeforeWrite.set(existing);
    }

    @Override
    public void afterWrite(Chunk<? extends TargetCustomerEntity> items) {
        addElapsed(writeNanos, writeStart);

        Set<String> existing = existingDocumentsBeforeWrite.get();
        if (existing != null) {
            for (TargetCustomerEntity item : items) {
                if (item.getDocumentNumber() != null && existing.contains(item.getDocumentNumber())) {
                    updateCount.incrementAndGet();
                } else {
                    insertCount.incrementAndGet();
                }
            }
        }
        existingDocumentsBeforeWrite.remove();
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends TargetCustomerEntity> items) {
        addElapsed(writeNanos, writeStart);
        existingDocumentsBeforeWrite.remove();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        readNanos.set(0);
        processNanos.set(0);
        writeNanos.set(0);
        insertCount.set(0);
        updateCount.set(0);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long totalNanos = stepExecution.getEndTime() != null && stepExecution.getStartTime() != null
                ? java.time.Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toNanos()
                : 0L;

        log.info("=== BATCH PERFORMANCE ===");
        log.info("Step total: {} ms", nanosToMillis(totalNanos));
        log.info("Reader time: {} ms", nanosToMillis(readNanos.get()));
        log.info("Processor time: {} ms", nanosToMillis(processNanos.get()));
        log.info("Writer time: {} ms", nanosToMillis(writeNanos.get()));
        log.info("Unaccounted time: {} ms", nanosToMillis(Math.max(0L,
                totalNanos - readNanos.get() - processNanos.get() - writeNanos.get())));
        log.info("Reads: {}, process skips: {}, filters: {}, writes: {}, inserts: {}, updates: {}, read skips: {}, write skips: {}, commits: {}, rollbacks: {}",
                stepExecution.getReadCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getFilterCount(),
                stepExecution.getWriteCount(),
                insertCount.get(),
                updateCount.get(),
                stepExecution.getReadSkipCount(),
                stepExecution.getWriteSkipCount(),
                stepExecution.getCommitCount(),
                stepExecution.getRollbackCount());
        log.info("==========================");

        return stepExecution.getExitStatus();
    }

    private void addElapsed(AtomicLong accumulator, ThreadLocal<Long> startHolder) {
        Long start = startHolder.get();
        if (start != null) {
            accumulator.addAndGet(System.nanoTime() - start);
            startHolder.remove();
        }
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000;
    }
}
