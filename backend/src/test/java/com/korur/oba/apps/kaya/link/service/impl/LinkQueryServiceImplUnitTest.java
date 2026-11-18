package com.korur.oba.apps.kaya.link.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.convention.service.ConventionQueryService;
import com.korur.oba.apps.kaya.link.dto.request.LinkQueryRequest;
import com.korur.oba.apps.kaya.link.dto.response.LinkResponse;
import com.korur.oba.apps.kaya.link.enums.LinkStatus;
import com.korur.oba.apps.kaya.link.mapper.LinkMapperImpl;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.helper.ComposedFile;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.rule.service.RuleQueryService;
import com.korur.oba.apps.kaya.skill.service.SkillQueryService;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkQueryServiceImplUnitTest {

    private static final String RULE = ".agents/rules/core.md";
    private static final String DOC = "docs/private/conventions/backend/a.md";
    private static final String MISSING = "docs/private/conventions/backend/missing.md";
    private static final String RULE_TEXT = "See `" + DOC + "` and `" + MISSING + "`.\n";

    @Mock
    private VersionQueryService versionQueryService;
    @Mock
    private ReleaseQueryService releaseQueryService;
    @Mock
    private BlobQueryService blobQueryService;
    @Mock
    private RuleQueryService ruleQueryService;
    @Mock
    private ConventionQueryService conventionQueryService;
    @Mock
    private WorkflowQueryService workflowQueryService;
    @Mock
    private SkillQueryService skillQueryService;
    @Mock
    private LoggingService loggingService;

    private LinkQueryServiceImpl service;
    private UUID organizationId;
    private UUID repositoryId;

    @BeforeEach
    void setUp() {
        service = new LinkQueryServiceImpl(versionQueryService, releaseQueryService, blobQueryService, ruleQueryService,
                conventionQueryService, workflowQueryService, skillQueryService, new LinkMapperImpl(),
                loggingService);
        organizationId = UUID.randomUUID();
        repositoryId = UUID.randomUUID();
    }

    private static LinkQueryRequest request(UUID versionId, UUID repositoryId) {
        LinkQueryRequest request = new LinkQueryRequest();
        request.setVersionId(versionId);
        request.setRepositoryId(repositoryId);
        return request;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static List<LinkResponse> expectedLinks() {
        return List.of(new LinkResponse(RULE, DOC, 1, LinkStatus.FOUND), new LinkResponse(RULE, MISSING, 1, LinkStatus.BROKEN));
    }

    private Version version(VersionStatus status) {
        Version version = Version.builder().id(UUID.randomUUID()).organizationId(organizationId).packId(UUID.randomUUID())
                .status(status).build();
        when(versionQueryService.getEntityById(organizationId, version.getId())).thenReturn(version);
        return version;
    }

    @Test
    void getLinks_noScope_throwsValidationException() {
        assertThrows(ValidationException.class, () -> service.getLinks(organizationId, request(null, null)));
    }

    @Test
    void getLinks_versionOnly_marksTargetsTheVersionDelivers() {
        Version version = version(VersionStatus.APPROVED);
        when(versionQueryService.render(organizationId, version)).thenReturn(List.of(
                new RenderedFile(RULE, FileMode.FILE, null, bytes(RULE_TEXT), "r", UUID.randomUUID(), null),
                new RenderedFile(DOC, FileMode.FILE, null, bytes("# A\n"), "d", UUID.randomUUID(), null)));

        List<LinkResponse> links = service.getLinks(organizationId, request(version.getId(), null));

        assertEquals(expectedLinks(), links);
    }

    @Test
    void getLinks_repositoryWithoutRelease_returnsEmpty() {
        when(releaseQueryService.findLatest(organizationId, repositoryId)).thenReturn(Optional.empty());

        assertEquals(List.of(), service.getLinks(organizationId, request(null, repositoryId)));
    }

    @Test
    void getLinks_repositoryOnly_readsTheLatestReleaseFromItsBlobs() {
        UUID owner = UUID.randomUUID();
        Release release = Release.builder().repositoryId(repositoryId).manifest(List.of(
                new ReleaseEntry(RULE, "r", FileMode.FILE, null, 10, true, owner, UUID.randomUUID(), null, null, null),
                new ReleaseEntry(DOC, "d", FileMode.FILE, null, 4, false, owner, UUID.randomUUID(), null, null, null))).build();
        when(releaseQueryService.findLatest(organizationId, repositoryId)).thenReturn(Optional.of(release));
        when(blobQueryService.getContents(eq(organizationId), any()))
                .thenReturn(Map.of("r", bytes(RULE_TEXT), "d", bytes("# A\n")));

        List<LinkResponse> links = service.getLinks(organizationId, request(null, repositoryId));

        assertEquals(expectedLinks(), links);
    }

    @Test
    void getLinks_openVersionInRepository_composesItInPlaceOfItsOwner() {
        Version version = version(VersionStatus.DRAFT);
        when(releaseQueryService.getComposingRepositoryIds(organizationId, version)).thenReturn(List.of(repositoryId));
        Composition composition = new Composition(repositoryId, List.of(
                new ComposedFile(RULE, FileMode.FILE, "r", 10, null, true, version.getPackId(), version.getId(), null, null, null),
                new ComposedFile(DOC, FileMode.FILE, "d", 4, null, false, UUID.randomUUID(), UUID.randomUUID(), null, null, null)),
                List.of(), List.of(version), null, List.of(), List.of(), List.of(), Map.of("r", bytes(RULE_TEXT), "d", bytes("# A\n")));
        when(releaseQueryService.previewComposition(organizationId, repositoryId, Map.of(version.getPackId(), version.getId())))
                .thenReturn(composition);

        List<LinkResponse> links = service.getLinks(organizationId, request(version.getId(), repositoryId));

        assertEquals(expectedLinks(), links);
    }

    @Test
    void getLinks_approvedVersionInRepository_throwsValidationException() {
        Version version = version(VersionStatus.APPROVED);

        assertThrows(ValidationException.class, () -> service.getLinks(organizationId, request(version.getId(), repositoryId)));

        verify(releaseQueryService, never()).previewComposition(any(), any(), any());
    }

    @Test
    void getLinks_versionNotComposedByRepository_throwsValidationException() {
        Version version = version(VersionStatus.IN_REVIEW);
        when(releaseQueryService.getComposingRepositoryIds(organizationId, version)).thenReturn(List.of(UUID.randomUUID()));

        assertThrows(ValidationException.class, () -> service.getLinks(organizationId, request(version.getId(), repositoryId)));

        verify(releaseQueryService, never()).previewComposition(any(), any(), any());
    }
}
