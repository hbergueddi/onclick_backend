-- ════════════════════════════════════════════════════════════════════
-- V31 — Correctif V30 : colonnes texte qui doivent rester NON bornées
-- ════════════════════════════════════════════════════════════════════
-- V30 a borné par erreur des colonnes légitimement longues / non bornées :
--
--  (a) 4 colonnes @Column(columnDefinition="text") — intention dev EXPLICITE,
--      que le sweep n'aurait pas dû toucher :
--        - error_logs.stacktrace        (trace d'exception : peut être énorme)
--        - job_executions.error_message (message d'erreur job)
--        - contract_templates.body      (contenu légal du contrat)
--        - webhook_deliveries.response_body (réponse HTTP externe)
--      Un varchar(64/1024) les tronquerait → perte de diagnostic / contenu.
--
--  (b) event_publication_archive : table FRAMEWORK Spring Modulith (archive
--      des events). serialized_event / listener_id sont des payloads ; les
--      borner casse l'archivage (republish-outstanding-events-on-restart).
--      event_publication (active) était déjà exclue ; son jumeau _archive
--      avait été oublié.
--
-- text est un élargissement depuis varchar(N) → ALTER non destructif.
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE error_logs         ALTER COLUMN stacktrace    TYPE text;
ALTER TABLE job_executions     ALTER COLUMN error_message TYPE text;
ALTER TABLE contract_templates ALTER COLUMN body          TYPE text;
ALTER TABLE webhook_deliveries ALTER COLUMN response_body TYPE text;

ALTER TABLE event_publication_archive ALTER COLUMN serialized_event TYPE text;
ALTER TABLE event_publication_archive ALTER COLUMN listener_id      TYPE text;
ALTER TABLE event_publication_archive ALTER COLUMN event_type       TYPE text;
ALTER TABLE event_publication_archive ALTER COLUMN status           TYPE text;
