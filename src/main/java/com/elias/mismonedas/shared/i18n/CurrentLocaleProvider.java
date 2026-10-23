package com.elias.mismonedas.shared.i18n;

import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.identity.security.Constants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CurrentLocaleProvider {

    public Locale currentLocale() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return SupportedLocale.toLocale(jwtAuthentication.getToken().getClaimAsString(Constants.LOCALE_CLAIM));
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return SupportedLocale.toLocale(jwt.getClaimAsString(Constants.LOCALE_CLAIM));
        }
        return SupportedLocale.toLocale(null);
    }
}
