package com.korur.oba.shrine.identity.enums;

public enum UserPreferredLanguage {

    DUTCH, // Dutch

    ENGLISH,

    DEUTSCH, // German

    FRENCH, // French

    SPANISH, // Spanish

    ITALIAN,

    TURKISH;

    public static String toCustomerLanguage(UserPreferredLanguage preferredLanguage) {
        if (preferredLanguage == null) {
            return "NL";
        }

        switch (preferredLanguage) {
            case DEUTSCH:
                return "DE";
            case DUTCH:
                return "NL";
            case ENGLISH:
                return "EN";
            case FRENCH:
                return "FR";
            case ITALIAN:
                return "IT";
            case SPANISH:
                return "SP";
            case TURKISH:
                return "TR";
            default:
                return "NL";
        }
    }

    public static UserPreferredLanguage mapContactLanguageToUserPreferredLanguage(String contactLanguage) {
        if (contactLanguage == null || contactLanguage.isBlank()) {
            return DUTCH;
        }

        switch (contactLanguage.trim()) {
            case "NL":
                return DUTCH;
            case "EN":
                return ENGLISH;
            case "DE":
                return DEUTSCH;
            case "FR":
                return FRENCH;
            case "SP":
                return SPANISH;
            case "IT":
                return ITALIAN;
            case "TR":
                return TURKISH;
            default:
                return DUTCH;
        }
    }
}
