package com.nirvaankar.marketplace.common.id;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/** Maps a {@link UUID} to MySQL BINARY(16), big-endian, index-friendly. */
@Converter(autoApply = false)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        return attribute == null ? null : UuidV7.toBytes(attribute);
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : UuidV7.toUuid(dbData);
    }
}
