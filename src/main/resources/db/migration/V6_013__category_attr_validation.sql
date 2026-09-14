-- Category attribute validation metadata + image guidance for seller forms.
-- Reuses category_attribute_definitions (no duplicate attribute tables).

ALTER TABLE category_attribute_definitions
    ADD COLUMN validation_regex VARCHAR(255) NULL AFTER is_required,
    ADD COLUMN min_length INT NULL AFTER validation_regex,
    ADD COLUMN max_length INT NULL AFTER min_length,
    ADD COLUMN min_value DECIMAL(18,4) NULL AFTER max_length,
    ADD COLUMN max_value DECIMAL(18,4) NULL AFTER min_value,
    ADD COLUMN placeholder VARCHAR(150) NULL AFTER max_value,
    ADD COLUMN help_text VARCHAR(500) NULL AFTER placeholder,
    ADD COLUMN image_guidance VARCHAR(500) NULL AFTER help_text;
