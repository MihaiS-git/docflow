package com.brutecx.docflow_backend.domain.security;

import com.brutecx.docflow_backend.audit.keyrotation.AuditExportSigningKeyRotationAuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        AuditSigningKeyRotationService.class,
        AuditSigningKeyResolver.class,
        AuditKeyCrypto.class,
        AuditSigningKeyRotationServiceTest.Config.class
})
class AuditSigningKeyRotationServiceTest {

    @Resource
    private AuditSigningKeyRotationService service;

    @Resource
    private AuditSigningKeyRepository repository;

    @TestConfiguration
    static class Config {

        @Bean
        AuditExportSigningKeyRotationAuditService rotationAuditService() {
            return (reason, oldKeyId, newKeyId, fingerprint) -> {
                // no-op for tests
            };
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void rotatesWhenMissing() {

        service.requireActiveForExport();

        List<AuditSigningKey> all = repository.findAll();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).isActive()).isTrue();
    }

    @Test
    void rotatesWhenExpired() {

        AuditSigningKey first = service.requireActiveForExport();

        repository.forceExpire(
                first.getKeyId(),
                Instant.now().minusSeconds(1)
        );

        AuditSigningKey second = service.requireActiveForExport();

        List<AuditSigningKey> all = repository.findAll();

        assertThat(all).hasSize(2);
        assertThat(second.getKeyId()).isNotEqualTo(first.getKeyId());
        assertThat(all.stream()
                .filter(AuditSigningKey::isActive)
                .count())
                .isEqualTo(1);
    }

    @Test
    void concurrentExportsProduceSingleKey() throws Exception {

        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        var pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                service.requireActiveForExport();
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();

        List<AuditSigningKey> all = repository.findAll();

        assertThat(all).hasSize(1);
    }
}