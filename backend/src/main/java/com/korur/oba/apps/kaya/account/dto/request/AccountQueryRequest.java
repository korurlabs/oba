package com.korur.oba.apps.kaya.account.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Filters of the account list. The list is not paged, because an organization has a few accounts
 * per person; it is ordered by account email, provider organization name and id.
 */
@Schema(description = "Filters of the account list")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AccountQueryRequest {

    @Schema(description = "Only accounts linked to this user", nullable = true,
            example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
    private UUID userId;
}
