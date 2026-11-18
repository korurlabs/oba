package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillFolder;
import com.korur.oba.apps.kaya.render.dto.embedded.SkillPartFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.skill.enums.SkillPartKind;

import static com.korur.oba.apps.kaya.render.mock_data.RenderStore.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillFolderParserUnitTest {

    @Test
    void kindOf_eachFolderAndSuffix_returnsItsPartKind() {
        assertEquals(SkillPartKind.SCRIPT, SkillFolderParser.kindOf("scripts/run.py", FileMode.FILE));
        assertEquals(SkillPartKind.SCRIPT, SkillFolderParser.kindOf("tools/run.sh", FileMode.EXECUTABLE));
        assertEquals(SkillPartKind.SCRIPT, SkillFolderParser.kindOf("rules/check.md", FileMode.EXECUTABLE),
                "the executable bit wins over the folder");
        assertEquals(SkillPartKind.WORKFLOW, SkillFolderParser.kindOf("workflows/close.md", FileMode.FILE));
        assertEquals(SkillPartKind.CONVENTION, SkillFolderParser.kindOf("rules/forms.md", FileMode.FILE));
        assertEquals(SkillPartKind.CONVENTION, SkillFolderParser.kindOf("conventions/naming.md", FileMode.FILE));
        assertEquals(SkillPartKind.TOOL_PARAMETER, SkillFolderParser.kindOf("evals/evals.json", FileMode.FILE));
        assertEquals(SkillPartKind.TOOL_PARAMETER, SkillFolderParser.kindOf("agents/openai.yml", FileMode.FILE));
        assertEquals(SkillPartKind.REFERENCE, SkillFolderParser.kindOf("assets/logo.png", FileMode.FILE));
        assertEquals(SkillPartKind.REFERENCE, SkillFolderParser.kindOf("cli.md", FileMode.FILE));
    }

    @Test
    void parse_folderWithParts_readsDescriptionTitlesAndMediaTypes() {
        // Arrange
        List<SourceFile> files = List.of(
                SourceFile.of(".agents/skills/demo/SKILL.md", FileMode.FILE, utf8("---\nname: demo\ndescription: Demo skill\n---\nBody\n")),
                SourceFile.of(".agents/skills/demo/workflows/close.md", FileMode.FILE, utf8("# Close it\n")),
                SourceFile.of(".agents/skills/demo/assets/logo.png", FileMode.FILE, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));

        // Act
        SkillFolder folder = SkillFolderParser.parse("demo", files);

        // Assert
        assertEquals("Demo skill", folder.description());
        assertEquals(2, folder.parts().size());
        SkillPartFile logo = folder.parts().get(0);
        assertEquals("assets/logo.png", logo.path());
        assertEquals("image/png", logo.mediaType());
        SkillPartFile workflow = folder.parts().get(1);
        assertEquals(SkillPartKind.WORKFLOW, workflow.kind());
        assertEquals("Close it", workflow.title());
    }

    @Test
    void parse_descriptionOf4000Characters_isKept() {
        // Arrange
        String description = "d".repeat(4000);
        List<SourceFile> files = List.of(SourceFile.of(".agents/skills/demo/SKILL.md", FileMode.FILE,
                utf8("---\nname: demo\ndescription: " + description + "\n---\nBody\n")));

        // Act
        SkillFolder folder = SkillFolderParser.parse("demo", files);

        // Assert
        assertEquals(description, folder.description());
    }

    @Test
    void parse_descriptionOf4001Characters_throwsInvalidFile() {
        // Arrange
        List<SourceFile> files = List.of(SourceFile.of(".agents/skills/demo/SKILL.md", FileMode.FILE,
                utf8("---\nname: demo\ndescription: " + "d".repeat(4001) + "\n---\nBody\n")));

        // Act
        ValidationException exception = assertThrows(ValidationException.class, () -> SkillFolderParser.parse("demo", files));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
    }

    @Test
    void parse_folderWithoutSkillFile_throwsInvalidFile() {
        // Arrange
        List<SourceFile> files = List.of(SourceFile.of(".agents/skills/demo/cli.md", FileMode.FILE, utf8("# CLI\n")));

        // Act
        ValidationException exception = assertThrows(ValidationException.class, () -> SkillFolderParser.parse("demo", files));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
    }

    @Test
    void parse_invalidToolParameterJson_throwsInvalidFile() {
        // Arrange
        List<SourceFile> files = List.of(
                SourceFile.of(".agents/skills/demo/SKILL.md", FileMode.FILE, utf8("---\nname: demo\n---\n")),
                SourceFile.of(".agents/skills/demo/evals/evals.json", FileMode.FILE, utf8("{ not json")));

        // Act
        ValidationException exception = assertThrows(ValidationException.class, () -> SkillFolderParser.parse("demo", files));

        // Assert
        assertEquals(ExceptionDomainKayaError.RENDER_INVALID_FILE.getCode(), exception.getErrorCode());
    }
}
