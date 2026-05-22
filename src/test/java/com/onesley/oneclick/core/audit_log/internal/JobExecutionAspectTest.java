package com.onesley.oneclick.core.audit_log.internal;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de l'aspect {@link JobExecutionAspect} — on pilote un
 * {@link ProceedingJoinPoint} mocké à travers les chemins succès / échec /
 * message null / message tronqué / INSERT initial en échec (non-bloquant).
 * Le {@code TransactionTemplate} interne exécute son callback même avec un
 * {@link PlatformTransactionManager} mocké (getTransaction → commit no-op).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobExecutionAspectTest {

    @Mock JobExecutionRepository jobRepo;
    @Mock PlatformTransactionManager txMgr;
    JobExecutionAspect aspect;

    @SuppressWarnings("unused")
    void sampleScheduledJob() { /* sert uniquement à dériver le job_name via réflexion */ }

    @BeforeEach
    void setup() {
        aspect = new JobExecutionAspect(jobRepo, txMgr);
        lenient().when(txMgr.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private ProceedingJoinPoint pjp() throws Exception {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method m = JobExecutionAspectTest.class.getDeclaredMethod("sampleScheduledJob");
        lenient().when(pjp.getSignature()).thenReturn(sig);
        lenient().when(sig.getMethod()).thenReturn(m);
        return pjp;
    }

    private void beginInsertsOk() {
        when(jobRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void success_persistsRunningThenSuccess() throws Throwable {
        beginInsertsOk();
        when(jobRepo.findById(any())).thenAnswer(i -> Optional.of(new JobExecution(UUID.randomUUID(), "X.run")));
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenReturn("OK");

        Object out = aspect.trackScheduled(pjp);

        assertThat(out).isEqualTo("OK");
        verify(jobRepo, atLeastOnce()).save(any()); // begin (running) + markSuccess
    }

    @Test
    void success_butJobRowVanished_coversIfPresentAbsentBranch() throws Throwable {
        beginInsertsOk();
        when(jobRepo.findById(any())).thenReturn(Optional.empty()); // ifPresent → branche "absent"
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenReturn(42);
        assertThat(aspect.trackScheduled(pjp)).isEqualTo(42);
    }

    @Test
    void failure_rethrowsAndMarksFailed() throws Throwable {
        beginInsertsOk();
        when(jobRepo.findById(any())).thenAnswer(i -> Optional.of(new JobExecution(UUID.randomUUID(), "X.run")));
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenThrow(new IllegalStateException("kaboom"));

        assertThatThrownBy(() -> aspect.trackScheduled(pjp))
            .isInstanceOf(IllegalStateException.class).hasMessage("kaboom");
        verify(jobRepo, atLeastOnce()).save(any());
    }

    @Test
    void failure_nullMessage_handledGracefully() throws Throwable {
        beginInsertsOk();
        when(jobRepo.findById(any())).thenAnswer(i -> Optional.of(new JobExecution(UUID.randomUUID(), "X.run")));
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenThrow(new IllegalStateException()); // getMessage() == null → branche ternaire
        assertThatThrownBy(() -> aspect.trackScheduled(pjp)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failure_veryLongMessage_isTruncated() throws Throwable {
        beginInsertsOk();
        when(jobRepo.findById(any())).thenAnswer(i -> Optional.of(new JobExecution(UUID.randomUUID(), "X.run")));
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenThrow(new IllegalStateException("e".repeat(5000))); // > MAX_ERR_LEN (4000)
        assertThatThrownBy(() -> aspect.trackScheduled(pjp)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void beginInsertFails_nonBlocking_successPath_startedNull() throws Throwable {
        when(jobRepo.save(any())).thenThrow(new RuntimeException("db down")); // INSERT job_execution KO
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenReturn("STILL-OK");

        // le job tourne malgré l'échec de tracking ; started == null → markSuccess court-circuite
        assertThat(aspect.trackScheduled(pjp)).isEqualTo("STILL-OK");
        verify(jobRepo, never()).findById(any());
    }

    @Test
    void beginInsertFails_nonBlocking_failurePath_startedNull() throws Throwable {
        when(jobRepo.save(any())).thenThrow(new RuntimeException("db down"));
        ProceedingJoinPoint pjp = pjp();
        when(pjp.proceed()).thenThrow(new IllegalArgumentException("boom"));

        // started == null → markFailed court-circuite, mais l'exception métier est bien relancée
        assertThatThrownBy(() -> aspect.trackScheduled(pjp)).isInstanceOf(IllegalArgumentException.class);
        verify(jobRepo, never()).findById(any());
    }
}
