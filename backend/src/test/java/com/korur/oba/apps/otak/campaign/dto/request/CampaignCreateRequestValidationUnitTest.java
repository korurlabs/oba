package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

class CampaignCreateRequestValidationUnitTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private static CampaignCreateRequest request(String name, CampaignChannel channel, String postUrl,
                                                 List<CampaignContentRequest> contents) {
        return new CampaignCreateRequest(name, channel, null, null, null, postUrl, null, null, null, contents);
    }

    private static CampaignCreateRequest withContents(CampaignContentRequest... contents) {
        return request("IGEF 2026", CampaignChannel.LINKEDIN, null, new ArrayList<>(Arrays.asList(contents)));
    }

    private static Set<String> violated(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toSet());
    }

    @Test
    void onlyRequiredFields_isValid() {
        assertThat(violated(request("IGEF 2026", CampaignChannel.LINKEDIN, null, null))).isEmpty();
    }

    @Test
    void blankNameAndMissingChannel_areRejected() {
        assertThat(violated(request("  ", null, null, null))).containsExactlyInAnyOrder(
                "name: " + CampaignConstant.NAME_REQUIRED_MESSAGE,
                "channel: " + CampaignConstant.CHANNEL_REQUIRED_MESSAGE);
    }

    @Test
    void nameOf201Characters_isRejectedAnd200IsValid() {
        assertThat(violated(request("a".repeat(201), CampaignChannel.X, null, null)))
                .containsExactly("name: " + CampaignConstant.NAME_SIZE_MESSAGE);
        assertThat(violated(request("a".repeat(200), CampaignChannel.X, null, null))).isEmpty();
    }

    @Test
    void postUrl_mustBeNullOrAFullAddress() {
        assertThat(violated(request("IGEF", CampaignChannel.LINKEDIN, " ", null)))
                .contains("postUrl: " + CampaignConstant.POST_URL_BLANK_MESSAGE);
        assertThat(violated(request("IGEF", CampaignChannel.LINKEDIN, "linkedin.com/posts/1", null)))
                .containsExactly("postUrl: " + CampaignConstant.POST_URL_PATTERN_MESSAGE);
        assertThat(violated(request("IGEF", CampaignChannel.LINKEDIN,
                "https://www.linkedin.com/" + "p".repeat(480), null)))
                .containsExactly("postUrl: " + CampaignConstant.POST_URL_SIZE_MESSAGE);
        assertThat(violated(request("IGEF", CampaignChannel.LINKEDIN,
                "https://www.linkedin.com/posts/korur_igef-2026", null))).isEmpty();
    }

    @Test
    void fourContents_areRejectedAndThreeAreValid() {
        CampaignContentRequest nl = new CampaignContentRequest(DocumentLanguage.NL, "Hallo");
        CampaignContentRequest tr = new CampaignContentRequest(DocumentLanguage.TR, "Merhaba");
        CampaignContentRequest en = new CampaignContentRequest(DocumentLanguage.EN, "Hello");
        assertThat(violated(withContents(nl, tr, en, en)))
                .containsExactly("contents: " + CampaignConstant.CONTENTS_SIZE_MESSAGE);
        assertThat(violated(withContents(nl, tr, en))).isEmpty();
    }

    @Test
    void blankTextAndMissingLanguage_areRejected() {
        assertThat(violated(withContents(new CampaignContentRequest(null, " \n "))))
                .containsExactlyInAnyOrder(
                        "contents[0].language: " + CampaignConstant.CONTENT_LANGUAGE_REQUIRED_MESSAGE,
                        "contents[0].text: " + CampaignConstant.CONTENT_TEXT_REQUIRED_MESSAGE);
    }

    @Test
    void textOf20001Characters_isRejectedAnd20000IsValid() {
        assertThat(violated(withContents(new CampaignContentRequest(DocumentLanguage.EN, "t".repeat(20001)))))
                .containsExactly("contents[0].text: " + CampaignConstant.CONTENT_TEXT_SIZE_MESSAGE);
        assertThat(violated(withContents(new CampaignContentRequest(DocumentLanguage.EN, "t".repeat(20000)))))
                .isEmpty();
    }

    @Test
    void nullContentEntry_isRejected() {
        assertThat(violated(withContents((CampaignContentRequest) null)))
                .containsExactly("contents[0].<list element>: " + CampaignConstant.CONTENT_REQUIRED_MESSAGE);
    }

    @Test
    void blankSearch_isRejectedAndAbsentIsValid() {
        CampaignQueryRequest blank = new CampaignQueryRequest();
        blank.setSearch(" ");
        assertThat(violated(blank)).containsExactly("search: " + CampaignConstant.SEARCH_BLANK_MESSAGE);
        assertThat(violated(new CampaignQueryRequest())).isEmpty();
    }
}
