-- ============================================================================
-- V8 — Phase 2 §21 spec senior dev : Spring Modulith event store
-- ============================================================================
-- Tables event_publication + event_publication_archive utilisées par Spring
-- Modulith pour persister les events publiés via ApplicationEventPublisher.
--
-- Pattern : transactional outbox + retry automatique.
-- Quand un module publie un event, Modulith :
--   1. Persiste une row dans event_publication (status=null)
--   2. Invoque les @ApplicationModuleListener (async, après commit)
--   3. Si listener réussit → completion_date = now()
--   4. Si listener échoue → status='RESUBMITTED', retry plus tard
--   5. Si event externalisé Kafka → published via spring-modulith-events-kafka
--
-- Schéma extrait de spring-modulith-events-jdbc-2.0.6.jar
-- (resource path : org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql)
-- ============================================================================

CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);

CREATE TABLE IF NOT EXISTS event_publication_archive
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_archive_serialized_event_hash_idx
    ON event_publication_archive USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_archive_by_completion_date_idx
    ON event_publication_archive (completion_date);
