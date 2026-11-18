package com.korur.oba.apps.kaya.delivery.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryBlob;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryCurrent;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryCurrentResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryProposalResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryReleaseResponse;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.version.dto.embedded.DeliveredFile;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;

/**
 * Maps releases and blobs to what the CLI receives. The release may be null in the current
 * response, while a repository has none yet.
 */
@Mapper(componentModel = "spring")
public interface DeliveryMapper {

    @Mapping(target = "repositoryId", source = "repositoryId")
    @Mapping(target = "releaseId", source = "release.id")
    @Mapping(target = "number", source = "release.number")
    @Mapping(target = "manifestSha256", source = "release.manifestSha256")
    DeliveryCurrentResponse toCurrentResponse(UUID repositoryId, Release release);

    DeliveryCurrent toCurrent(DeliveryCurrentResponse response, String etag);

    @Mapping(target = "releaseId", source = "release.id")
    @Mapping(target = "repositoryId", source = "release.repositoryId")
    @Mapping(target = "number", source = "release.number")
    @Mapping(target = "manifest", source = "signedManifest")
    @Mapping(target = "manifestSha256", source = "release.manifestSha256")
    @Mapping(target = "signature", source = "release.signature")
    @Mapping(target = "keyId", source = "release.keyId")
    DeliveryReleaseResponse toReleaseResponse(Release release, String signedManifest);

    DeliveryBlob toBlob(Blob blob);

    @Mapping(target = "proposalId", source = "version.id")
    @Mapping(target = "number", source = "version.number")
    @Mapping(target = "status", source = "version.status")
    @Mapping(target = "packId", source = "version.packId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "baseVersionId", source = "version.baseVersionId")
    @Mapping(target = "releaseNumber", source = "releaseNumber")
    @Mapping(target = "executableChange", source = "version.executableChange")
    @Mapping(target = "reviewUrl", source = "reviewUrl")
    DeliveryProposalResponse toProposalResponse(VersionResponse version, String name, Integer releaseNumber,
                                                String reviewUrl);

    @Mapping(target = "symlinkTarget", source = "target")
    DeliveredFile toDeliveredFile(ReleaseEntry entry);

    /**
     * A proposed file at the path its pack stores it at, which differs from the delivered path only
     * for a mounted pack's AGENTS.md.
     */
    @Mapping(target = "path", source = "storedPath")
    ProposalFileRequest toStoredFile(ProposalFileRequest file, String storedPath);
}
