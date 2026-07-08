package com.onesley.oneclick.core.ai.api;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Port de <b>chargement de documents</b> — domaine {@code core/ai}.
 *
 * <p>Extrait le contenu textuel d'une source binaire (PDF, txt, HTML, upload…) sous forme de
 * {@link RawDocument}. Contrat neutre : n'expose que des types JDK. Les implémentations (à venir) sont
 * spécifiques au format ; le domaine et le pipeline dépendent de cette interface.
 *
 * <p><b>Fondation uniquement</b> : aucune implémentation à ce stade.
 */
public interface DocumentLoader {

    /**
     * Charge un ou plusieurs documents bruts depuis un flux d'entrée.
     *
     * @param source        flux binaire à lire (le fournisseur ne le ferme pas ; à la charge de l'appelant)
     * @param sourceMetadata métadonnées de source (nom de fichier, type MIME…) propagées aux documents
     * @return les documents bruts extraits (peut être vide, jamais nul)
     */
    List<RawDocument> load(InputStream source, Map<String, Object> sourceMetadata);
}
