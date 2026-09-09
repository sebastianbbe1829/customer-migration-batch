package com.sebastianbbe.customer.migration.config;

import com.sebastianbbe.customer.migration.batch.CustomerMigrationException;
import com.sebastianbbe.customer.migration.batch.CustomerMigrationPerformanceListener;
import com.sebastianbbe.customer.migration.batch.CustomerMigrationProcessor;
import com.sebastianbbe.customer.migration.batch.CustomerMigrationSkipListener;
import com.sebastianbbe.customer.migration.domain.LegacyCustomerEntity;
import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CustomerMigrationJobConfig {

    private static final int CHUNK_SIZE = 100;

    @Bean
    public JpaPagingItemReader<LegacyCustomerEntity> customerReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<LegacyCustomerEntity>()
                .name("customerReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        select c
                        from LegacyCustomerEntity c
                        where c.documentNumber is null
                           or c.id = (
                               select min(c2.id)
                               from LegacyCustomerEntity c2
                               where c2.documentNumber = c.documentNumber
                           )
                        order by c.id
                        """)
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<TargetCustomerEntity> customerWriter(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcBatchItemWriterBuilder<TargetCustomerEntity>()
                .namedParametersJdbcTemplate(jdbcTemplate)
                .sql("""
                        INSERT INTO target.customers (
                            document_number,
                            first_name,
                            last_name,
                            email,
                            phone,
                            status,
                            created_at,
                            migrated_at
                        ) VALUES (
                            :documentNumber,
                            :firstName,
                            :lastName,
                            :email,
                            :phone,
                            :status,
                            :createdAt,
                            :migratedAt
                        )
                        """)
                .beanMapped()
                .assertUpdates(true)
                .build();
    }

    @Bean
    public Step customerMigrationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<LegacyCustomerEntity> customerReader,
            CustomerMigrationProcessor processor,
            JdbcBatchItemWriter<TargetCustomerEntity> customerWriter,
            CustomerMigrationSkipListener skipListener,
            CustomerMigrationPerformanceListener performanceListener) {

        return new StepBuilder("customerMigrationStep", jobRepository)
                .<LegacyCustomerEntity, TargetCustomerEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(customerReader)
                .processor(processor)
                .writer(customerWriter)
                .faultTolerant()
                .skipLimit(10)
                .skip(CustomerMigrationException.class)
                .skip(DataIntegrityViolationException.class)
                .retryLimit(3)
                .retry(PessimisticLockingFailureException.class)
                .listener((org.springframework.batch.core.SkipListener<LegacyCustomerEntity, TargetCustomerEntity>) skipListener)
                .listener((org.springframework.batch.core.StepExecutionListener) skipListener)
                .listener((org.springframework.batch.core.ItemReadListener<LegacyCustomerEntity>) performanceListener)
                .listener((org.springframework.batch.core.ItemProcessListener<LegacyCustomerEntity, TargetCustomerEntity>) performanceListener)
                .listener((org.springframework.batch.core.ItemWriteListener<TargetCustomerEntity>) performanceListener)
                .listener((org.springframework.batch.core.StepExecutionListener) performanceListener)
                .build();
    }

    @Bean
    public Job customerMigrationJob(JobRepository jobRepository, Step customerMigrationStep) {
        return new JobBuilder("customerMigrationJob", jobRepository)
                .start(customerMigrationStep)
                .build();
    }
}
