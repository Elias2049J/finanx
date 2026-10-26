package com.elias.mismonedas.identity.util;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

public final class EmailAddressValidator {

    private static final int MAX_EMAIL_LENGTH = 320;

    private EmailAddressValidator() {
    }

    public static String normalize(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        if (email == null || email.isBlank() || email.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidEmailAddressException();
        }

        try {
            InternetAddress[] addresses = InternetAddress.parse(email, true);
            if (addresses.length != 1 || !email.equals(addresses[0].getAddress())) {
                throw new InvalidEmailAddressException();
            }
            addresses[0].validate();
            return email;
        } catch (AddressException e) {
            throw new InvalidEmailAddressException();
        }
    }
}
