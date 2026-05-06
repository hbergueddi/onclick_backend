package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.ChatMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ChatMessage} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID>, JpaSpecificationExecutor<ChatMessage> {
}
