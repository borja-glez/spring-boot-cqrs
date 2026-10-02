package com.borjaglez.cqrs.jdbc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;

import lombok.Generated;

/** Creates the processed-message table from {@value #SCHEMA_LOCATION} when the mode asks for it. */
public class JdbcIdempotencySchemaInitializer implements InitializingBean {

  public static final String SCHEMA_LOCATION = "com/borjaglez/cqrs/jdbc/schema-idempotency.sql";

  private static final String DEFAULT_INDEX_NAME = JdbcIdempotencyStore.DEFAULT_TABLE_NAME + "_at";

  private final DataSource dataSource;
  private final InitializeSchema mode;
  private final String tableName;

  public JdbcIdempotencySchemaInitializer(
      DataSource dataSource, InitializeSchema mode, String tableName) {
    this.dataSource = dataSource;
    this.mode = mode;
    this.tableName = JdbcIdempotencyStore.validTableName(tableName);
  }

  @Override
  public void afterPropertiesSet() {
    if (mode == InitializeSchema.NEVER
        || (mode == InitializeSchema.EMBEDDED
            && !EmbeddedDatabaseConnection.isEmbedded(dataSource))) {
      return;
    }
    new ResourceDatabasePopulator(new ByteArrayResource(script().getBytes(StandardCharsets.UTF_8)))
        .execute(dataSource);
  }

  /**
   * Reads the schema script and renames the table. The {@code IOException} catch block is excluded
   * from JaCoCo coverage via {@code @Generated}: the script ships in this jar, so reading it never
   * fails.
   */
  @Generated
  private String script() {
    try {
      return new ClassPathResource(SCHEMA_LOCATION)
          .getContentAsString(StandardCharsets.UTF_8)
          .replace(DEFAULT_INDEX_NAME, unqualified(tableName) + "_at")
          .replace(JdbcIdempotencyStore.DEFAULT_TABLE_NAME, tableName);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The index lives in the table's schema, so its own name must not be qualified. */
  private static String unqualified(String tableName) {
    return tableName.substring(tableName.lastIndexOf('.') + 1);
  }
}
