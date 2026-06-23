package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.media.api.MediaAccessApi;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaDto;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantPhotoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito (L3 isolé) de {@link RestaurantPhotoService}.
 *
 * <p>Couvre les RÈGLES métier de la galerie « Identité visuelle » sans contexte Spring
 * ni I/O : quota max 5, 1re photo / makePrimary ⇒ principale synchronisée sur
 * {@code restaurants.image}, suppression de la principale ⇒ réaffectation, anti-IDOR
 * cross-restaurant. Le stockage ({@link MediaAccessApi}) et le dépôt sont mockés.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantPhotoServiceTest {

    @Mock MediaAccessApi mediaAccess;
    @Mock RestaurantRepository restaurantRepository;

    RestaurantPhotoService service;

    private final UUID rid = UUID.randomUUID();
    private Restaurant restaurant;

    @BeforeEach
    void setup() {
        service = new RestaurantPhotoService(mediaAccess, restaurantRepository);
        restaurant = org.mockito.Mockito.mock(Restaurant.class);
        lenient().when(restaurant.getDeletedAt()).thenReturn(null);
        lenient().when(restaurantRepository.findById(rid)).thenReturn(Optional.of(restaurant));
        lenient().when(restaurantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MediaDto media(UUID id, String url, int sortOrder) {
        return new MediaDto(id, RestaurantPhotoService.ENTITY_TYPE, rid, url, "image",
            "image/jpeg", 1024L, sortOrder, Map.of(), Instant.now());
    }

    private MultipartFile file(boolean empty) {
        MultipartFile f = org.mockito.Mockito.mock(MultipartFile.class);
        lenient().when(f.isEmpty()).thenReturn(empty);
        return f;
    }

    // ─── Quota max 5 ───────────────────────────────────────────────────────────

    @Test
    void upload_whenAlreadyFive_throwsBadRequest_andDoesNotUpload() {
        when(mediaAccess.countForEntity(eq("restaurant"), eq(rid))).thenReturn(5L);
        assertThatThrownBy(() -> service.upload(rid, file(false), false))
            .isInstanceOf(BadRequestException.class);
        verify(mediaAccess, never()).upload(any(), any(), any(), any());
    }

    @Test
    void upload_emptyFile_throwsBadRequest() {
        assertThatThrownBy(() -> service.upload(rid, file(true), false))
            .isInstanceOf(BadRequestException.class);
        verify(mediaAccess, never()).upload(any(), any(), any(), any());
    }

    // ─── Photo principale ⇄ restaurants.image ───────────────────────────────────

    @Test
    void upload_firstPhoto_becomesPrimary_syncsImage() {
        when(restaurant.getImage()).thenReturn(null);
        when(mediaAccess.countForEntity(eq("restaurant"), eq(rid))).thenReturn(0L);
        UUID mId = UUID.randomUUID();
        when(mediaAccess.upload(eq("restaurant"), eq(rid), any(), anyInt())).thenReturn(media(mId, "u1", 0));

        RestaurantPhotoDto result = service.upload(rid, file(false), false);

        assertThat(result.primary()).isTrue();
        verify(restaurant).setImage("u1");
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    void upload_secondaryWithoutMakePrimary_keepsExistingImage() {
        when(restaurant.getImage()).thenReturn("existing");
        when(mediaAccess.countForEntity(eq("restaurant"), eq(rid))).thenReturn(1L);
        when(mediaAccess.upload(eq("restaurant"), eq(rid), any(), anyInt())).thenReturn(media(UUID.randomUUID(), "u2", 1));

        RestaurantPhotoDto result = service.upload(rid, file(false), false);

        assertThat(result.primary()).isFalse();
        verify(restaurant, never()).setImage(any());
    }

    @Test
    void upload_makePrimaryTrue_syncsImage() {
        when(restaurant.getImage()).thenReturn("existing");
        when(mediaAccess.countForEntity(eq("restaurant"), eq(rid))).thenReturn(2L);
        when(mediaAccess.upload(eq("restaurant"), eq(rid), any(), anyInt())).thenReturn(media(UUID.randomUUID(), "u3", 2));

        RestaurantPhotoDto result = service.upload(rid, file(false), true);

        assertThat(result.primary()).isTrue();
        verify(restaurant).setImage("u3");
    }

    @Test
    void setPrimary_syncsImage_andFlagsInList() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        when(mediaAccess.findById(m2)).thenReturn(media(m2, "u2", 1));
        when(mediaAccess.listForEntity(eq("restaurant"), eq(rid)))
            .thenReturn(List.of(media(m1, "u1", 0), media(m2, "u2", 1)));

        List<RestaurantPhotoDto> result = service.setPrimary(rid, m2);

        verify(restaurant).setImage("u2");
        // Principale en tête + flag correct.
        assertThat(result.get(0).url()).isEqualTo("u2");
        assertThat(result.get(0).primary()).isTrue();
        assertThat(result.stream().filter(RestaurantPhotoDto::primary).count()).isEqualTo(1);
    }

    @Test
    void setPrimary_foreignMedia_throwsNotFound() {
        UUID foreign = UUID.randomUUID();
        // Média appartenant à un AUTRE restaurant → 404 anti-IDOR.
        when(mediaAccess.findById(foreign)).thenReturn(new MediaDto(
            foreign, "restaurant", UUID.randomUUID(), "x", "image", null, null, 0, Map.of(), Instant.now()));
        assertThatThrownBy(() -> service.setPrimary(rid, foreign)).isInstanceOf(NotFoundException.class);
        verify(restaurant, never()).setImage(any());
    }

    // ─── Suppression ─────────────────────────────────────────────────────────────

    @Test
    void delete_primary_reassignsToNextRemaining() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        when(restaurant.getImage()).thenReturn("u1");
        when(mediaAccess.findById(m1)).thenReturn(media(m1, "u1", 0));
        // Après suppression : seule u2 reste.
        when(mediaAccess.listForEntity(eq("restaurant"), eq(rid))).thenReturn(List.of(media(m2, "u2", 1)));

        service.delete(rid, m1);

        verify(mediaAccess).delete(m1);
        verify(restaurant).setImage("u2");
    }

    @Test
    void delete_primary_noneLeft_clearsImage() {
        UUID m1 = UUID.randomUUID();
        when(restaurant.getImage()).thenReturn("u1");
        when(mediaAccess.findById(m1)).thenReturn(media(m1, "u1", 0));
        when(mediaAccess.listForEntity(eq("restaurant"), eq(rid))).thenReturn(List.of());

        service.delete(rid, m1);

        verify(restaurant).setImage(null);
    }

    @Test
    void delete_nonPrimary_keepsImage() {
        UUID m2 = UUID.randomUUID();
        when(restaurant.getImage()).thenReturn("u1");
        when(mediaAccess.findById(m2)).thenReturn(media(m2, "u2", 1));

        service.delete(rid, m2);

        verify(mediaAccess).delete(m2);
        verify(restaurant, never()).setImage(any());
    }

    // ─── Restaurant inexistant ────────────────────────────────────────────────────

    @Test
    void list_restaurantNotFound_throwsNotFound() {
        UUID missing = UUID.randomUUID();
        when(restaurantRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(missing)).isInstanceOf(NotFoundException.class);
    }
}
