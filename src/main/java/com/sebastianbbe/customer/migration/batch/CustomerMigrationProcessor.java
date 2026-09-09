package com.sebastianbbe.customer.migration.batch;

import com.sebastianbbe.customer.migration.domain.LegacyCustomerEntity;
import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class CustomerMigrationProcessor implements ItemProcessor<LegacyCustomerEntity, TargetCustomerEntity> {

    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    @Override
    public TargetCustomerEntity process(LegacyCustomerEntity item) {
        String document = normalize(item.getDocumentNumber());
        if (document == null) {
            throw new CustomerMigrationException("Document number is required");
        }

        String fullName = normalize(item.getFullName());
        if (fullName == null || fullName.indexOf(' ') < 0) {
            throw new CustomerMigrationException("Full name must contain first and last name");
        }

        String[] nameParts = fullName.split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts[1];

        String email = normalize(item.getEmail());
        if (email != null && !email.matches(EMAIL_REGEX)) {
            throw new CustomerMigrationException("Invalid email: " + email);
        }

        String status = normalize(item.getStatus());
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
        }
        if (status == null || !(status.equals("ACTIVE") || status.equals("INACTIVE"))) {
            throw new CustomerMigrationException("Unsupported status: " + item.getStatus());
        }

        String phone = normalize(item.getPhone());

        return new TargetCustomerEntity(
                document,
                firstName,
                lastName,
                email,
                phone,
                status,
                item.getCreatedAt(),
                LocalDateTime.now()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
