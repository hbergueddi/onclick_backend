package com.onesley.oneclick;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test unitaire isolé du {@link StuckEventPublicationReaper} : émet l'UPDATE avec la grâce et renvoie le count. */
class StuckEventPublicationReaperTest {

    @Test
    void reap_issuesUpdateWithGrace_andReturnsCount() {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.executeUpdate()).thenReturn(4);

        StuckEventPublicationReaper reaper = new StuckEventPublicationReaper();
        ReflectionTestUtils.setField(reaper, "em", em);
        ReflectionTestUtils.setField(reaper, "graceDays", 3);

        int reaped = reaper.reapStuckPublications();

        assertThat(reaped).isEqualTo(4);
        verify(q).setParameter("days", 3);
        verify(q).executeUpdate();
    }
}
