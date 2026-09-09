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

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final AtomicLong existingCount = new AtomicLong();
    private final AtomicLong businessUpdateCount = new AtomicLong();
    private final AtomicLong unchangedCount = new AtomicLong();

    private final ThreadLocal<Long> readStart = new ThreadLocal<>();
    private final ThreadLocal<Long> processStart = new ThreadLocal<>();
    private final ThreadLocal<Long> writeStart = new ThreadLocal<>();
    private final ThreadLocal<Map<String, TargetSnapshot>> targetSnapshotsBeforeWrite = new ThreadLocal<>();

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
            targetSnapshotsBeforeWrite.set(Collections.emptyMap());
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(documents.size(), "?"));
        Map<String, TargetSnapshot> snapshots = new HashMap<>();

        jdbcTemplate.query(
                "SELECT document_number, first_name, last_name, email, phone, status, created_at "
                        + "FROM target.customers WHERE document_number IN (" + placeholders + ")",
                documents.toArray(),
                rs -> {
                    snapshots.put(
                            rs.getString("document_number"),
                            new TargetSnapshot(
                                    rs.getString("first_name"),
                                    rs.getString("last_name"),
                                    rs.getString("email"),
                                    rs.getString("phone"),
                                    rs.getString("status"),
                                    rs.getTimestamp("created_at")));
                });

        targetSnapshotsBeforeWrite.set(snapshots);
    }

    @Override
    public void afterWrite(Chunk<? extends TargetCustomerEntity> items) {
        addElapsed(writeNanos, writeStart);

        Map<String, TargetSnapshot> snapshots = targetSnapshotsBeforeWrite.get();
        if (snapshots != null) {
            for (TargetCustomerEntity item : items) {
                if (item.getDocumentNumber() == null) {
                    continue;
                }

                TargetSnapshot existing = snapshots.get(item.getDocumentNumber());
                if (existing == null) {
                    insertCount.incrementAndGet();
                    continue;
                }

                existingCount.incrementAndGet();
                if (existing.matches(item)) {
                    unchangedCount.incrementAndGet();
                } else {
                    businessUpdateCount.incrementAndGet();
                }
            }
        }
        targetSnapshotsBeforeWrite.remove();
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends TargetCustomerEntity> items) {
        addElapsed(writeNanos, writeStart);
        targetSnapshotsBeforeWrite.remove();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        readNanos.set(0);
        processNanos.set(0);
        writeNanos.set(0);
        insertCount.set(0);
        existingCount.set(0);
        businessUpdateCount.set(0);
        unchangedCount.set(0);
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
        log.info("Reads: {}, process skips: {}, filters: {}, writes: {}, inserts: {}, existing: {}, business updates: {}, unchanged: {}, read skips: {}, write skips: {}, commits: {}, rollbacks: {}",
                stepExecution.getReadCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getFilterCount(),
                stepExecution.getWriteCount(),
                insertCount.get(),
                existingCount.get(),
                businessUpdateCount.get(),
                unchangedCount.get(),
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

    private record TargetSnapshot(
            String firstName,
            String lastName,
            String email,
            String phone,
            String status,
            Timestamp createdAt) {

        boolean matches(TargetCustomerEntity item) {
            return java.util.Objects.equals(firstName, item.getFirstName())
                    && java.util.Objects.equals(lastName, item.getLastName())
                    && java.util.Objects.equals(email, item.getEmail())
                    && java.util.Objects.equals(phone, item.getPhone())
                    && java.util.Objects.equals(status, item.getStatus())
                    && (createdAt == null
                    ? item.getCreatedAt() == null
                    : java.util.Objects.equals(createdAt.toLocalDateTime(), item.getCreatedAt()));
        }
    }
}
