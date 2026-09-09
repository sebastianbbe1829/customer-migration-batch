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
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class CustomerMigrationPerformanceListener implements
        ItemReadListener<LegacyCustomerEntity>,
        ItemProcessListener<LegacyCustomerEntity, TargetCustomerEntity>,
        ItemWriteListener<TargetCustomerEntity>,
        StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerMigrationPerformanceListener.class);

    private final AtomicLong readNanos = new AtomicLong();
    private final AtomicLong processNanos = new AtomicLong();
    private final AtomicLong writeNanos = new AtomicLong();

    private final ThreadLocal<Long> readStart = new ThreadLocal<>();
    private final ThreadLocal<Long> processStart = new ThreadLocal<>();
    private final ThreadLocal<Long> writeStart = new ThreadLocal<>();

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
    }

    @Override
    public void afterWrite(Chunk<? extends TargetCustomerEntity> items) {
        addElapsed(writeNanos, writeStart);
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends TargetCustomerEntity> items) {
        addElapsed(writeNanos, writeStart);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        readNanos.set(0);
        processNanos.set(0);
        writeNanos.set(0);
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
        log.info("Reads: {}, process skips: {}, filters: {}, writes: {}, read skips: {}, write skips: {}, commits: {}, rollbacks: {}",
                stepExecution.getReadCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getFilterCount(),
                stepExecution.getWriteCount(),
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
