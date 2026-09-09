package com.sebastianbbe.customer.migration.legacy;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Temporary compatibility holder for the initial repository layout.
 * The active entity lives under the conventional package path.
 */
@Entity(name = "LegacyCustomerLegacyEntity")
@Table(name = "customers", schema = "legacy")
public class LegacyCustomerEntity {
}
