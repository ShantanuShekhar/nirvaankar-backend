package com.nirvaankar.marketplace.seller.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreNameRulesTest {

    @Test
    void acceptsAllowedStyles() {
        assertThat(StoreNameRules.validate("artisan_studio")).isNull();
        assertThat(StoreNameRules.validate("ArtisanStudio")).isNull();
        assertThat(StoreNameRules.validate("artisan-studio")).isNull();
        assertThat(StoreNameRules.validate("artisanstudio")).isNull();
        assertThat(StoreNameRules.validate("Artisan Studio")).isNull();
    }

    @Test
    void rejectsInvalidAndTooManyWords() {
        assertThat(StoreNameRules.validate("!!!")).isNotBlank();
        String many = ("Word ".repeat(51)).trim();
        // Capitalize for sentence case so style check passes and word-count fails.
        many = Character.toUpperCase(many.charAt(0)) + many.substring(1);
        assertThat(StoreNameRules.validate(many)).contains("50");
    }
}
