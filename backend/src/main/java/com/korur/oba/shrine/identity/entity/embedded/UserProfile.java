package com.korur.oba.shrine.identity.entity.embedded;

// 1. Framework-level
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 4. Internal
import com.korur.oba.shrine.identity.enums.UserGender;
import com.korur.oba.shrine.identity.enums.UserPosition;
import com.korur.oba.shrine.identity.enums.UserPreferredLanguage;

@Embeddable
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfile {

    /**
     * User's given name.
     */
    @Column(name = "first_name", length = 255, nullable = true)
    private String firstName;

    /**
     * User's family name or lastName.
     */
    @Column(name = "last_name", length = 255, nullable = true)
    private String lastName;

    /**
     * User's contact phone number. Convention: 30
     */
    @Column(name = "phone_number", length = 30, nullable = true)
    private String phoneNumber;

    //---Enums---//
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "user_position", nullable = true)
    private UserPosition userPosition;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "user_preferred_language", nullable = true)
    private UserPreferredLanguage userPreferredLanguage = UserPreferredLanguage.ENGLISH;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "user_gender", nullable = true)
    private UserGender userGender = UserGender.UNKNOWN;
    //---Enums---//
}
