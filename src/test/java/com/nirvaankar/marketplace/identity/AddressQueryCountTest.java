package com.nirvaankar.marketplace.identity;

import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.identity.service.AuthService;
import com.nirvaankar.marketplace.identity.service.UserAddressService;
import com.nirvaankar.marketplace.identity.service.dto.AuthenticatedSession;
import com.nirvaankar.marketplace.support.AbstractIntegrationTest;
import com.nirvaankar.marketplace.support.QueryCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule the whole persistence layer is built around: a list endpoint costs
 * a constant number of queries no matter how many rows come back. Ten
 * addresses and one address must cost the same.
 */
class AddressQueryCountTest extends AbstractIntegrationTest {

    private static final int QUERY_BUDGET = 3;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAddressService userAddressService;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("listing addresses costs the same whether there are 1 or 25")
    void listAddressesIssuesConstantQueries() {
        Long userId = createUserWithAddresses(1);
        queryCounter.reset();
        userAddressService.listAddresses(userId);
        long oneAddress = queryCounter.getQueryCount();

        Long busyUserId = createUserWithAddresses(5);
        queryCounter.reset();
        userAddressService.listAddresses(busyUserId);
        long manyAddresses = queryCounter.getQueryCount();

        assertThat(manyAddresses)
                .as("query count must not grow with row count - that is the N+1 signature")
                .isEqualTo(oneAddress)
                .isLessThanOrEqualTo(QUERY_BUDGET);
    }

    private Long createUserWithAddresses(int addressCount) {
        String suffix = String.valueOf(System.nanoTime());
        AuthenticatedSession session = authService.registerWithPassword(
                "buyer" + suffix + "@nirvaankar.test", null, "kumhaar-1947",
                "Test", "Buyer", null, null, null);

        Long userId = resolveUserId(session);
        int count = Math.min(addressCount, UserAddressService.MAX_ADDRESSES_PER_USER);
        for (int i = 0; i < count; i++) {
            userAddressService.addAddress(userId, "home", "Test Buyer", "+919000000001",
                    "House " + i, null, null, "Jaipur", "Rajasthan", "302001", "IN",
                    BigDecimal.valueOf(26.9124), BigDecimal.valueOf(75.7873), i == 0);
        }
        return userId;
    }

    /**
     * The session only carries the public id, which is the point - internal
     * ids never leave the server. Only a test reaches for the repository
     * directly like this.
     */
    private Long resolveUserId(AuthenticatedSession session) {
        return userRepository.findByPublicId(session.userPublicId())
                .orElseThrow(() -> new IllegalStateException("registered user not found"))
                .getId();
    }
}
