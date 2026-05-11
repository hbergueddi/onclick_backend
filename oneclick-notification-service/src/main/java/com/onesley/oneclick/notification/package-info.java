/**
 * Microservice notification — Phase 2 §21 spec senior dev (1er service extrait).
 *
 * <p>Service standalone Spring Boot :
 * <ul>
 *   <li>REST API publique sur {@code /api/notifications} (port 8084)</li>
 *   <li>Consumer Kafka topic {@code reservation.created} → auto-création notif client</li>
 *   <li>DB partagée {@code oneclick_enterprise} (tables {@code notifications},
 *       {@code notification_campaigns}, {@code device_tokens} uniquement)</li>
 * </ul>
 *
 * <h3>Tables possédées</h3>
 * <ul>
 *   <li>{@code notifications} — notif unitaire avec channel explicite (push/email/inapp/sms)</li>
 *   <li>{@code notification_campaigns} — campagnes marketing programmées</li>
 *   <li>{@code device_tokens} — tokens FCM/APNs par device pour push mobile</li>
 * </ul>
 */
package com.onesley.oneclick.notification;
