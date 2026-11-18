package com.korur.oba.apps.kazan.dailyupdate.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;

class DailyUpdateNudgeMapperUnitTest {

    private final DailyUpdateNudgeMapper mapper = new DailyUpdateNudgeMapperImpl();

    private User user(String firstName, String lastName) {
        return User.builder().email("ayse@example.com")
                .profile(UserProfile.builder().firstName(firstName).lastName(lastName).build()).build();
    }

    @Test
    void toSenderName_firstAndLastName_joinsThem() {
        assertThat(mapper.toSenderName(user("Ayşe", "Kaya"))).isEqualTo("Ayşe Kaya");
    }

    @Test
    void toSenderName_onlyFirstName_returnsIt() {
        assertThat(mapper.toSenderName(user(" Ayşe ", null))).isEqualTo("Ayşe");
    }

    @Test
    void toSenderName_onlyLastName_returnsIt() {
        assertThat(mapper.toSenderName(user("  ", "Kaya"))).isEqualTo("Kaya");
    }

    @Test
    void toSenderName_noName_fallsBackToEmail() {
        assertThat(mapper.toSenderName(user(null, ""))).isEqualTo("ayse@example.com");
    }

    @Test
    void toSenderName_nullProfile_fallsBackToEmail() {
        // Arrange
        User user = User.builder().email("ayse@example.com").build();
        user.setProfile(null);

        // Act and assert
        assertThat(mapper.toSenderName(user)).isEqualTo("ayse@example.com");
    }
}
