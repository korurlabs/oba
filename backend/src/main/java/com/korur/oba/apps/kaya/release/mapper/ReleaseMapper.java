package com.korur.oba.apps.kaya.release.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.mapper.PackMapper;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveItemResponse;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveOverrideResponse;
import com.korur.oba.apps.kaya.release.dto.response.PackRequiredResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseDetailResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackEntryResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackResponse;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.enums.ItemLock;
import com.korur.oba.apps.kaya.release.enums.OverrideStatus;
import com.korur.oba.apps.kaya.release.helper.ComposedFile;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifestFile;

/**
 * Maps releases and pack flag changes to responses, a composition to the stored manifest entries, and stored entries to
 * the signed document. The signed document is built from the stored entries in both directions,
 * so a release signed at creation and the document delivery rebuilds later are the same bytes.
 */
@Mapper(componentModel = "spring", uses = PackMapper.class)
public interface ReleaseMapper {

    @Mapping(target = "fileCount", source = "manifest", qualifiedByName = "fileCount")
    ReleaseResponse toResponse(Release release);

    List<ReleaseResponse> toResponses(List<Release> releases);

    @Mapping(target = "fileCount", source = "manifest", qualifiedByName = "fileCount")
    @Mapping(target = "files", source = "manifest")
    ReleaseDetailResponse toDetailResponse(Release release);

    @Mapping(target = "repositoryId", source = "repositoryId")
    @Mapping(target = "packs", source = "packs")
    @Mapping(target = "release", source = "release")
    RepositoryPackResponse toPackResponse(UUID repositoryId, List<RepositoryPackEntryResponse> packs, Release release);

    @Mapping(target = "rootPath", source = "rootPath")
    RepositoryPackEntryResponse toPackEntry(Pack pack, String rootPath);

    @Mapping(target = "packId", source = "packId")
    @Mapping(target = "required", source = "required")
    @Mapping(target = "releaseCount", source = "releaseCount")
    PackRequiredResponse toPackRequiredResponse(UUID packId, boolean required, int releaseCount);

    @Mapping(target = "path", source = "path")
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "sourcePackId", source = "sourcePackId")
    @Mapping(target = "sourceVersionId", source = "sourceVersionId")
    @Mapping(target = "own", source = "own")
    @Mapping(target = "locked", source = "locked")
    @Mapping(target = "override", source = "override")
    EffectiveItemResponse toEffectiveItem(String path, EntityKind kind, UUID sourcePackId, UUID sourceVersionId, boolean own,
                                          ItemLock locked, EffectiveOverrideResponse override);

    @Mapping(target = "basePackId", source = "basePackId")
    @Mapping(target = "baseVersionId", source = "baseVersionId")
    @Mapping(target = "baseItemVersion", source = "baseItemVersion")
    @Mapping(target = "parentVersionId", source = "parentVersionId")
    @Mapping(target = "parentItemVersion", source = "parentItemVersion")
    @Mapping(target = "status", source = "status")
    EffectiveOverrideResponse toEffectiveOverride(UUID basePackId, UUID baseVersionId, Integer baseItemVersion,
                                                  UUID parentVersionId, Integer parentItemVersion, OverrideStatus status);

    @Mapping(target = "target", source = "symlinkTarget")
    ReleaseEntry toEntry(ComposedFile file);

    List<ReleaseEntry> toEntries(List<ComposedFile> files);

    @Mapping(target = "organization", source = "organization")
    @Mapping(target = "remoteUrl", source = "remoteUrl")
    @Mapping(target = "number", source = "number")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "files", source = "files")
    SignedManifest toSignedManifest(String organization, String remoteUrl, int number, String createdAt,
                                    List<ReleaseEntry> files);

    SignedManifestFile toSignedFile(ReleaseEntry entry);

    @Named("fileCount")
    default int fileCount(List<ReleaseEntry> manifest) {
        return manifest == null ? 0 : manifest.size();
    }
}
