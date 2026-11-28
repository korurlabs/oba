package com.korur.oba.apps.kaya.version.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import jakarta.persistence.EntityManager;

// 3. Java SDK
import java.time.Clock;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.pack.service.PackService;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.service.RenderService;
import com.korur.oba.apps.kaya.validation.service.ValidationQueryService;
import com.korur.oba.apps.kaya.version.dto.request.DraftCreateRequest;
import com.korur.oba.apps.kaya.version.helper.ExecutableChangeDetector;
import com.korur.oba.apps.kaya.version.mapper.ItemOverrideMapper;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.repository.ItemOverrideRepository;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.version.service.ItemOverrideQueryService;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Runs the web writes that start or propose a configuration version through the real
 * {@link AgentValidator}: an approved version releases hooks and settings to every machine of the
 * organization, so an agent neither drafts, restores nor submits one. A person's writes go through the
 * same validator in {@link VersionServiceImplUnitTest}.
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceImplUnitAgentTest {

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private VersionQueryService versionQueryService;
    @Mock
    private PackQueryService packQueryService;
    @Mock
    private PackService packService;
    @Mock
    private RenderService renderService;
    @Mock
    private ItemOverrideRepository itemOverrideRepository;
    @Mock
    private ItemOverrideQueryService itemOverrideQueryService;
    @Mock
    private EntityManager entityManager;

    private VersionServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final User agent = User.builder().id(UUID.randomUUID()).kind(UserKind.AGENT).build();

    @BeforeEach
    void setUp() {
        LoggingService loggingService = new LoggingService();
        service = new VersionServiceImpl(versionRepository, versionQueryService, packQueryService, packService,
                renderService, mock(ValidationQueryService.class), mock(ReleaseQueryService.class),
                mock(ExecutableChangeDetector.class), mock(VersionMapper.class), itemOverrideRepository,
                itemOverrideQueryService, mock(ItemOverrideMapper.class),
                new AgentValidator(mock(UserQueryService.class), loggingService), Clock.systemUTC(), loggingService,
                entityManager);
    }

    @Test
    void createDraft_agent_throwsForbiddenAndWritesNothing() {
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.createDraft(agent, organizationId, new DraftCreateRequest(UUID.randomUUID())));

        assertRefused(exception, "KAYA_CONFIG_DRAFT");
    }

    @Test
    void restore_agent_throwsForbiddenAndWritesNothing() {
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.restore(agent, organizationId, UUID.randomUUID()));

        assertRefused(exception, "KAYA_CONFIG_DRAFT");
    }

    @Test
    void submit_agentsOwnDraft_throwsForbiddenAndWritesNothing() {
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> service.submit(agent, organizationId, UUID.randomUUID(), "Allow the board CLI"));

        assertRefused(exception, "KAYA_CONFIG_PROPOSE");
    }

    private void assertRefused(ForbiddenException exception, String action) {
        assertEquals("AGT_SEC_002", exception.getErrorCode());
        assertEquals(action, exception.getErrorValues().get("action"));
        verifyNoInteractions(versionRepository, versionQueryService, packQueryService, packService, renderService,
                itemOverrideRepository, itemOverrideQueryService, entityManager);
    }
}
