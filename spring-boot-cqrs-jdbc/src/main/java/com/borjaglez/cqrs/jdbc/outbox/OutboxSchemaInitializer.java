package com.borjaglez.cqrs.jdbc.outbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.ClassUtils;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;
import com.borjaglez.cqrs.jdbc.JdbcIdempotencyStore;

import lombok.Generated;

/**
 * Creates the outbox table when the mode asks for it, with the script of the database: {@value
 * #POSTGRESQL_SCHEMA_LOCATION} or {@value #H2_SCHEMA_LOCATION}. Other databases have no script: the
 * application creates the table (see docs/outbox.md) and sets {@code
 * cqrs.jdbc.initialize-schema=never}. The {@code embedded} mode needs Spring Boot's {@code
 * EmbeddedDatabaseConnection}; without it no database counts as embedded.
 */
public class OutboxSchemaInitializer implements InitializingBean {

  public static final String POSTGRESQL_SCHEMA_LOCATION =
      "com/borjaglez/cqrs/jdbc/schema-outbox-postgresql.sql";
  public static final String H2_SCHEMA_LOCATION = "com/borjaglez/cqrs/jdbc/schema-outbox-h2.sql";

  static final boolean EMBEDDED_DATABASE_CHECK_PRESENT =
      ClassUtils.isPresent(
          "org.springframework.boot.jdbc.EmbeddedDatabaseConnection",
          OutboxSchemaInitializer.class.getClassLoader());

  private final DataSource dataSource;
  private final InitializeSchema mode;
  private final String tableName;
  private final boolean embeddedDatabaseCheckPresent;

  public OutboxSchemaInitializer(DataSource dataSource, InitializeSchema mode, String tableName) {
    this(dataSource, mode, tableName, EMBEDDED_DATABASE_CHECK_PRESENT);
  }

  OutboxSchemaInitializer(
      DataSource dataSource,
      InitializeSchema mode,
      String tableName,
      boolean embeddedDatabaseCheckPresent) {
    this.dataSource = dataSource;
    this.mode = mode;
    this.tableName = JdbcIdempotencyStore.validTableName(tableName);
    this.embeddedDatabaseCheckPresent = embeddedDatabaseCheckPresent;
  }

  @Override
  public void afterPropertiesSet() {
    if (mode == InitializeSchema.NEVER || (mode == InitializeSchema.EMBEDDED && !isEmbedded())) {
      return;
    }
    String script = script(schemaLocation(databaseProductName()));
    new ResourceDatabasePopulator(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)))
        .execute(dataSource);
  }

  static String schemaLocation(String databaseProductName) {
    if ("PostgreSQL".equals(databaseProductName)) {
      return POSTGRESQL_SCHEMA_LOCATION;
    }
    if ("H2".equals(databaseProductName)) {
      return H2_SCHEMA_LOCATION;
    }
    throw new IllegalStateException(
        "No outbox schema script for "
            + databaseProductName
            + "; create the outbox table yourself (see docs/outbox.md) and set"
            + " cqrs.jdbc.initialize-schema=never");
  }

  private String databaseProductName() {
    try {
      return JdbcUtils.extractDatabaseMetaData(
          dataSource, DatabaseMetaData::getDatabaseProductName);
    } catch (MetaDataAccessException e) {
      throw new IllegalStateException(
          "Could not read the database product name to create the outbox table", e);
    }
  }

  /**
   * Reads the script and fills in the table and index names. The {@code IOException} catch block is
   * excluded from JaCoCo coverage via {@code @Generated}: the scripts ship in this jar.
   */
  @Generated
  private String script(String location) {
    try {
      return new ClassPathResource(location)
          .getContentAsString(StandardCharsets.UTF_8)
          .replace("${index}", unqualified(tableName))
          .replace("${table}", tableName);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean isEmbedded() {
    return embeddedDatabaseCheckPresent && EmbeddedDatabaseConnection.isEmbedded(dataSource);
  }

  /** An index lives in its table's schema, so its own name must not be qualified. */
  private static String unqualified(String tableName) {
    return tableName.substring(tableName.lastIndexOf('.') + 1);
  }
}
