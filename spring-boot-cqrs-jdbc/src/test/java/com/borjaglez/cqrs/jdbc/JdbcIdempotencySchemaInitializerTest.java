package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;

class JdbcIdempotencySchemaInitializerTest {

  private DataSource embedded() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .setName(UUID.randomUUID().toString())
        .build();
  }

  private boolean tableExists(DataSource dataSource, String table) {
    return new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = ?",
                Integer.class,
                table)
        == 1;
  }

  @Test
  void embeddedModeCreatesTheTableInAnEmbeddedDatabaseTwiceWithoutError() throws Exception {
    DataSource dataSource = embedded();
    new JdbcIdempotencySchemaInitializer(
            dataSource, InitializeSchema.EMBEDDED, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
    new JdbcIdempotencySchemaInitializer(
            dataSource, InitializeSchema.EMBEDDED, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();

    assertThat(tableExists(dataSource, "cqrs_processed_message")).isTrue();
  }

  @Test
  void embeddedModeSkipsANonEmbeddedDatabase() throws Exception {
    DataSource server = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(server.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn("jdbc:postgresql://db.example.com:5432/app");

    new JdbcIdempotencySchemaInitializer(
            server, InitializeSchema.EMBEDDED, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();

    // Only the embedded check ran: no statement was ever created to run the schema script.
    verify(connection, never()).createStatement();
    verify(connection, never()).prepareStatement(anyString());
  }

  @Test
  void neverModeDoesNothing() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    new JdbcIdempotencySchemaInitializer(
            dataSource, InitializeSchema.NEVER, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();

    verifyNoInteractions(dataSource);
  }

  @Test
  void alwaysModeUsesTheCustomTableName() throws Exception {
    DataSource dataSource = embedded();
    new JdbcIdempotencySchemaInitializer(dataSource, InitializeSchema.ALWAYS, "markers")
        .afterPropertiesSet();

    assertThat(tableExists(dataSource, "markers")).isTrue();
    assertThat(tableExists(dataSource, "cqrs_processed_message")).isFalse();
  }

  @Test
  void rejectsAnUnsafeTableName() {
    assertThatThrownBy(
            () ->
                new JdbcIdempotencySchemaInitializer(
                    mock(DataSource.class), InitializeSchema.ALWAYS, "x; DROP TABLE y"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
