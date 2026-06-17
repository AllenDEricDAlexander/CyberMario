package top.egon.mario.clocktower;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerSchemaMigrationTests {

    @Test
    void migrationCreatesCoreClocktowerTablesAndSeedsThreeScripts() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V18__create_clocktower_core_schema.sql"));

        assertThat(sql).contains("CREATE TABLE clocktower_script");
        assertThat(sql).contains("CREATE TABLE clocktower_role");
        assertThat(sql).contains("CREATE TABLE clocktower_night_order");
        assertThat(sql).contains("CREATE TABLE clocktower_jinx_rule");
        assertThat(sql).contains("CREATE TABLE clocktower_room");
        assertThat(sql).contains("CREATE TABLE clocktower_seat");
        assertThat(sql).contains("CREATE TABLE clocktower_event");
        assertThat(sql).contains("CREATE TABLE clocktower_grimoire_entry");
        assertThat(sql).contains("CREATE TABLE clocktower_status_marker");
        assertThat(sql).contains("CREATE TABLE clocktower_nomination");
        assertThat(sql).contains("CREATE TABLE clocktower_vote");
        assertThat(sql).contains("CREATE TABLE clocktower_board_config");
        assertThat(sql).contains("CREATE TABLE clocktower_board_role");
        assertThat(sql).contains("CREATE TABLE clocktower_storyteller_task");
        assertThat(sql).contains("TROUBLE_BREWING");
        assertThat(sql).contains("BAD_MOON_RISING");
        assertThat(sql).contains("SECTS_AND_VIOLETS");
        assertThat(sql).contains("idx_clocktower_event_room_seq");
        assertThat(sql).contains("uk_clocktower_room_code");
    }
}
