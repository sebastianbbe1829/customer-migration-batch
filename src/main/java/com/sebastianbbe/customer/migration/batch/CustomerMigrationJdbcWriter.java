package com.sebastianbbe.customer.migration.batch;

import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CustomerMigrationJdbcWriter extends JdbcBatchItemWriter<TargetCustomerEntity> {

    private static final String INSERT_SQL = """
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
            """;

    public CustomerMigrationJdbcWriter(NamedParameterJdbcTemplate jdbcTemplate) {
        super();
        JdbcBatchItemWriter<TargetCustomerEntity> delegate = new JdbcBatchItemWriterBuilder<TargetCustomerEntity>()
                .namedParametersJdbcTemplate(jdbcTemplate)
                .sql(INSERT_SQL)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                        .addValue("documentNumber", item.getDocumentNumber())
                        .addValue("firstName", item.getFirstName())
                        .addValue("lastName", item.getLastName())
                        .addValue("email", item.getEmail())
                        .addValue("phone", item.getPhone())
                        .addValue("status", item.getStatus())
                        .addValue("createdAt", item.getCreatedAt())
                        .addValue("migratedAt", item.getMigratedAt()))
                .assertUpdates(true)
                .build();

        setSql(delegate.getSql());
        setItemSqlParameterSourceProvider(delegate.getItemSqlParameterSourceProvider());
        setDataSource(jdbcTemplate.getJdbcTemplate().getDataSource());
    }
}
