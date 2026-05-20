/**
 * Infra temps réel partagée (Bug 37) — WebSocket/STOMP + base des publishers de
 * dashboard. Module OPEN (comme {@code cache} et {@code audit}) : c'est de
 * l'infrastructure transverse dont tout module métier peut dépendre pour exposer
 * son dashboard en temps réel (extends {@code AbstractDashboardPublisher}).
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    displayName = "realtime (shared infra)"
)
package com.onesley.oneclick.realtime;
