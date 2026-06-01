package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.identity.api.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés de {@link UserDirectoryService} (P2 — query-API annuaire).
 *
 * <p>Le repository est mocké ; l'entité {@code User} est construite réellement
 * (pas de mock d'entité JPA — getters de base potentiellement final).
 */
class UserDirectoryServiceTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final UserDirectoryService service = new UserDirectoryService(repo);

    private User user(UUID id, String first, String last, String phone, String email, boolean deleted) {
        User u = new User(id, null, email, null, first, last);
        u.setPhone(phone);
        if (deleted) u.markDeleted();
        return u;
    }

    @Test
    void nameById_activeUser_mapsProjection() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(user(id, "Ali", "Bennani", "+212", "ali@x.ma", false)));

        Optional<UserName> res = service.nameById(id);

        assertThat(res).isPresent();
        assertThat(res.get()).isEqualTo(new UserName(id, "Ali", "Bennani", "+212", "ali@x.ma"));
    }

    @Test
    void nameById_softDeletedUser_filteredOut() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(user(id, "X", "Y", null, "x@x.ma", true)));

        assertThat(service.nameById(id)).isEmpty();
    }

    @Test
    void nameById_null_returnsEmpty_noRepoCall() {
        assertThat(service.nameById(null)).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void namesByIds_empty_shortCircuits_noRepoCall() {
        assertThat(service.namesByIds(List.of())).isEmpty();
        assertThat(service.namesByIds(null)).isEmpty();
        verify(repo, never()).findAllByIds(any());
    }

    @Test
    void namesByIds_mapsAll() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findAllByIds(List.of(a, b)))
            .thenReturn(List.of(user(a, "Ali", "B", null, "a@x.ma", false), user(b, "Sara", "C", "+1", "b@x.ma", false)));

        List<UserName> res = service.namesByIds(List.of(a, b));

        assertThat(res).containsExactlyInAnyOrder(
            new UserName(a, "Ali", "B", null, "a@x.ma"),
            new UserName(b, "Sara", "C", "+1", "b@x.ma"));
        verify(repo).findAllByIds(List.of(a, b));
    }
}
