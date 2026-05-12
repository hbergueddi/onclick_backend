package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour le module {@code core/identity} — liste paginée + 404 lookup.
 */
class UserSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getUsers_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/users?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserByUnknownId_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/users/00000000-0000-0000-0000-000000000099"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        // SUPERADMIN passe SecurityHelper.requireOwnerOrAdmin → on atteint le service
        // qui throw NotFoundException → 404 attendu.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
