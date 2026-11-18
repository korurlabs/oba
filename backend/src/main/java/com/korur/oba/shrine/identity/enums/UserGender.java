package com.korur.oba.shrine.identity.enums;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserGender {
    MALE,
    FEMALE,
    UNKNOWN;

    public static String toCustomerGender(UserGender gender) {
        if (gender == null) {
            return "u";
        }

        switch (gender) {
            case FEMALE:
                return "v";
            case MALE:
                return "m";
            case UNKNOWN:
                return "u";
            default:
                return "u";
        }
    }

    public static UserGender mapContactGenderToUserGender(String contactGender) {
        if (contactGender == null || contactGender.isBlank()) {
            return UNKNOWN;
        }

        return switch (contactGender.trim()) {
            case "m" -> MALE;
            case "v" -> FEMALE;
            default -> UNKNOWN;
        };
    }
}
