package com.onesley.oneclick.core.ai.internal;

/**
 * Identifiant du modèle de chat <b>actif</b> (Ollama ou Groq), fourni par la config du provider
 * sélectionné ({@code app.ai.provider}). Injecté dans le service de chat pour les logs et le champ
 * {@code model} du résultat — découple le service du provider concret.
 *
 * @param value nom du modèle (ex. {@code llama3.2:3b}, {@code llama-3.3-70b-versatile})
 */
record AiChatModelName(String value) {
}
