package top.egon.mario.investment.common.job;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentJobRunnerTests {

    @Test
    void startsPollingAutomaticallyAndStopsCleanly() throws Exception {
        InvestmentJobWorker worker = mock(InvestmentJobWorker.class);
        InvestmentJobProperties properties = new InvestmentJobProperties();
        properties.setBatchSize(3);
        properties.getRunner().setInitialDelay(Duration.ZERO);
        properties.getRunner().setPollInterval(Duration.ofMillis(100));
        CountDownLatch processed = new CountDownLatch(1);
        when(worker.processBatch(anyString(), eq(3))).thenAnswer(invocation -> {
            processed.countDown();
            return 2;
        });
        InvestmentJobRunner runner = new InvestmentJobRunner(worker, properties);

        runner.start();
        try {
            assertThat(runner.isAutoStartup()).isTrue();
            assertThat(runner.isRunning()).isTrue();
            assertThat(processed.await(2, TimeUnit.SECONDS)).isTrue();
            verify(worker, atLeastOnce()).processBatch(anyString(), eq(3));
        } finally {
            runner.stop();
        }

        assertThat(runner.isRunning()).isFalse();
    }
}
