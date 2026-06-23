package com.onesley.oneclick.modules.announcement.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AnnouncementCronJobs} (B13) — le cron délègue à
 * {@code AnnouncementService.publishDueScheduled()} (l'idempotence et la résolution réelle des
 * annonces dues sont testées côté {@code AnnouncementServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementCronJobsTest {

    @Mock AnnouncementService announcementService;
    @InjectMocks AnnouncementCronJobs cron;

    @Test
    void publishDueAnnouncements_delegatesToService() {
        when(announcementService.publishDueScheduled()).thenReturn(3);
        cron.publishDueAnnouncements();
        verify(announcementService).publishDueScheduled();
    }

    @Test
    void publishDueAnnouncements_zeroDue_stillDelegates() {
        when(announcementService.publishDueScheduled()).thenReturn(0);
        cron.publishDueAnnouncements();
        verify(announcementService).publishDueScheduled();
    }
}
