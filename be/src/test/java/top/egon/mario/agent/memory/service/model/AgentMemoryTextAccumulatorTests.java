package top.egon.mario.agent.memory.service.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemoryTextAccumulatorTests {

    @Test
    void appendsDeltaChunks() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("你");
        accumulator.accept("好");

        assertThat(accumulator.content()).isEqualTo("你好");
        assertThat(accumulator.normalizedContent()).isEqualTo("你好");
    }

    @Test
    void replacesCumulativeChunks() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("你");
        accumulator.accept("你好");
        accumulator.accept("你好，Mario");

        assertThat(accumulator.content()).isEqualTo("你好，Mario");
    }

    @Test
    void appendsRepeatedDeltaChunks() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("哈");
        accumulator.accept("哈");

        assertThat(accumulator.content()).isEqualTo("哈哈");
    }

    @Test
    void ignoresNullChunksAndMergesFromNullCurrentText() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept(null);

        assertThat(accumulator.content()).isEqualTo("");
        assertThat(accumulator.normalizedContent()).isNull();
        assertThat(AgentMemoryTextAccumulator.merge(null, "text")).isEqualTo("text");
        assertThat(AgentMemoryTextAccumulator.merge("text", null)).isEqualTo("text");
    }

    @Test
    void normalizesBlankContentToNull() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("");
        accumulator.accept("   ");

        assertThat(accumulator.content()).isEqualTo("");
        assertThat(accumulator.normalizedContent()).isNull();
    }
}
