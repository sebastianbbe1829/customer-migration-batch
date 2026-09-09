package com.sebastianbbe.customer.migration.batch;

import com.sebastianbbe.customer.migration.domain.LegacyCustomerEntity;
import com.sebastianbbe.customer.migration.domain.TargetCustomerEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerMigrationProcessorTest {

    private final CustomerMigrationProcessor processor = new CustomerMigrationProcessor();
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 9, 9, 8, 0);

    @Test
    void shouldNormalizeAndTransformValidCustomer() {
        LegacyCustomerEntity source = new LegacyCustomerEntity(
                " 1001 ",
                "  JUAN   PEREZ  ",
                " juan.perez@example.com ",
                " 3001234567 ",
                " active ",
                createdAt
        );

        TargetCustomerEntity result = processor.process(source);

        assertEquals("1001", result.getDocumentNumber());
        assertEquals("JUAN", result.getFirstName());
        assertEquals("PEREZ", result.getLastName());
        assertEquals("juan.perez@example.com", result.getEmail());
        assertEquals("3001234567", result.getPhone());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(createdAt, result.getCreatedAt());
        assertTrue(result.getMigratedAt() != null);
    }

    @Test
    void shouldAllowMissingOptionalEmail() {
        LegacyCustomerEntity source = new LegacyCustomerEntity(
                "1010", "DANIEL CASTRO", null, "3090123456", "ACTIVE", createdAt
        );

        TargetCustomerEntity result = processor.process(source);

        assertEquals(null, result.getEmail());
    }

    @Test
    void shouldRejectMissingDocument() {
        LegacyCustomerEntity source = new LegacyCustomerEntity(
                null, "DOCUMENTO NULO", "valid@example.com", "3056789012", "ACTIVE", createdAt
        );

        assertThrows(CustomerMigrationException.class, () -> processor.process(source));
    }

    @Test
    void shouldRejectInvalidEmail() {
        LegacyCustomerEntity source = new LegacyCustomerEntity(
                "1007", "EMAIL INVALIDO", "email-invalido", "3067890123", "ACTIVE", createdAt
        );

        CustomerMigrationException exception = assertThrows(
                CustomerMigrationException.class,
                () -> processor.process(source)
        );

        assertTrue(exception.getMessage().contains("Invalid email"));
    }

    @Test
    void shouldRejectUnsupportedStatus() {
        LegacyCustomerEntity source = new LegacyCustomerEntity(
                "1008", "ESTADO DESCONOCIDO", "estado@example.com", "3078901234", "BLOCKED", createdAt
        );

        CustomerMigrationException exception = assertThrows(
                CustomerMigrationException.class,
                () -> processor.process(source)
        );

        assertTrue(exception.getMessage().contains("Unsupported status"));
    }
}
