package com.korur.oba.apps.kaya.support;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.render.service.RenderService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the integration tests of packs, versions and releases. A test writes packs and
 * approved versions by JDBC the way a seed leaves them, stores their files through
 * {@link RenderService} so the kind rows are indexed as on a save, then drives the endpoints over
 * HTTP.
 */
public abstract class AbstractVersionIT extends AbstractTenantIT {

    protected static final String SETTINGS = """
            {"permissions":{"allow":["Bash(git status)"]},
             "hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}],
                      "SessionStart":[{"hooks":[{"type":"command","command":".agents/hooks/dispatch.sh"}]}]}}
            """;

    @Autowired
    protected RepositoryRepository repositoryRepository;
    @Autowired
    protected RenderService renderService;
    @Autowired
    protected RenderQueryService renderQueryService;

    protected Tenant tenant;
    protected RequestPostProcessor owner;
    protected RequestPostProcessor admin;
    protected RequestPostProcessor member;
    protected UUID proposer;

    protected void initTenant() {
        tenant = newTenant();
        owner = jwtOf(tenant.owner().getUser());
        admin = jwtOf(tenant.admin().getUser());
        member = jwtOf(tenant.member().getUser());
        proposer = tenant.owner().getUser().getId();
    }

    protected Repository saveRepository(Organization organization) {
        Repository repository = RepositoryMocks.repository(organization.getId(), "github.com/o" + uniqueSuffix() + "/r",
                RepositoryStatus.ACTIVE);
        repository.setId(null);
        return repositoryRepository.saveAndFlush(repository);
    }

    /** A pack of the organization without versions. */
    protected UUID insertPack(Organization organization, String name) {
        return jdbcTemplate.queryForObject("INSERT INTO kaya.packs (organization_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, organization.getId(), name);
    }

    /** A pack without versions that the repository composes after the packs it composes already. */
    protected UUID insertRepositoryPack(Organization organization, UUID repositoryId, String name) {
        UUID pack = insertPack(organization, name);
        jdbcTemplate.update("""
                INSERT INTO kaya.repository_packs (repository_id, pack_id, position)
                SELECT ?, ?, COALESCE(MAX(position) + 1, 0) FROM kaya.repository_packs WHERE repository_id = ?
                """, repositoryId, pack, repositoryId);
        return pack;
    }

    /** A child pack of the given parent without versions. */
    protected UUID insertChildPack(Organization organization, String name, UUID parentPackId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO kaya.packs (organization_id, name, parent_pack_id) VALUES (?, ?, ?) RETURNING id",
                UUID.class, organization.getId(), name, parentPackId);
    }

    /**
     * An APPROVED version of a pack made current, the way a seed leaves it: an earlier APPROVED
     * version of the pack becomes SUPERSEDED.
     */
    protected UUID approvedVersion(UUID packId, int number) {
        UUID organizationId = jdbcTemplate.queryForObject("SELECT organization_id FROM kaya.packs WHERE id = ?",
                UUID.class, packId);
        jdbcTemplate.update("UPDATE kaya.versions SET status = 'SUPERSEDED'::version_version_status_enum WHERE "
                + "pack_id = ? AND status = 'APPROVED'::version_version_status_enum", packId);
        UUID versionId = jdbcTemplate.queryForObject("INSERT INTO kaya.versions (organization_id, pack_id"
                + ", number, status, proposed_by, source, reason) VALUES (?, ?, ?, 'APPROVED'::version_version_status_enum, ?, "
                + "'SEED'::version_version_source_enum, ?) RETURNING id",
                UUID.class, organizationId, packId, number, proposer, "Seed of version " + number);
        jdbcTemplate.update("UPDATE kaya.packs SET current_version_id = ? WHERE id = ?", versionId, packId);
        return versionId;
    }

    /** Stores one file into a version through the render domain, as a draft save does. */
    protected void insertFile(Organization organization, UUID versionId, String path, String content) {
        insertFile(organization, versionId, path, FileMode.FILE, content);
    }

    protected void insertFile(Organization organization, UUID versionId, String path, FileMode mode, String content) {
        renderService.parseOne(organization.getId(), versionId,
                SourceFile.of(path, mode, content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Stores one file the way a seed imports it, so a file over the size or line limits of a save
     * can stand in a version as a repository may already hold it; the version's other files are kept.
     */
    protected void insertSeededFile(Organization organization, UUID versionId, String path, String content) {
        List<SourceFile> files = new ArrayList<>();
        for (RenderedFile file : filesOf(versionId)) {
            if (!file.path().equals(path)) {
                files.add(file.toSource());
            }
        }
        files.add(SourceFile.of(path, FileMode.FILE, content.getBytes(StandardCharsets.UTF_8)));
        renderService.parseImported(organization.getId(), versionId, files);
    }

    /** Stores one regular file with raw bytes, for content that is not UTF-8 text. */
    protected void insertBytes(Organization organization, UUID versionId, String path, byte[] content) {
        renderService.parseOne(organization.getId(), versionId,
                SourceFile.of(path, FileMode.FILE, content));
    }

    /** Copies every file of one version into another of the same owner, leaving out the given paths. */
    protected void copyFiles(UUID fromVersionId, UUID toVersionId, String... leftOut) {
        List<String> skipped = List.of(leftOut);
        List<SourceFile> files = new ArrayList<>();
        for (RenderedFile file : filesOf(fromVersionId)) {
            if (!skipped.contains(file.path())) {
                files.add(file.toSource());
            }
        }
        UUID organizationId = jdbcTemplate.queryForObject("SELECT organization_id FROM kaya.versions WHERE id = ?",
                UUID.class, toVersionId);
        renderService.parseImported(organizationId, toVersionId, files);
    }

    /** Inserts a DRAFT version of a pack directly, without the draft endpoint. */
    protected UUID insertDraft(UUID packId, int number, UUID proposedBy) {
        UUID organizationId = jdbcTemplate.queryForObject("SELECT organization_id FROM kaya.packs WHERE id = ?",
                UUID.class, packId);
        return jdbcTemplate.queryForObject("INSERT INTO kaya.versions (organization_id, pack_id"
                + ", number, status, proposed_by, source) VALUES (?, ?, ?, 'DRAFT'::version_version_status_enum, ?, "
                + "'MANUAL'::version_version_source_enum) RETURNING id", UUID.class, organizationId, packId, number, proposedBy);
    }

    /** Stores the given files (path, content pairs) into a version in one parse. */
    protected void insertFiles(Organization organization, UUID versionId, String... pathsAndContents) {
        List<SourceFile> files = new ArrayList<>();
        for (int index = 0; index + 1 < pathsAndContents.length; index += 2) {
            files.add(SourceFile.of(pathsAndContents[index], FileMode.FILE,
                    pathsAndContents[index + 1].getBytes(StandardCharsets.UTF_8)));
        }
        renderService.parse(organization.getId(), versionId, files);
    }

    /** A pack with one approved version holding the given files (path, content pairs). */
    protected UUID packWith(String name, String... pathsAndContents) {
        UUID pack = insertPack(tenant.organization(), name);
        insertFiles(tenant.organization(), approvedVersion(pack, 1), pathsAndContents);
        return pack;
    }

    /**
     * The repository's own pack: a pack with one approved version holding the given files,
     * composed by the repository after the packs it composes already.
     */
    protected UUID repositoryPackWith(Repository repository, String... pathsAndContents) {
        UUID pack = packWith("repo-" + uniqueSuffix(), pathsAndContents);
        composePacks(repository, pack);
        return pack;
    }

    /** Appends packs to the pack order of a repository without releasing it. */
    protected void composePacks(Repository repository, UUID... packIds) {
        for (UUID packId : packIds) {
            jdbcTemplate.update("""
                    INSERT INTO kaya.repository_packs (repository_id, pack_id, position)
                    SELECT ?, ?, COALESCE(MAX(position) + 1, 0) FROM kaya.repository_packs WHERE repository_id = ?
                    """, repository.getId(), packId, repository.getId());
        }
    }

    protected UUID currentVersionOf(UUID packId) {
        return jdbcTemplate.queryForObject("SELECT current_version_id FROM kaya.packs WHERE id = ?", UUID.class, packId);
    }

    /** The rendered files of a version, path-sorted, as a release or the editor sees them. */
    protected List<RenderedFile> filesOf(UUID versionId) {
        UUID organizationId = jdbcTemplate.queryForObject("SELECT organization_id FROM kaya.versions WHERE id = ?",
                UUID.class, versionId);
        return renderQueryService.render(organizationId, versionId);
    }

    /** The UTF-8 text of one file of a version; null when the version has no such path. */
    protected String contentOf(UUID versionId, String path) {
        return filesOf(versionId).stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .map(file -> file.content() == null ? null : new String(file.content(), StandardCharsets.UTF_8))
                .orElse(null);
    }

    /** The body of POST /drafts for a pack. */
    protected String draftBody(UUID packId) {
        return "{\"packId\":\"" + packId + "\"}";
    }

    /** Starts a draft of the owner's current version and returns its id. */
    protected UUID startDraft(UUID ownerId, RequestPostProcessor jwt) throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post("/kaya/drafts"), jwt, draftBody(ownerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result, "$.data.id"));
    }


    protected static String packIds(UUID... ids) {
        StringBuilder body = new StringBuilder("{\"packs\":[");
        for (int index = 0; index < ids.length; index++) {
            body.append(index == 0 ? "" : ",").append("{\"packId\":\"").append(ids[index]).append("\"}");
        }
        return body.append("]}").toString();
    }

    /** A pack list body that mounts the pack at the folder; a null folder composes it at the repository root. */
    protected static String mountBody(UUID rootPack, UUID mountedPack, String rootPath) {
        String folder = rootPath == null ? "null" : "\"" + rootPath + "\"";
        return "{\"packs\":[{\"packId\":\"" + rootPack + "\"},{\"packId\":\"" + mountedPack + "\",\"rootPath\":" + folder + "}]}";
    }

    protected static String packsPath(Repository repository) {
        return "/kaya/repositories/" + repository.getId() + "/packs";
    }

    protected static List<String> list(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    /** Verifies an Ed25519 signature with a raw 32-byte public key, the way the CLI does, without any Kaya class. */
    protected static boolean verifyWithRawKey(String rawPublicKeyBase64, String canonical, String signatureBase64)
            throws Exception {
        byte[] raw = Base64.getDecoder().decode(rawPublicKeyBase64);
        assertEquals(32, raw.length, "the published key is the raw 32-byte Ed25519 key");
        byte[] encoded = HexFormat.of().parseHex("302a300506032b6570032100" + HexFormat.of().formatHex(raw));
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        try {
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (SignatureException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Rebuilds the signed document from the API's release detail alone: organization, the
     * repository's remote URL, number, creation time and the delivered paths, in the documented
     * key order.
     */
    protected static String canonicalFromApi(String organizationId, String remoteUrl, MvcResult detail) throws Exception {
        String body = detail.getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder json = new StringBuilder("{\"organization\":").append(mapper.writeValueAsString(organizationId))
                .append(",\"remoteUrl\":").append(mapper.writeValueAsString(remoteUrl))
                .append(",\"number\":").append((Integer) JsonPath.read(body, "$.data.number"))
                .append(",\"createdAt\":").append(mapper.writeValueAsString(DateTimeFormatter.ISO_INSTANT.format(
                        Instant.parse(JsonPath.<String>read(body, "$.data.createdAt")).truncatedTo(ChronoUnit.MILLIS))))
                .append(",\"files\":[");
        List<Map<String, Object>> files = JsonPath.read(body, "$.data.files");
        for (int index = 0; index < files.size(); index++) {
            Map<String, Object> file = files.get(index);
            json.append(index == 0 ? "" : ",").append("{\"path\":").append(mapper.writeValueAsString(file.get("path")));
            if (file.get("sha256") != null) {
                json.append(",\"sha256\":").append(mapper.writeValueAsString(file.get("sha256")));
            }
            json.append(",\"mode\":").append(mapper.writeValueAsString(file.get("mode")));
            if (file.get("target") != null) {
                json.append(",\"target\":").append(mapper.writeValueAsString(file.get("target")));
            }
            json.append(",\"alwaysOn\":").append(file.get("alwaysOn")).append('}');
        }
        return json.append("]}").toString();
    }
}
