package com.nirvaankar.marketplace.catalog.service;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogServiceMysqlFlagTest {

    @Test
    void treatsMysqlTinyintOneAsActive() {
        assertTrue(CatalogService.mysqlFlag(Boolean.TRUE));
        assertTrue(CatalogService.mysqlFlag(1));
        assertTrue(CatalogService.mysqlFlag(1L));
        assertTrue(CatalogService.mysqlFlag((byte) 1));
        assertTrue(CatalogService.mysqlFlag(BigInteger.ONE));
        assertFalse(CatalogService.mysqlFlag(Boolean.FALSE));
        assertFalse(CatalogService.mysqlFlag(0));
        assertFalse(CatalogService.mysqlFlag(null));
        assertFalse(Boolean.TRUE.equals(1), "numeric TINYINT must not be compared as Boolean.TRUE");
    }
}
