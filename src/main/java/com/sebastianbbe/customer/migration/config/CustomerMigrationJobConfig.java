package com.sebastianbbe.customer.migration.config;

import com.sebastianbbe.customer.migration.batch.CustomerMigrationException;
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
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
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
    public JpaItemWriter<TargetCustomerEntity> customerWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<TargetCustomerEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step customerMigrationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<LegacyCustomerEntity> customerReader,
            CustomerMigrationProcessor processor,
            JpaItemWriter<TargetCustomerEntity> customerWriter,
            CustomerMigrationSkipListener skipListener) {

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
                .retry(DeadlockLoserDataAccessException.class)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job customerMigrationJob(JobRepository jobRepository, Step customerMigrationStep) {
        return new JobBuilder("customerMigrationJob", jobRepository)
                .start(customerMigrationStep)
                .build();
    }
}
