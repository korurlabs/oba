package com.korur.oba.apps.otak.icp.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.korur.oba.apps.otak.support.RequestValidationSupport.violations;
import static com.korur.oba.apps.otak.support.RequestValidationSupport.with;
import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.apps.otak.icp.constant.IcpConstant;
import com.korur.oba.apps.otak.icp.enums.Industry;

class IcpCreateRequestValidationUnitTest {

    private static IcpCreateRequest valid() {
        return new IcpCreateRequest("Textile exporters", Industry.MANUFACTURING, null, null, null, null, null);
    }

    @Test
    void validBody_hasNoViolation() {
        assertThat(violations(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void name_blank_isRequired(String blank) {
        assertThat(violations(with(valid(), "name", blank)))
                .containsExactly("name: " + IcpConstant.NAME_REQUIRED_MESSAGE);
    }

    @Test
    void name_overTheLimit_isRejectedBySize() {
        assertThat(violations(with(valid(), "name", "a".repeat(IcpConstant.NAME_MAX_LENGTH + 1))))
                .containsExactly("name: " + IcpConstant.NAME_SIZE_MESSAGE);
    }

    @Test
    void industry_omitted_isRequired() {
        assertThat(violations(with(valid(), "industry", null)))
                .containsExactly("industry: " + IcpConstant.INDUSTRY_REQUIRED_MESSAGE);
    }

    @Test
    void description_nullIsValidAndBlankIsRejected() {
        assertThat(violations(with(valid(), "description", null))).isEmpty();
        assertThat(violations(with(valid(), "description", "")))
                .containsExactly("description: " + IcpConstant.DESCRIPTION_BLANK_MESSAGE);
        assertThat(violations(with(valid(), "description", "   ")))
                .containsExactly("description: " + IcpConstant.DESCRIPTION_BLANK_MESSAGE);
    }

    @Test
    void description_overTheLimit_isRejectedBySize() {
        assertThat(violations(with(valid(), "description", "d".repeat(IcpConstant.DESCRIPTION_MAX_LENGTH + 1))))
                .containsExactly("description: " + IcpConstant.DESCRIPTION_SIZE_MESSAGE);
    }

    @Test
    void querySearch_blankIsRejectedAndAbsentIsValid() {
        IcpQueryRequest blank = new IcpQueryRequest();
        blank.setSearch("  ");
        IcpQueryRequest tooLong = new IcpQueryRequest();
        tooLong.setSearch("s".repeat(IcpConstant.SEARCH_MAX_LENGTH + 1));

        assertThat(violations(blank)).containsExactly("search: " + IcpConstant.SEARCH_BLANK_MESSAGE);
        assertThat(violations(tooLong)).containsExactly("search: " + IcpConstant.SEARCH_SIZE_MESSAGE);
        assertThat(violations(new IcpQueryRequest())).isEmpty();
    }
}
