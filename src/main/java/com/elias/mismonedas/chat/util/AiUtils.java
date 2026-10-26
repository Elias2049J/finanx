package com.elias.mismonedas.chat.util;

import com.elias.mismonedas.chat.domain.enums.ChatSkill;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;



@Slf4j
public final class AiUtils {

    private AiUtils() {}


    private static final List<Pattern> ADVISOR_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(gast[oo]s?|ingres[oo]s?|balance|presupuest[oa]?|ahorro|deuda[s]?|categoria[s]?|pago[s]?|transacci[oo]n(es)?|flujo de caja|capital|inversi[oo]n|patrimoni[oo])"),

            Pattern.compile("(?i)\\b(cu[aá]nto|cuanto|analiz(ar|o)|resum(ir|en)|compar(ar|aci[oo]n)|mes(es)?|periodo|finanz(as|a)?|proyecci[oo]n|simulaci[oo]n|evaluaci[oo]n|diagn[oo]stico)"),

            Pattern.compile("(?i)\\b(insight|consejo(s)? financiero(s)?|situaci[oo]n financiera|debo preocupar(me)?|estoy bien|recomendaci[oo]n(es)?|alerta(s)?|riesgo(s)?|oportunidad(es)?)"),

            Pattern.compile("(?i)\\b(spend|income|budget|saving|expense|payment|financial|debt|cashflow|investment|assets|liabilities|forecast|advice|insight|analysis|report|summary|trend|projection)")
    );





    public static ChatSkill detectSkill(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return ChatSkill.CHITCHAT;
        for (Pattern pattern : ADVISOR_PATTERNS) {
            if (pattern.matcher(userMessage).find()) {
                return ChatSkill.ADVISOR;
            }
        }
        return ChatSkill.CHITCHAT;
    }




    public static String sanitizeUserPrompt(String input, int maxLength) {
        if (input == null || input.isBlank()) return "";

        String trimmed = input.strip();

        if (trimmed.length() > maxLength) {
            log.warn("[AiUtils] Prompt truncado: {} → {} chars", trimmed.length(), maxLength);
            trimmed = trimmed.substring(0, maxLength);
        }

        return escapeForPrompt(trimmed);
    }



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
