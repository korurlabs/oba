package com.korur.oba.apps.otak.icp.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.icp.constant.IcpConstant;
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Filters of the ICP list. ICPs are always ordered by name, so sortBy and sortDirection
 * are ignored.
 */
@Schema(description = "Paging and filters of the ICP list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class IcpQueryRequest extends QueryRequest {

    @Schema(description = "Part of the name, case ignored; absent lists every ICP", nullable = true,
            example = "textile")
    @NullOrNotBlank(message = IcpConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = IcpConstant.SEARCH_MAX_LENGTH, message = IcpConstant.SEARCH_SIZE_MESSAGE)
    private String search;
}
