package com.korur.oba.apps.otak.campaign.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.controller.CampaignContentControllerResponseConstant;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.service.CampaignContentQueryService;
import com.korur.oba.apps.otak.campaign.service.CampaignContentService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class CampaignContentControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();

    @Mock
    private CampaignContentService campaignContentService;

    @Mock
    private CampaignContentQueryService campaignContentQueryService;

    private CampaignContentController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new CampaignContentController(campaignContentService, campaignContentQueryService,
                new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_everyHandlerRequiresMemberDeleteIncluded() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listContents", OrganizationMemberRole.MEMBER,
                "createContent", OrganizationMemberRole.MEMBER,
                "updateContent", OrganizationMemberRole.MEMBER,
                "deleteContent", OrganizationMemberRole.MEMBER);

        // Act
        List<Method> handlers = new ArrayList<>();
        Set<String> handlerNames = new HashSet<>();
        for (Method method : CampaignContentController.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                handlers.add(method);
                handlerNames.add(method.getName());
            }
        }

        // Assert
        assertThat(handlerNames).isEqualTo(expected.keySet());
        for (Method method : handlers) {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).as(method.getName()).isNotNull();
            assertThat(access.value()).as(method.getName()).isEqualTo(expected.get(method.getName()));
        }
    }

    @Test
    void controller_servesTheNestedContentsPath() {
        assertThat(CampaignContentController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/campaigns/{campaignId}/contents");
    }

    @Test
    void listContents_returnsTheUnpagedListAnd200() {
        // Arrange
        List<CampaignContentResponse> contents = List.of(mock(CampaignContentResponse.class));
        when(campaignContentQueryService.list(organizationId, campaignId)).thenReturn(contents);

        // Act
        ResponseEntity<CustomResponse<List<CampaignContentResponse>>> response =
                controller.listContents(user, organizationId, campaignId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_LIST_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(contents);
        assertThat(response.getBody().pagination()).isNull();
    }

    @Test
    void createContent_delegatesWithHeaderOrganizationAndPathCampaignAndReturns201() {
        // Arrange
        CampaignContentRequest request = mock(CampaignContentRequest.class);
        CampaignContentResponse created = mock(CampaignContentResponse.class);
        when(campaignContentService.create(organizationId, campaignId, request)).thenReturn(created);

        // Act
        ResponseEntity<CustomResponse<CampaignContentResponse>> response =
                controller.createContent(user, organizationId, campaignId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message())
                .isEqualTo(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_CREATED);
        assertThat(response.getBody().data()).isSameAs(created);
    }

    @Test
    void updateContent_delegatesWithPathIdsAndReturns200() {
        // Arrange
        UUID contentId = UUID.randomUUID();
        CampaignContentRequest request = mock(CampaignContentRequest.class);
        CampaignContentResponse updated = mock(CampaignContentResponse.class);
        when(campaignContentService.update(organizationId, campaignId, contentId, request)).thenReturn(updated);

        // Act
        ResponseEntity<CustomResponse<CampaignContentResponse>> response =
                controller.updateContent(user, organizationId, campaignId, contentId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_UPDATED);
        assertThat(response.getBody().data()).isSameAs(updated);
    }

    @Test
    void deleteContent_delegatesAndReturns200() {
        // Arrange
        UUID contentId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response =
                controller.deleteContent(user, organizationId, campaignId, contentId);

        // Assert
        verify(campaignContentService).delete(organizationId, campaignId, contentId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(CampaignContentControllerResponseConstant.RESPONSE_CAMPAIGN_CONTENT_DELETED);
    }
}
