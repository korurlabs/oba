package com.korur.oba.shrine.identity.dto;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.shrine.identity.enums.UserGender;
import com.korur.oba.shrine.identity.enums.UserPosition;
import com.korur.oba.shrine.identity.enums.UserPreferredLanguage;
import com.korur.oba.shrine.identity.enums.UserStatus;

@Getter
@Setter
@NoArgsConstructor
public class UserQueryRequest extends QueryRequest {

    @Schema(description = "User statuses")
    private List<UserStatus> userStatuses;

    @Schema(description = "User position filter")
    private UserPosition userPosition;

    @Schema(description = "User gender filter")
    private UserGender userGender;

    @Schema(description = "Preferred language filter")
    private UserPreferredLanguage userPreferredLanguage;

    @Schema(description = "Search keyword")
    private String search;
}
