package top.egon.mario.rag.service;

import org.junit.jupiter.api.Test;

import top.egon.mario.rag.po.enums.RagAccessLevel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the user-scoped RAG knowledge base access hierarchy.
 */
class RagAccessLevelTests {

    @Test
    void manageIncludesWriteAndReadPermissions() {
        assertThat(RagAccessLevel.MANAGE.allows(RagAccessLevel.WRITE)).isTrue();
        assertThat(RagAccessLevel.MANAGE.allows(RagAccessLevel.READ)).isTrue();
    }

    @Test
    void readDoesNotIncludeWritePermission() {
        assertThat(RagAccessLevel.READ.allows(RagAccessLevel.WRITE)).isFalse();
    }

}
