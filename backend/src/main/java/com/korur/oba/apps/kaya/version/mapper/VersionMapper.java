package com.korur.oba.apps.kaya.version.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.enums.FileEncoding;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.version.dto.embedded.RebaseConflict;
import com.korur.oba.apps.kaya.version.dto.embedded.DeliveredFile;
import com.korur.oba.apps.kaya.version.dto.embedded.FileHistoryEntry;
import com.korur.oba.apps.kaya.version.dto.response.DraftFileSaveResponse;
import com.korur.oba.apps.kaya.version.dto.response.FileDiffResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionDetailResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionFileContentResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionFileResponse;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.FileChange;

/**
 * Maps a {@link Version} and its {@link RenderedFile}s to responses. The encoded content of a file
 * and the text diff come from the caller, which decides how bytes are sent.
 */
@Mapper(componentModel = "spring")
public interface VersionMapper {

    @Mapping(target = "baseVersionId", source = "baseVersion.id")
    VersionResponse toResponse(Version version);

    @Mapping(target = "id", source = "version.id")
    @Mapping(target = "packId", source = "version.packId")
    @Mapping(target = "number", source = "version.number")
    @Mapping(target = "status", source = "version.status")
    @Mapping(target = "baseVersionId", source = "version.baseVersion.id")
    @Mapping(target = "proposedBy", source = "version.proposedBy")
    @Mapping(target = "source", source = "version.source")
    @Mapping(target = "reason", source = "version.reason")
    @Mapping(target = "executableChange", source = "version.executableChange")
    @Mapping(target = "manifestSha256", source = "version.manifestSha256")
    @Mapping(target = "submittedAt", source = "version.submittedAt")
    @Mapping(target = "decidedAt", source = "version.decidedAt")
    @Mapping(target = "createdAt", source = "version.createdAt")
    @Mapping(target = "updatedAt", source = "version.updatedAt")
    @Mapping(target = "files", source = "files")
    VersionDetailResponse toDetailResponse(Version version, List<VersionFileResponse> files);

    @Mapping(target = "size", source = "content", qualifiedByName = "contentSize")
    VersionFileResponse toFileResponse(RenderedFile file);

    List<VersionFileResponse> toFileResponses(List<RenderedFile> files);

    DeliveredFile toDeliveredFile(RenderedFile file);

    @Mapping(target = "path", source = "path")
    @Mapping(target = "kind", source = "entityKind")
    @Mapping(target = "entityId", source = "entityId")
    @Mapping(target = "sha256", source = "sha256")
    @Mapping(target = "sizeBytes", source = "content", qualifiedByName = "contentSize")
    @Mapping(target = "executable", source = "mode", qualifiedByName = "executable")
    @Mapping(target = "overridesPath", source = "overridesPath")
    DraftFileSaveResponse toSaveResponse(RenderedFile file);

    /**
     * Whether a file carries the executable bit.
     *
     * @param mode the {@link FileMode} of the file
     * @return true for {@link FileMode#EXECUTABLE}
     */
    @Named("executable")
    default boolean executable(FileMode mode) {
        return mode == FileMode.EXECUTABLE;
    }

    /**
     * The size of a file's content in bytes.
     *
     * @param content the bytes; null for a link
     * @return the size, or null for a link
     */
    @Named("contentSize")
    default Integer contentSize(byte[] content) {
        return content == null ? null : content.length;
    }

    @Mapping(target = "versionId", source = "versionId")
    @Mapping(target = "path", source = "file.path")
    @Mapping(target = "mode", source = "file.mode")
    @Mapping(target = "sha256", source = "file.sha256")
    @Mapping(target = "size", source = "file.content", qualifiedByName = "contentSize")
    @Mapping(target = "symlinkTarget", source = "file.symlinkTarget")
    @Mapping(target = "overridesPath", source = "file.overridesPath")
    @Mapping(target = "encoding", source = "encoding")
    @Mapping(target = "content", source = "content")
    VersionFileContentResponse toFileContentResponse(UUID versionId, RenderedFile file, FileEncoding encoding, String content);

    @Mapping(target = "path", source = "path")
    @Mapping(target = "change", source = "change")
    @Mapping(target = "oldMode", source = "before.mode")
    @Mapping(target = "newMode", source = "after.mode")
    @Mapping(target = "oldSha256", source = "before.sha256")
    @Mapping(target = "newSha256", source = "after.sha256")
    @Mapping(target = "oldSymlinkTarget", source = "before.symlinkTarget")
    @Mapping(target = "newSymlinkTarget", source = "after.symlinkTarget")
    @Mapping(target = "binary", source = "binary")
    @Mapping(target = "unifiedDiff", source = "unifiedDiff")
    FileDiffResponse toDiffResponse(String path, FileChange change, RenderedFile before, RenderedFile after,
                                    boolean binary, String unifiedDiff);

    @Mapping(target = "versionId", source = "version.id")
    @Mapping(target = "number", source = "version.number")
    @Mapping(target = "status", source = "version.status")
    @Mapping(target = "source", source = "version.source")
    @Mapping(target = "change", source = "change")
    @Mapping(target = "sha256", source = "sha256")
    @Mapping(target = "current", source = "current")
    @Mapping(target = "proposedBy", source = "version.proposedBy")
    @Mapping(target = "reason", source = "version.reason")
    @Mapping(target = "submittedAt", source = "version.submittedAt")
    @Mapping(target = "decidedAt", source = "version.decidedAt")
    FileHistoryEntry toHistoryEntry(Version version, FileChange change, String sha256, boolean current);

    RebaseConflict toConflict(String path, FileChange draftChange, FileChange currentChange);
}
