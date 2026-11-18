package com.korur.oba.connect.alertmanager.validator;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.config.AlertmanagerConfig;
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;
import com.korur.oba.connect.alertmanager.properties.AlertmanagerProperties;

class AlertmanagerTokenValidatorUnitTest {

    private static final String TOKEN = "s3cret-token-value";

    private final UUID organizationId = UUID.randomUUID();

    @Configuration
    @EnableConfigurationProperties
    static class BindingConfiguration {
    }

    private AlertmanagerTokenValidator validator(String configured) {
        AlertmanagerProperties properties = new AlertmanagerProperties();
        properties.setToken(configured);
        properties.setOrganizationId(organizationId);
        return new AlertmanagerTokenValidator(properties);
    }

    @Test
    void resolveOrganizationId_rightToken_returnsBoundOrganization() {
        assertThat(validator(TOKEN).resolveOrganizationId("Bearer " + TOKEN)).contains(organizationId);
    }

    @Test
    void resolveOrganizationId_wrongToken_returnsEmpty() {
        assertThat(validator(TOKEN).resolveOrganizationId("Bearer other-token")).isEmpty();
    }

    @Test
    void resolveOrganizationId_nullHeader_returnsEmpty() {
        assertThat(validator(TOKEN).resolveOrganizationId(null)).isEmpty();
    }

    @Test
    void resolveOrganizationId_blankHeader_returnsEmpty() {
        assertThat(validator(TOKEN).resolveOrganizationId("  ")).isEmpty();
    }

    @Test
    void resolveOrganizationId_basicScheme_returnsEmpty() {
        assertThat(validator(TOKEN).resolveOrganizationId("Basic " + TOKEN)).isEmpty();
    }

    @Test
    void resolveOrganizationId_bearerWithExtraSpace_returnsEmpty() {
        assertThat(validator(TOKEN).resolveOrganizationId("Bearer  " + TOKEN)).isEmpty();
    }

    @Test
    void resolveOrganizationId_blankConfiguredToken_returnsEmpty() {
        assertThat(validator("").resolveOrganizationId("Bearer ")).isEmpty();
        assertThat(validator("   ").resolveOrganizationId("Bearer    ")).isEmpty();
    }

    @Test
    void resolveOrganizationId_nullConfiguredToken_returnsEmpty() {
        Optional<UUID> result = validator(null).resolveOrganizationId("Bearer " + TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    void constructor_tokenWithoutOrganization_throwsIllegalState() {
        AlertmanagerProperties properties = new AlertmanagerProperties();
        properties.setToken(TOKEN);

        assertThatThrownBy(() -> new AlertmanagerTokenValidator(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(AlertmanagerConstant.TOKEN_WITHOUT_ORGANIZATION);
    }

    @Test
    void constructor_noTokenAndNoOrganization_startsWithoutIntegration() {
        AlertmanagerProperties properties = new AlertmanagerProperties();

        AlertmanagerTokenValidator validator = new AlertmanagerTokenValidator(properties);

        assertThat(validator.resolveOrganizationId("Bearer anything")).isEmpty();
    }

    @Test
    void context_tokenWithoutOrganization_failsToStart() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class, AlertmanagerConfig.class,
                        AlertmanagerTokenValidator.class)
                .withPropertyValues("oba.integrations.alertmanager.token=" + TOKEN,
                        "oba.integrations.alertmanager.organization-id=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(AlertmanagerConstant.TOKEN_WITHOUT_ORGANIZATION);
                });
    }

    @Test
    void context_tokenWithOrganization_startsAndBindsOrganization() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class, AlertmanagerConfig.class,
                        AlertmanagerTokenValidator.class)
                .withPropertyValues("oba.integrations.alertmanager.token=" + TOKEN,
                        "oba.integrations.alertmanager.organization-id=" + organizationId)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AlertmanagerTokenValidator.class)
                            .resolveOrganizationId("Bearer " + TOKEN)).contains(organizationId);
                });
    }

    @Test
    void context_noTokenAndNoOrganization_starts() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class, AlertmanagerConfig.class,
                        AlertmanagerTokenValidator.class)
                .withPropertyValues("oba.integrations.alertmanager.token=",
                        "oba.integrations.alertmanager.organization-id=")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
