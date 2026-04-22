package com.flashsale.commonlib.config;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.SqlTypes;

/**
 * Custom PostgreSQL dialect that forces {@code BLOB} columns to map to {@code BYTEA}
 * and {@code CLOB} columns to map to {@code TEXT} instead of Hibernate 7 defaults.
 *
 * <p><strong>BYTEA mapping:</strong> Axon Framework's {@code JpaTokenStore} stores
 * serialized tokens as binary data. With the standard Hibernate PostgreSQL dialect,
 * a {@code byte[]} field maps to {@code OID} (Large Objects), which conflicts with
 * a {@code BYTEA} column defined by Flyway migration scripts. This dialect ensures
 * that {@code SqlTypes.BLOB} produces {@code BYTEA} in generated SQL.
 *
 * <p><strong>TEXT mapping:</strong> Axon's JPA Token Store serializes the
 * {@code timestamp} column as a VARCHAR (ISO-8601 string, e.g.
 * "2026-04-21T15:56:16.806Z"). Hibernate may emit {@code CLOB} for String fields
 * on some configurations; this dialect maps {@code SqlTypes.CLOB} to {@code TEXT}
 * to prevent VARCHAR → TEXT coercion mismatches.
 *
 * <p>Usage: set {@code spring.jpa.properties.hibernate.dialect} to this class name.
 */
public class ByteaPostgreSQLDialect extends PostgreSQLDialect {

    public ByteaPostgreSQLDialect() {
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.BLOB) {
            return "bytea";
        }
        if (sqlTypeCode == SqlTypes.CLOB) {
            return "text";
        }
        return super.columnType(sqlTypeCode);
    }

    @Override
    protected String castType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.BLOB) {
            return "bytea";
        }
        if (sqlTypeCode == SqlTypes.CLOB) {
            return "text";
        }
        return super.castType(sqlTypeCode);
    }
}
