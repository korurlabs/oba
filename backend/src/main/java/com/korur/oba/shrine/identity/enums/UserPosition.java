package com.korur.oba.shrine.identity.enums;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the functional role or position of a user within an organization.
 * Ordered and numbered according to Quercis ContactFunction (1–13).
 */
public enum UserPosition {

    PROCUREMENT,               // 1 - Inkoop
    SALES,                     // 2 - Verkoop
    PRODUCTION,                // 3 - Productie
    MARKETING,                 // 4 - Marketing
    OFFICE,                    // 5 - Kantoor
    LOGISTIC,                  // 6 - Logistiek
    WAREHOUSE,                 // 7 - Inventory
    MANAGEMENT,                // 8 - Management
    ADMINISTRATION,            // 9 - Administratie
    ICT,                       // 10 - ICT
    RESEARCH_AND_DEVELOPMENT,  // 12 - R&D
    OTHER;                     // 13 - Anders

    // Mapping of enum to Quercis ContactFunction number
    private static final Map<UserPosition, Integer> positionToQuercisCode = new HashMap<>();

    static {
        positionToQuercisCode.put(PROCUREMENT, 1);
        positionToQuercisCode.put(SALES, 2);
        positionToQuercisCode.put(PRODUCTION, 3);
        positionToQuercisCode.put(MARKETING, 4);
        positionToQuercisCode.put(OFFICE, 5);
        positionToQuercisCode.put(LOGISTIC, 6);
        positionToQuercisCode.put(WAREHOUSE, 7);
        positionToQuercisCode.put(MANAGEMENT, 8);
        positionToQuercisCode.put(ADMINISTRATION, 9);
        positionToQuercisCode.put(ICT, 10);
        positionToQuercisCode.put(RESEARCH_AND_DEVELOPMENT, 12);
        positionToQuercisCode.put(OTHER, 13);
    }

    /**
     * Gets the corresponding Quercis ContactFunction number.
     *
     * @return the ContactFunction number or null if not mapped
     */
    public Integer getQuercisCode() {
        return positionToQuercisCode.get(this);
    }

    /**
     * Reverse lookup: Find UserPosition by Quercis ContactFunction number.
     *
     * @param quercisCode the numeric ContactFunction code
     * @return the corresponding UserPosition, or null if not found
     */
    public static UserPosition fromQuercisCode(int quercisCode) {
        for (Map.Entry<UserPosition, Integer> entry : positionToQuercisCode.entrySet()) {
            if (entry.getValue() == quercisCode) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Reverse lookup: Find UserPosition by Quercis ContactFunction number.
     *
     * @param contactFunction the numeric ContactFunction code
     * @return the corresponding UserPosition, or null if not found
     */
    public static UserPosition fromContactFunction(String contactFunction) {
        if (contactFunction == null || contactFunction.isBlank()) {
            return OTHER;
        }

        return switch (contactFunction.trim()) {
            case "1" -> PROCUREMENT;
            case "2" -> SALES;
            case "3" -> PRODUCTION;
            case "4" -> MARKETING;
            case "5" -> OFFICE;
            case "6" -> LOGISTIC;
            case "7" -> WAREHOUSE;
            case "8" -> MANAGEMENT;
            case "9" -> ADMINISTRATION;
            case "10" -> ICT;
            case "12" -> RESEARCH_AND_DEVELOPMENT;
            case "13" -> OTHER;
            default -> OTHER;
        };
    }
}
