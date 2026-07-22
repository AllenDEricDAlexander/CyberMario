package top.egon.mario.agent.externalim.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySpaceExecutionLaneTests {

    private final MemorySpaceExecutionLane lane = new MemorySpaceExecutionLane();

    @AfterEach
    void tearDown() {
        lane.close();
    }

    @Test
    void serializesTheSameSpaceButAllowsAnotherSpaceToRun() throws Exception {
        CountDownLatch firstAStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstA = new CountDownLatch(1);
        CountDownLatch bFinished = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();

        CompletableFuture<Void> firstA = lane.submit("space-a", () -> {
            order.add("a1-start");
            firstAStarted.countDown();
            await(releaseFirstA);
            order.add("a1-end");
        });
        assertThat(firstAStarted.await(1, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> secondA = lane.submit("space-a", () -> order.add("a2"));
        CompletableFuture<Void> b = lane.submit("space-b", () -> {
            order.add("b");
            bFinished.countDown();
        });

        assertThat(bFinished.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(order).contains("b").doesNotContain("a2");
        releaseFirstA.countDown();
        CompletableFuture.allOf(firstA, secondA, b).get(2, TimeUnit.SECONDS);
        assertThat(order.indexOf("a1-end")).isLessThan(order.indexOf("a2"));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
