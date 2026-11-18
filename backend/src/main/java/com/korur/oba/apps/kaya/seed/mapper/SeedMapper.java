package com.korur.oba.apps.kaya.seed.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedDrift;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedNotImported;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedRefusedFile;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReleaseReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedSetReport;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedWarning;
import com.korur.oba.apps.kaya.seed.dto.response.SeedRunResponse;
import com.korur.oba.apps.kaya.seed.entity.SeedRun;
import com.korur.oba.apps.kaya.seed.enums.SeedSetKind;
import com.korur.oba.apps.kaya.seed.enums.SeedSetOutcome;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;

/**
 * Maps seed runs to their responses, and the parts of a run's outcome to its report.
 */
@Mapper(componentModel = "spring")
public interface SeedMapper {

    SeedRunResponse toResponse(SeedRun run);

    @Mapping(target = "fileCount", source = "manifest", qualifiedByName = "manifestSize")
    SeedReleaseReport toReleaseReport(Release release);

    @Mapping(target = "setKind", source = "setKind")
    @Mapping(target = "setName", source = "setName")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "errorCode", source = "errorCode")
    @Mapping(target = "reason", source = "reason")
    @Mapping(target = "violations", source = "violations")
    SeedRefusedFile toRefusedFile(SeedSetKind setKind, String setName, String path, String errorCode, String reason,
                                  List<Violation> violations);

    @Mapping(target = "setKind", source = "setKind")
    @Mapping(target = "setName", source = "setName")
    @Mapping(target = "violation", source = "violation")
    SeedWarning toWarning(SeedSetKind setKind, String setName, Violation violation);

    @Mapping(target = "path", source = "path")
    @Mapping(target = "reason", source = "reason")
    SeedNotImported toNotImported(String path, String reason);

    @Mapping(target = "packId", source = "packId")
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "outcome", source = "outcome")
    @Mapping(target = "versionId", source = "versionId")
    @Mapping(target = "versionNumber", source = "versionNumber")
    @Mapping(target = "fileCount", source = "fileCount")
    @Mapping(target = "refusedCount", source = "refusedCount")
    @Mapping(target = "executableChange", source = "executableChange")
    SeedSetReport toSetReport(UUID packId, SeedSetKind kind, String name,
                              SeedSetOutcome outcome, UUID versionId, Integer versionNumber, int fileCount, int refusedCount,
                              boolean executableChange);

    @Mapping(target = "applied", source = "applied")
    @Mapping(target = "entities", source = "entities")
    @Mapping(target = "sets", source = "sets")
    @Mapping(target = "releases", source = "releases")
    @Mapping(target = "refused", source = "refused")
    @Mapping(target = "violations", source = "violations")
    @Mapping(target = "warnings", source = "warnings")
    @Mapping(target = "notImported", source = "notImported")
    @Mapping(target = "drift", source = "drift")
    SeedReport toReport(boolean applied, Map<EntityKind, Integer> entities, List<SeedSetReport> sets,
                        List<SeedReleaseReport> releases, List<SeedRefusedFile> refused,
                        List<Violation> violations, List<SeedWarning> warnings,
                        List<SeedNotImported> notImported,
                        List<SeedDrift> drift);

    @Named("manifestSize")
    default int manifestSize(List<ReleaseEntry> manifest) {
        return manifest == null ? 0 : manifest.size();
    }
}
