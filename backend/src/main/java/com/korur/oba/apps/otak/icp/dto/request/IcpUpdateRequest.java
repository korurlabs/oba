package com.korur.oba.apps.otak.icp.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.icp.constant.IcpConstant;
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.enums.TargetPersonRole;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload used to replace every field of an ICP, the body of
 * {@link com.korur.oba.apps.otak.icp.controller.IcpController#updateIcp}. The ICP is read
 * within the organization of the checked request header, so an ICP of another organization
 * answers not found,
 * {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_ICP_FOUND}
 * (OTK_NOT_004, 404).
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001): the
 * name and the industry are required; optional fields take null for "none", not an empty
 * string, and {@link com.korur.oba.dojo.annotation.NullOrNotBlank} refuses a blank
 * description; texts are capped at their column length. A null bound or filter means
 * "any", not "unknown".
 *
 * A full update (PUT): every field is written, and null clears an optional one, so a
 * client sends the whole ICP, not only what changed; there is no "null keeps the stored
 * value", which would make a cleared field and an omitted one look the same.
 *
 * Rules that need two fields or the stored ICPs are checked in
 * {@link com.korur.oba.apps.otak.icp.service.impl.IcpServiceImpl#update}:
 *
 * - {@link com.korur.oba.apps.otak.icp.validator.IcpValidator#ensureSizeRange} refuses a
 *   minimum company size above the maximum,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ICP_SIZE_RANGE}
 *   (OTK_VAL_001, 422);
 * - {@link com.korur.oba.apps.otak.icp.validator.IcpValidator#ensureNameFree} refuses a name
 *   another ICP of the organization already has, case ignored,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_ICP_NAME_TAKEN}
 *   (OTK_STA_005, 409), so an ICP can be picked by its name without ambiguity.
 *
 * The request is mapped with {@link com.korur.oba.apps.otak.icp.mapper.IcpMapper#updateEntity}.
 * Sent by the Otak web app and by the oba CLI (oba otak icp).
 *
 * @param name             the ICP name, unique within the organization with case ignored;
 *                         required, stored trimmed
 * @param industry         the industry the ICP targets; required
 * @param country          the country the ICP targets, or null for any country
 * @param companySizeMin   the smallest head-count band, or null for no lower bound
 * @param companySizeMax   the largest head-count band, or null for no upper bound
 * @param targetPersonRole the role of the person to approach, or null for any
 * @param description      the free description, or null
 * @see IcpCreateRequest
 */
@Schema(description = "ICP update request; replaces every field")
public record IcpUpdateRequest(

        @Schema(description = "Name of the ICP, unique within the organization with case ignored",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Dutch textile SMEs")
        @NotBlank(message = IcpConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = IcpConstant.NAME_MAX_LENGTH, message = IcpConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "Industry the ICP targets", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "MANUFACTURING")
        @NotNull(message = IcpConstant.INDUSTRY_REQUIRED_MESSAGE)
        Industry industry,

        @Schema(description = "ISO 3166-1 alpha-2 country code the ICP targets; null for any country",
                nullable = true, example = "NL")
        Country country,

        @Schema(description = "Smallest head-count band; null for no lower bound. Not above companySizeMax",
                nullable = true, example = "SIZE_11_50")
        RelationSize companySizeMin,

        @Schema(description = "Largest head-count band; null for no upper bound", nullable = true,
                example = "SIZE_201_500")
        RelationSize companySizeMax,

        @Schema(description = "Role of the person to approach; null for any", nullable = true, example = "CEO")
        TargetPersonRole targetPersonRole,

        @Schema(description = "Free description of the ICP; null for none", nullable = true,
                example = "Family-owned textile producers with their own factory and no security officer.")
        @NullOrNotBlank(message = IcpConstant.DESCRIPTION_BLANK_MESSAGE)
        @Size(max = IcpConstant.DESCRIPTION_MAX_LENGTH, message = IcpConstant.DESCRIPTION_SIZE_MESSAGE)
        String description
) {
}
