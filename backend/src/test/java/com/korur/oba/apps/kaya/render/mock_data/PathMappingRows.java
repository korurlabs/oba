package com.korur.oba.apps.kaya.render.mock_data;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.enums.FileKind;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPath;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.helper.SkillFolderParser;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

/**
 * Finds the path mapping row of a delivered path the way delivery reads it, through
 * {@link DeliveryPaths#classify} and {@link SkillFolderParser#kindOf}, so a test can hold the rows
 * against the path rules.
 */
public final class PathMappingRows {

    private PathMappingRows() {
    }

    /** The row of a path, or empty when no kind is delivered there. */
    public static Optional<PathMapping> rowOf(String path, FileMode mode, String mountRootPath) {
        return DeliveryPaths.classify(path, mountRootPath, mode == FileMode.SYMLINK).map(delivery -> DeliveryPaths.mappingOf(
                delivery.kind(), partOf(delivery, mode), delivery.placement(), delivery.toolLink()));
    }

    private static SkillPartKind partOf(DeliveryPath delivery, FileMode mode) {
        if (delivery.kind() != FileKind.SKILL || delivery.relativePath().equals(RenderConstant.SKILL_FILE)) {
            return null;
        }
        return SkillFolderParser.kindOf(delivery.relativePath(), mode);
    }
}
