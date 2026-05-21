package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.social.api.SocialExtensionDtos.EliteApplicationCreateDto;
import com.onesley.oneclick.modules.social.api.SocialExtensionDtos.EliteApplicationReviewDto;
import com.onesley.oneclick.modules.social.api.SocialExtensionDtos.RestaurantGroupCreateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link SocialExtensionService} (L3 — modules.social).
 * Elite applications (create/review) + restaurant groups (CRUD + soft delete).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class SocialExtensionServiceTest {

    @Mock EliteApplicationRepository eliteRepo;
    @Mock RestaurantGroupRepository groupRepo;
    @InjectMocks SocialExtensionService service;

    SocialExtensionServiceTest() {}

    private EliteApplication application() {
        EliteApplication a = new EliteApplication();
        a.setUserId(UUID.randomUUID());
        a.setStatus("pending");
        return a;
    }
    private RestaurantGroup restaurantGroup() {
        RestaurantGroup g = new RestaurantGroup();
        g.setName("Groupe");
        g.setOwnerId(UUID.randomUUID());
        return g;
    }

    @Test
    void eliteApplications_listingsMap() {
        lenient().when(eliteRepo.findAllActive()).thenReturn(List.of(application()));
        lenient().when(eliteRepo.findByUser(any())).thenReturn(List.of(application()));
        lenient().when(eliteRepo.findByStatus(any())).thenReturn(List.of(application()));
        assertThat(service.findAllApplications()).hasSize(1);
        assertThat(service.findUserApplications(UUID.randomUUID())).hasSize(1);
        assertThat(service.findApplicationsByStatus("pending")).hasSize(1);
    }

    @Test
    void createApplication_setsPending() {
        when(eliteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createApplication(new EliteApplicationCreateDto(
            UUID.randomUUID(), "je veux rejoindre", UUID.randomUUID()))).isNotNull();
    }

    @Test
    void reviewApplication_notFound_throwsNotFound() {
        when(eliteRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reviewApplication(UUID.randomUUID(),
            new EliteApplicationReviewDto("approved", UUID.randomUUID(), null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void reviewApplication_success_updatesStatus() {
        EliteApplication a = application();
        when(eliteRepo.findById(any())).thenReturn(Optional.of(a));
        when(eliteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.reviewApplication(UUID.randomUUID(), new EliteApplicationReviewDto("rejected", UUID.randomUUID(), "incomplet"));
        assertThat(a.getStatus()).isEqualTo("rejected");
        assertThat(a.getReviewedAt()).isNotNull();
    }

    @Test
    void restaurantGroups_listingsMap() {
        lenient().when(groupRepo.findAllActive()).thenReturn(List.of(restaurantGroup()));
        lenient().when(groupRepo.findByOwner(any())).thenReturn(List.of(restaurantGroup()));
        assertThat(service.findAllGroups()).hasSize(1);
        assertThat(service.findOwnerGroups(UUID.randomUUID())).hasSize(1);
    }

    @Test
    void findGroup_notFoundAndSuccess() {
        when(groupRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findGroup(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        RestaurantGroup g = restaurantGroup();
        when(groupRepo.findById(g.getId())).thenReturn(Optional.of(g));
        assertThat(service.findGroup(g.getId())).isNotNull();
    }

    @Test
    void createGroup_success() {
        when(groupRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createGroup(new RestaurantGroupCreateDto(
            UUID.randomUUID(), "Resto Group", "desc", UUID.randomUUID(), "http://logo"))).isNotNull();
    }

    @Test
    void updateGroup_notFound_throwsNotFound() {
        when(groupRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateGroup(UUID.randomUUID(),
            new RestaurantGroupCreateDto(null, "X", null, null, null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateGroup_success_appliesNonNullFields() {
        RestaurantGroup g = restaurantGroup();
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.updateGroup(g.getId(), new RestaurantGroupCreateDto(null, "Renommé", "nouvelle desc", UUID.randomUUID(), "http://new"));
        assertThat(g.getName()).isEqualTo("Renommé");
    }

    @Test
    void deleteGroup_notFoundAndSuccess() {
        when(groupRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteGroup(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        RestaurantGroup g = restaurantGroup();
        when(groupRepo.findById(g.getId())).thenReturn(Optional.of(g));
        when(groupRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.deleteGroup(g.getId());
        assertThat(g.getDeletedAt()).isNotNull();
        verify(groupRepo).save(g);
    }
}
