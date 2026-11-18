package com.korur.oba.apps.kazan.board.dto.request;

// 1. Framework-level
import jakarta.validation.constraints.NotNull;

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.board.enums.BoardMemberSpecialtyType;

/**
 * Request DTO for updating a board member's specialties.
 *
 * @param specialties the list of specialties to assign (replaces existing)
 */
public record BoardMemberSpecialtyUpdateRequest(
        @NotNull List<BoardMemberSpecialtyType> specialties
) {
}
