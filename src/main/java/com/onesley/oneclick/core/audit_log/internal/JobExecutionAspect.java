package com.onesley.oneclick.core.audit_log.internal;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Aspect AOP — wrap toutes les méthodes annotées {@code @Scheduled} pour tracer
 * leur exécution dans la table {@code job_executions} (§15 spec senior dev).
 *
 * <p>Comportement :
 * <ol>
 *   <li>Avant l'exécution : INSERT row {@code status='running', started_at=now()}</li>
 *   <li>Si OK : UPDATE {@code status='success', finished_at=now()}</li>
 *   <li>Si exception : UPDATE {@code status='failed', error_message=<truncated>}</li>
 * </ol>
 *
 * <p>Le {@code job_name} est dérivé de {@code ClassName.methodName} pour identifier
 * unique chaque cron. Le tracking utilise {@link TransactionTemplate} (REQUIRES_NEW)
 * pour découpler la persistance du job de l'éventuelle transaction du job lui-même.
 *
 * <p>Note : intentionnellement non-bloquant. Si l'INSERT job_executions échoue,
 * on logge mais on n'empêche pas le job de tourner.
 */
@Aspect
@Component
@Slf4j
public class JobExecutionAspect {

    private static final int MAX_ERR_LEN = 4000;

    private final JobExecutionRepository jobRepo;
    private final TransactionTemplate txTemplate;

    public JobExecutionAspect(JobExecutionRepository jobRepo, PlatformTransactionManager txMgr) {
        this.jobRepo = jobRepo;
        this.txTemplate = new TransactionTemplate(txMgr);
        // REQUIRES_NEW : la persistance du job_execution est indépendante de la transaction du job
        this.txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object trackScheduled(ProceedingJoinPoint pjp) throws Throwable {
        String jobName = deriveJobName(pjp);
        JobExecution started = beginJob(jobName);

        try {
            Object out = pjp.proceed();
            markSuccess(started);
            return out;
        } catch (Throwable t) {
            markFailed(started, t);
            throw t;
        }
    }

    private String deriveJobName(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method m = sig.getMethod();
        return m.getDeclaringClass().getSimpleName() + "." + m.getName();
    }

    private JobExecution beginJob(String jobName) {
        try {
            return txTemplate.execute(status -> {
                JobExecution j = new JobExecution(UUID.randomUUID(), jobName);
                return jobRepo.save(j);
            });
        } catch (Exception e) {
            log.warn("JobExecution begin INSERT failed for {} : {} (non-blocking)", jobName, e.getMessage());
            return null;
        }
    }

    private void markSuccess(JobExecution started) {
        if (started == null) return;
        try {
            txTemplate.executeWithoutResult(status -> {
                jobRepo.findById(started.getId()).ifPresent(j -> {
                    j.markSuccess(null);
                    jobRepo.save(j);
                });
            });
        } catch (Exception e) {
            log.warn("JobExecution mark success UPDATE failed for {} : {}", started.getJobName(), e.getMessage());
        }
    }

    private void markFailed(JobExecution started, Throwable t) {
        if (started == null) return;
        String msg = t.getClass().getSimpleName() + " : " + (t.getMessage() == null ? "" : t.getMessage());
        if (msg.length() > MAX_ERR_LEN) msg = msg.substring(0, MAX_ERR_LEN);
        final String err = msg;
        try {
            txTemplate.executeWithoutResult(status -> {
                jobRepo.findById(started.getId()).ifPresent(j -> {
                    j.markFailed(err);
                    jobRepo.save(j);
                });
            });
        } catch (Exception e) {
            log.warn("JobExecution mark failed UPDATE failed for {} : {}", started.getJobName(), e.getMessage());
        }
    }
}
