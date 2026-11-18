package com.korur.oba.apps.kazan.label.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.label.repository.LabelRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class LabelValidatorUnitTest {

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private LabelValidator labelValidator;

    @Nested
    class EnsureLabelExistsBoardScoped {

        @Test
        void ensureLabelExists_labelBelongsToBoard_returnsLabel() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();

            Board board = Board.builder().id(boardId).build();
            Label label = Label.builder()
                    .name("Bug")
                    .board(board)
                    .build();
            label.setId(labelId);

            when(labelRepository.findByBoard_IdAndId(boardId, labelId)).thenReturn(Optional.of(label));

            // Act
            Label result = labelValidator.ensureLabelExists(boardId, labelId);

            // Assert
            assertNotNull(result);
            assertEquals(labelId, result.getId());
        }

        @Test
        void ensureLabelExists_labelBelongsToDifferentBoard_throwsNotFoundException() {
            // Arrange
            UUID boardId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();

            when(labelRepository.findByBoard_IdAndId(boardId, labelId)).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(
                    NotFoundException.class,
                    () -> labelValidator.ensureLabelExists(boardId, labelId));

            // Assert
            assertEquals(ExceptionDomainKazanError.NOT_LABEL_FOUND.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsureLabelInOrganization {

        @Test
        void labelOnBoardOfTheOrganization_returnsLabel() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();
            Label label = Label.builder().build();
            label.setId(labelId);
            when(labelRepository.findByIdAndBoard_OrganizationId(labelId, organizationId)).thenReturn(Optional.of(label));

            // Act
            Label result = labelValidator.ensureLabelInOrganization(organizationId, labelId);

            // Assert
            assertEquals(label, result);
        }

        @Test
        void labelOnBoardOfAnotherOrganization_throwsNotFoundException() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID labelId = UUID.randomUUID();
            when(labelRepository.findByIdAndBoard_OrganizationId(labelId, organizationId)).thenReturn(Optional.empty());

            // Act & Assert
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> labelValidator.ensureLabelInOrganization(organizationId, labelId));
            assertEquals(ExceptionDomainKazanError.NOT_LABEL_FOUND.getCode(), exception.getErrorCode());
        }
    }
}
