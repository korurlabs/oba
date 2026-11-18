package com.korur.oba.apps.kaya.identity.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Locale;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;

/**
 * Normalises email addresses, so that machine accounts, git emails and platform sign-in emails
 * compare the same lower-case values.
 */
public final class EmailDomainHelper {

    private EmailDomainHelper() {
        throw new UnsupportedOperationException(CommonConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * Trims an email address and converts it to lower case.
     *
     * @param email the address, may be null
     * @return the normalised address, or null for null
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
