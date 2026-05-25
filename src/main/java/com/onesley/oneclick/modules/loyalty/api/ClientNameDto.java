package com.onesley.oneclick.modules.loyalty.api;

import java.util.UUID;

/**
 * Nom d'un client résolu pour les dashboards staff (PulsePro — Top clients).
 *
 * <p>Renvoyé par {@code POST /api/loyalty/clients/names} : alternative <b>scoped</b> à
 * {@code POST /api/users/by-ids} (VIEW:USERS, refusé au RESTAURATEUR/STAFF). L'endpoint
 * ne renvoie que les clients ayant un compte fidélité à l'un des restaurants dont
 * l'appelant est staff/admin — pas d'énumération d'utilisateurs arbitraires.</p>
 */
public record ClientNameDto(UUID id, String firstName, String lastName, String phone) {}
