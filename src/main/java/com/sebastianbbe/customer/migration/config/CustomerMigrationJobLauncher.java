package com.sebastianbbe.customer.migration.config;

import java.time.LocalDateTime;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "batch.migration.enabled", havingValue = "true")
public class CustomerMigrationJobLauncher implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job customerMigrationJob;

    public CustomerMigrationJobLauncher(JobLauncher jobLauncher, Job customerMigrationJob) {
        this.jobLauncher = jobLauncher;
        this.customerMigrationJob = customerMigrationJob;
    }

    @Override
    public void run(String... args) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addString("executionTime", LocalDateTime.now().toString())
                .toJobParameters();

        jobLauncher.run(customerMigrationJob, parameters);
    }
}
