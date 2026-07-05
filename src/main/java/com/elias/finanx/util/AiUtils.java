package com.elias.finanx.util;

import com.elias.finanx.entity.enums.ChatSkill;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilidades de IA.
 */
@Slf4j
public final class AiUtils {

    private AiUtils() {}


    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("(?i)(ignore|forget|disregard|olvida|ignora|no consideres|descarta).{0,30}(previous|above|instruction|system|prompt|instrucci[oo]n|contexto|mensaje)"),
            Pattern.compile("(?i)(you are now|act as|pretend|roleplay|jailbreak|ahora eres|act[úu]a como|finge|haz de cuenta|simula)"),
            Pattern.compile("(?i)<\\s*(script|iframe|img|svg|object|embed|link|etiqueta|javascript)[^>]*>"),
            Pattern.compile("(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|UNION|--|;\\s*--|SQL|consulta|base de datos)"),
            Pattern.compile("(?i)(api[_\\-]?key|secret|token|password|bearer|authorization|contrase[ñn]a|clave|llave|credencial|secreto)\\s*[:=]\\s*\\S+"),
            Pattern.compile("(?i)\\$\\{.*?\\}"),
            Pattern.compile("(?i)\\{\\{.*?\\}\\}"),
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")
    );


    /**
     * Keywords que indican intencion financiera/analitica.
     * Si el mensaje no contiene ninguna → CHITCHAT.
     */
    private static final List<Pattern> ADVISOR_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(gast[oo]s?|ingres[oo]s?|balance|presupuest[oa]?|ahorro|deuda[s]?|categoria[s]?|pago[s]?|transacci[oo]n(es)?|flujo de caja|capital|inversi[oo]n|patrimoni[oo])"),

            Pattern.compile("(?i)\\b(cu[aá]nto|cuanto|analiz(ar|o)|resum(ir|en)|compar(ar|aci[oo]n)|mes(es)?|periodo|finanz(as|a)?|proyecci[oo]n|simulaci[oo]n|evaluaci[oo]n|diagn[oo]stico)"),

            Pattern.compile("(?i)\\b(insight|consejo(s)? financiero(s)?|situaci[oo]n financiera|debo preocupar(me)?|estoy bien|recomendaci[oo]n(es)?|alerta(s)?|riesgo(s)?|oportunidad(es)?)"),

            Pattern.compile("(?i)\\b(spend|income|budget|saving|expense|payment|financial|debt|cashflow|investment|assets|liabilities|forecast|advice|insight|analysis|report|summary|trend|projection)")
    );


    /**
     * Detecta la skill apropiada segun el contenido del mensaje del usuario.
     * ADVISOR si hay intencion financiera explicita, CHITCHAT en caso contrario.
     */
    public static ChatSkill detectSkill(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return ChatSkill.CHITCHAT;
        for (Pattern pattern : ADVISOR_PATTERNS) {
            if (pattern.matcher(userMessage).find()) {
                return ChatSkill.ADVISOR;
            }
        }
        return ChatSkill.CHITCHAT;
    }

    /**
     * Sanitiza el prompt del usuario: trunca, detecta patrones sospechosos y escapa.
     * Retorna "[consulta no disponible]" si el input es malicioso.
     */
    public static String sanitizeUserPrompt(String input, int maxLength) {
        if (input == null || input.isBlank()) return "";

        String trimmed = input.strip();

        if (trimmed.length() > maxLength) {
            log.warn("[AiUtils] Prompt truncado: {} → {} chars", trimmed.length(), maxLength);
            trimmed = trimmed.substring(0, maxLength);
        }

        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                log.warn("[AiUtils] Prompt sospechoso rechazado. Patron: {}", pattern.pattern());
                return "[consulta no disponible]";
            }
        }

        return escapeForPrompt(trimmed);
    }

    /**
     * Escapa caracteres especiales para insercion segura en prompts de texto.
     */
    public static String escapeForPrompt(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}