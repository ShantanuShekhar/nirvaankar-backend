package com.nirvaankar.marketplace.identity.repository.projection;

import java.time.Instant;

/**
 * The admin user list. A projection, not an entity: the list screen shows six
 * fields, so loading eleven columns plus a profile plus an address collection
 * per row would be pure waste.
 */
public interface UserSummaryRow {

    Long getId();

    byte[] getPublicId();

    String getEmail();

    String getPhone();

    String getFirstName();

    String getLastName();

    Boolean getIsActive();

    String getRoleCodes();

    Instant getCreatedAt();

    Instant getLastLoginAt();
}
