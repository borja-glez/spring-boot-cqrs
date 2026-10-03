package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;

class OutboxSchemaInitializerTest {

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void tearDown() {
    dataSource.shutdown();
  }

  private int rows(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  @Test
  void embeddedModeCreatesTheTableOnAnEmbeddedDatabase() {
    new OutboxSchemaInitializer(dataSource, InitializeSchema.EMBEDDED, "cqrs_outbox")
        .afterPropertiesSet();

    assertThat(rows("cqrs_outbox")).isZero();
  }

  @Test
  void runningTwiceIsHarmless() {
    OutboxSchemaInitializer initializer =
        new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, "cqrs_outbox");

    initializer.afterPropertiesSet();
    initializer.afterPropertiesSet();

    assertThat(rows("cqrs_outbox")).isZero();
  }

  @Test
  void customTableNameNamesTheTableAndItsIndex() {
    new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, "app_outbox")
        .afterPropertiesSet();

    assertThat(rows("app_outbox")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = 'APP_OUTBOX_PENDING'",
                Integer.class))
        .isOne();
  }

  @Test
  void neverModeCreatesNothing() {
    new OutboxSchemaInitializer(dataSource, InitializeSchema.NEVER, "cqrs_outbox")
        .afterPropertiesSet();

    assertThatThrownBy(() -> rows("cqrs_outbox")).isInstanceOf(BadSqlGrammarException.class);
  }

  @Test
  void embeddedModeWithoutTheEmbeddedCheckCreatesNothing() {
    new OutboxSchemaInitializer(dataSource, InitializeSchema.EMBEDDED, "cqrs_outbox", false)
        .afterPropertiesSet();

    assertThatThrownBy(() -> rows("cqrs_outbox")).isInstanceOf(BadSqlGrammarException.class);
  }

  @Test
  void picksTheScriptOfTheDatabase() {
    assertThat(OutboxSchemaInitializer.schemaLocation("PostgreSQL"))
        .isEqualTo(OutboxSchemaInitializer.POSTGRESQL_SCHEMA_LOCATION);
    assertThat(OutboxSchemaInitializer.schemaLocation("H2"))
        .isEqualTo(OutboxSchemaInitializer.H2_SCHEMA_LOCATION);
    assertThatThrownBy(() -> OutboxSchemaInitializer.schemaLocation("MySQL"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MySQL")
        .hasMessageContaining("cqrs.jdbc.initialize-schema=never");
  }

  @Test
  void unreadableMetadataIsReported() throws SQLException {
    DataSource broken = mock(DataSource.class);
    when(broken.getConnection()).thenThrow(new SQLException("down"));

    assertThatThrownBy(
            () ->
                new OutboxSchemaInitializer(broken, InitializeSchema.ALWAYS, "cqrs_outbox")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("database product name");
  }

  @Test
  void rejectsAnInvalidTableName() {
    assertThatThrownBy(
            () -> new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, "x; DROP"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void embeddedModeSkipsADatabaseThatIsNotEmbedded() throws SQLException {
    DataSource notEmbedded = mock(DataSource.class);
    when(notEmbedded.getConnection()).thenThrow(new SQLException("not reachable"));

    new OutboxSchemaInitializer(notEmbedded, InitializeSchema.EMBEDDED, "other_outbox")
        .afterPropertiesSet();
  }
}
