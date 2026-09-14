package com.nirvaankar.marketplace.identity.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate loads {@link User} with {@code Class.getDeclaredConstructor().newInstance()}.
 * A private factory constructor without an explicit no-arg constructor produces
 * {@code JpaSystemException: No default constructor for entity 'User'} on login DB fallback.
 */
class UserEntityConstructorTest {

    @Test
    void hibernateCanCallProtectedNoArgConstructor() throws Exception {
        Constructor<User> constructor = User.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isFalse();
        constructor.setAccessible(true);

        User user = constructor.newInstance();

        assertThat(user).isNotNull();
        assertThat(user.isActive()).isTrue();
        assertThat(user.getId()).isNull();
    }

    @Test
    void factoryConstructorStillRegistersPasswordUsers() {
        UUID publicId = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");
        User user = User.registerWithPassword(publicId, "buyer@nirvaankar.test", null, "hash");

        assertThat(user.getPublicId()).isEqualTo(publicId);
        assertThat(user.hasPassword()).isTrue();
        assertThat(user.isActive()).isTrue();
    }
}
