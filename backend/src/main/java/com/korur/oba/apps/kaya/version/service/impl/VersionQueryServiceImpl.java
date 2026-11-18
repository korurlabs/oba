package com.korur.oba.apps.kaya.version.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.service.RenderQueryService;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;
import com.korur.oba.apps.kaya.version.constant.service.VersionServiceLogConstant;
import com.korur.oba.apps.kaya.version.dto.embedded.FileHistoryEntry;
import com.korur.oba.apps.kaya.version.dto.request.VersionQueryRequest;
import com.korur.oba.apps.kaya.version.dto.response.FileDiffResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionDetailResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionFileContentResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.FileChange;
import com.korur.oba.apps.kaya.version.enums.VersionOrder;
import com.korur.oba.apps.kaya.version.helper.UnifiedDiff;
import com.korur.oba.apps.kaya.version.mapper.VersionMapper;
import com.korur.oba.apps.kaya.version.repository.VersionRepository;
import com.korur.oba.apps.kaya.version.service.VersionQueryService;
import com.korur.oba.apps.kaya.version.specification.VersionSpecification;

/**
 * Reads versions and the files {@link RenderQueryService} renders for them, at the repository paths
 * their pack stores them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionQueryServiceImpl implements VersionQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(VersionQueryServiceImpl.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, VersionConstant.ATTRIBUTE_NUMBER);
    private static final Sort HISTORY_ORDER = Sort.by(Sort.Direction.DESC, VersionConstant.ATTRIBUTE_NUMBER)
            .and(Sort.by(Sort.Direction.DESC, VersionConstant.ATTRIBUTE_ID));
    private static final Sort RECENTLY_CHANGED = Sort.by(Sort.Direction.DESC, VersionConstant.ATTRIBUTE_UPDATED_AT)
            .and(Sort.by(Sort.Direction.DESC, VersionConstant.ATTRIBUTE_ID));

    private final VersionRepository versionRepository;
    private final RenderQueryService renderQueryService;
    private final PackQueryService packQueryService;
    private final VersionMapper versionMapper;
    private final LoggingService loggingService;

    @Override
    public Page<VersionResponse> getVersions(UUID organizationId, VersionQueryRequest request) {
        return getVersions(organizationId, request, VersionOrder.NEWEST_NUMBER);
    }

    @Override
    public Page<VersionResponse> getVersions(UUID organizationId, VersionQueryRequest request, VersionOrder order) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.LISTING_VERSIONS, organizationId, request);
        Sort sort = order == VersionOrder.RECENTLY_CHANGED ? RECENTLY_CHANGED : NEWEST_FIRST;
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), sort);
        return versionRepository.findAll(VersionSpecification.filterBy(organizationId, request), pageable)
                .map(versionMapper::toResponse);
    }

    @Override
    public VersionDetailResponse getVersion(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.READING_VERSION, versionId, organizationId);
        Version version = getEntityById(organizationId, versionId);
        return versionMapper.toDetailResponse(version, versionMapper.toFileResponses(render(organizationId, version)));
    }

    @Override
    public VersionFileContentResponse getFileContent(UUID organizationId, UUID versionId, String path) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.READING_FILE, path, versionId, organizationId);
        Version version = getEntityById(organizationId, versionId);
        RenderedFile file = render(organizationId, version).stream()
                .filter(rendered -> rendered.path().equals(path))
                .findFirst()
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DRAFT_FILE_NOT_FOUND, versionId, path);
                    return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                            Map.of(VersionConstant.FIELD_VERSION_ID, versionId.toString(), VersionConstant.FIELD_PATH, path));
                });
        if (file.content() == null) {
            return versionMapper.toFileContentResponse(versionId, file, null, null);
        }
        String text = decodeUtf8(file.content());
        return text != null
                ? versionMapper.toFileContentResponse(versionId, file, FileEncoding.UTF_8, text)
                : versionMapper.toFileContentResponse(versionId, file, FileEncoding.BASE64,
                        Base64.getEncoder().encodeToString(file.content()));
    }

    @Override
    public List<FileDiffResponse> getDiff(UUID organizationId, UUID versionId, UUID againstVersionId, String path) {
        Version version = getEntityById(organizationId, versionId);
        Version against = againstVersionId == null
                ? version.getBaseVersion()
                : getEntityById(organizationId, againstVersionId);
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.DIFFING, versionId,
                against == null ? null : against.getId(), path, organizationId);

        Map<String, RenderedFile> after = byPath(render(organizationId, version));
        Map<String, RenderedFile> before = byPath(render(organizationId, against));
        Set<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        if (path != null) {
            paths.retainAll(Set.of(path));
        }
        List<FileDiffResponse> diffs = new ArrayList<>();
        for (String changed : paths) {
            if (!sameFile(before.get(changed), after.get(changed))) {
                diffs.add(toDiff(changed, before.get(changed), after.get(changed)));
            }
        }
        return diffs;
    }

    @Override
    public Page<FileHistoryEntry> getFileHistory(User user, UUID organizationId, UUID versionId, String path,
                                                 QueryRequest page) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.READING_FILE_HISTORY,
                path, versionId, organizationId, user.getId());
        Version anchor = getEntityById(organizationId, versionId);
        UUID currentVersionId = packQueryService.getEntityById(organizationId, anchor.getPackId()).getCurrentVersionId();
        Pageable pageable = PageRequest.of(page.getPage() - 1, page.getAmount(), HISTORY_ORDER);
        Page<Version> versions = versionRepository.findAll(VersionSpecification.historyOf(organizationId,
                anchor.getPackId(), user.getId()), pageable);

        // The path alone, in every version of the page and its base, in one render.
        Set<UUID> renderedIds = new LinkedHashSet<>();
        for (Version version : versions.getContent()) {
            renderedIds.add(version.getId());
            if (version.getBaseVersion() != null) {
                renderedIds.add(version.getBaseVersion().getId());
            }
        }
        Map<UUID, RenderedFile> rendered = renderQueryService.renderPath(organizationId, renderedIds, path);
        Function<Version, RenderedFile> fileAt = version -> version == null ? null : rendered.get(version.getId());
        List<FileHistoryEntry> entries = new ArrayList<>();
        for (Version version : versions.getContent()) {
            RenderedFile before = fileAt.apply(version.getBaseVersion());
            RenderedFile after = fileAt.apply(version);
            if (sameFile(before, after)) {
                continue;
            }
            FileChange change = before == null ? FileChange.ADDED : after == null ? FileChange.DELETED : FileChange.MODIFIED;
            entries.add(versionMapper.toHistoryEntry(version, change, after == null ? null : after.sha256(),
                    version.getId().equals(currentVersionId)));
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.FILE_HISTORY_READ,
                entries.size(), versions.getNumberOfElements(), path, page.getPage(), anchor.getPackId());
        return new PageImpl<>(entries, pageable, versions.getTotalElements());
    }

    @Override
    public Version getEntityById(UUID organizationId, UUID versionId) {
        return versionRepository.findByIdAndOrganizationId(versionId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, VersionServiceLogConstant.VERSION_NOT_FOUND, versionId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(VersionConstant.FIELD_VERSION_ID, versionId.toString()));
        });
    }

    @Override
    public List<Version> getEntitiesByIds(UUID organizationId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Version> found = versionRepository.findByOrganizationIdAndIdIn(organizationId, versionIds).stream()
                .collect(Collectors.toMap(Version::getId, Function.identity()));
        return versionIds.stream().distinct().map(found::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<Version> getEntitiesOfPack(UUID organizationId, UUID packId) {
        return versionRepository.findByOrganizationIdAndPackIdOrderByNumberAsc(organizationId, packId);
    }

    @Override
    public List<RenderedFile> render(UUID organizationId, Version version) {
        if (version == null) {
            return List.of();
        }
        return renderQueryService.render(organizationId, version.getId());
    }

    private FileDiffResponse toDiff(String path, RenderedFile before, RenderedFile after) {
        FileChange change = before == null
                ? FileChange.ADDED
                : after == null ? FileChange.DELETED : FileChange.MODIFIED;
        boolean link = isLink(before) || isLink(after);
        String oldText = text(before);
        String newText = text(after);
        boolean binary = (hasContent(before) && oldText == null) || (hasContent(after) && newText == null);
        // A link has a target, not lines; the response carries both targets instead.
        String unifiedDiff = link || binary ? null : UnifiedDiff.diff(path, oldText, newText);
        return versionMapper.toDiffResponse(path, change, before, after, binary, unifiedDiff);
    }

    private static Map<String, RenderedFile> byPath(List<RenderedFile> files) {
        return files.stream().collect(Collectors.toMap(RenderedFile::path, Function.identity()));
    }

    private static boolean sameFile(RenderedFile before, RenderedFile after) {
        if (before == null || after == null) {
            return before == after;
        }
        return before.mode() == after.mode() && Objects.equals(before.sha256(), after.sha256())
                && Objects.equals(before.symlinkTarget(), after.symlinkTarget());
    }

    private static boolean isLink(RenderedFile file) {
        return file != null && file.mode() == FileMode.SYMLINK;
    }

    private static boolean hasContent(RenderedFile file) {
        return file != null && file.content() != null;
    }

    /** The file's text; null when it is absent, a link, or bytes that are not valid UTF-8. */
    private static String text(RenderedFile file) {
        return hasContent(file) ? decodeUtf8(file.content()) : null;
    }

    private static String decodeUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            // rule-10-no-op -- reason: binary content is a valid file; it is sent as BASE64 or marked binary instead.
            return null;
        }
    }
}
