package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Port de <b>découpage</b> — domaine {@code core/ai}.
 *
 * <p>Transforme un {@link RawDocument} en une liste de {@link DocumentChunk} adaptés à l'embedding
 * (taille bornée, chevauchement éventuel). La stratégie (par tokens, par phrases, taille fixe…) relève
 * de l'implémentation ; le contrat reste neutre.
 *
 * <p><b>Fondation uniquement</b> : aucune implémentation à ce stade.
 */
public interface Chunker {

    /**
     * Découpe un document brut en fragments indexables.
     *
     * @param document document source
     * @return fragments dans l'ordre du document (peut être vide, jamais nul)
     */
    List<DocumentChunk> chunk(RawDocument document);
}
