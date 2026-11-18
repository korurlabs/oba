package com.korur.oba.support;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 3. Java SDK
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.settings.service.SettingsService;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;
import com.korur.oba.shrine.organization.repository.OrganizationRepository;

/**
 * Base of every integration test: the whole application, started the way production starts it.
 * Its configuration is application.properties resolved from the environment, so the database,
 * the storage and the secrets are those of the stack the environment names, and Flyway migrates
 * that database on startup. Only the Keycloak decoder is replaced (see
 * {@link TestJwtDecoderConfiguration}), so a test never needs a live issuer. Spring caches the
 * context, so the application starts once per run for every class that adds nothing to it.
 *
 * The database is shared by every test and by anything else that uses it, and no test cleans it:
 * each test creates its own organization and users with random ids and reads only its own rows.
 * Tests commit real transactions, because the race tests and the scheduled jobs could not see
 * rows a per-test rollback would hide.
 *
 * Every subclass shares this one context, so whatever a single class needs that leaves the others
 * unaffected lives here instead of in a context of its own:
 * - a real server on a random port next to MockMvc, for the tests of streamed responses, whose async
 *   dispatch MockMvc does not run;
 * - a one-second session-stream heartbeat, so a test can see the periodic send;
 * - a {@link SettingsService} spy, which delegates to the real bean until a test stubs it and is
 *   reset after every test;
 * - signing keys generated for the run, the current one and the one it replaced, so the key rotation
 *   is tested without committed key material. Production also signs with a configured key.
 * Only a class that needs a setting the others cannot live with (a short stream timeout) declares
 * its own {@code @SpringBootTest} and so starts a second context.
 *
 * Run locally from projects/oba/backend: {@code docker compose up -d}, then
 * {@code kagi run --path /oba/api --env testing -- mvn verify}. Run {@code docker compose down -v}
 * first for an empty database. {@code mvn test} runs the unit tests only and needs neither.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"kaya.session.stream.heartbeat=PT1S"})
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfiguration.class)
@MockitoSpyBean(types = SettingsService.class)
public abstract class ObaIntegrationTest {

    protected static final String SIGNING_KEY_ID = "test-current";
    protected static final String PREVIOUS_SIGNING_KEY_ID = "test-previous";
    protected static final String PREVIOUS_SIGNING_KEY_VALID_TO = "2099-01-01T00:00:00Z";
    protected static final KeyPair SIGNING_KEY = generateSigningKey();
    protected static final KeyPair PREVIOUS_SIGNING_KEY = generateSigningKey();

    private static KeyPair generateSigningKey() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void signingKeys(DynamicPropertyRegistry registry) {
        registry.add("kaya.config.signing.private-key", () -> Base64.getEncoder().encodeToString(SIGNING_KEY.getPrivate().getEncoded()));
        registry.add("kaya.config.signing.key-id", () -> SIGNING_KEY_ID);
        registry.add("kaya.config.signing.previous-public-key", () -> SigningKeyProvider.rawPublicKey(PREVIOUS_SIGNING_KEY.getPublic()));
        registry.add("kaya.config.signing.previous-key-id", () -> PREVIOUS_SIGNING_KEY_ID);
        registry.add("kaya.config.signing.previous-valid-to", () -> PREVIOUS_SIGNING_KEY_VALID_TO);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected OrganizationRepository organizationRepository;
    @Autowired
    protected OrganizationMemberRepository organizationMemberRepository;

    protected static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** A domain no other test uses, valid for the domain pattern. */
    protected static String uniqueDomain() {
        return "t" + uniqueSuffix() + ".example";
    }

    /** An ACTIVE organization no other test uses. */
    protected Organization createOrganization() {
        return createOrganization(OrganizationStatus.ACTIVE);
    }

    protected Organization createOrganization(OrganizationStatus status) {
        return organizationRepository.saveAndFlush(Organization.builder()
                .name("Org " + uniqueSuffix())
                .status(status)
                .build());
    }

    /** An ACTIVE user with a random Keycloak id and email. */
    protected User createUser() {
        String suffix = uniqueSuffix();
        return createUser("kc-" + suffix, "user" + suffix + "@" + uniqueDomain());
    }

    /**
     * Gives the organization its Everyone group with a grant of every app, as the migration does for an
     * existing organization, so every member opens every app that the role allows.
     */
    protected void grantEveryAppToEveryone(Organization organization) {
        UUID groupId = jdbcTemplate.queryForObject("""
                INSERT INTO shrine.organization_groups (organization_id, name, type)
                VALUES (?, 'Everyone', 'EVERYONE'::shrine_organization_group_type_enum)
                RETURNING id
                """, UUID.class, organization.getId());
        jdbcTemplate.update("""
                INSERT INTO shrine.organization_app_grants (organization_id, app, group_id)
                SELECT ?, a.app, ?
                FROM (SELECT unnest(enum_range(NULL::shrine_app_grant_app_enum)) AS app) a
                """, organization.getId(), groupId);
    }

    protected User createUser(String keycloakId, String email) {
        return userRepository.saveAndFlush(User.builder()
                .keycloakId(keycloakId)
                .email(email)
                .status(UserStatus.ACTIVE)
                .build());
    }

    protected OrganizationMember createMember(
            Organization organization, User user, OrganizationMemberRole role, OrganizationMemberStatus status) {
        return organizationMemberRepository.saveAndFlush(OrganizationMember.builder()
                .organization(organization)
                .user(user)
                .role(role)
                .status(status)
                .build());
    }

    /** A user and an ACTIVE membership in one step; the user's email is on the given domain. */
    protected OrganizationMember createActiveMember(Organization organization, String domain, OrganizationMemberRole role) {
        String suffix = uniqueSuffix();
        User user = createUser("kc-" + suffix, "user" + suffix + "@" + domain);
        return createMember(organization, user, role, OrganizationMemberStatus.ACTIVE);
    }

    protected static RequestPostProcessor jwtOf(User user) {
        return jwtOf(user.getKeycloakId(), user.getEmail());
    }

    protected static RequestPostProcessor jwtOf(String keycloakId, String email) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt
                .subject(keycloakId)
                .claim("email", email)
                .claim("email_verified", true));
    }

    protected long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0L : result;
    }
}
