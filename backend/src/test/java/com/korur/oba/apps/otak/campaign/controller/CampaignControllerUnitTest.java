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
import com.korur.oba.apps.otak.campaign.constant.controller.CampaignControllerResponseConstant;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.service.CampaignQueryService;
import com.korur.oba.apps.otak.campaign.service.CampaignService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

@ExtendWith(MockitoExtension.class)
class CampaignControllerUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private CampaignService campaignService;

    @Mock
    private CampaignQueryService campaignQueryService;

    private CampaignController controller;

    private User user;

    @BeforeEach
    void setUp() {
        controller = new CampaignController(campaignService, campaignQueryService, new LoggingService());
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void endpoints_deleteRequiresAdminAndEveryOtherHandlerMember() {
        // Arrange
        Map<String, OrganizationMemberRole> expected = Map.of(
                "listCampaigns", OrganizationMemberRole.MEMBER,
                "createCampaign", OrganizationMemberRole.MEMBER,
                "getCampaign", OrganizationMemberRole.MEMBER,
                "updateCampaign", OrganizationMemberRole.MEMBER,
                "deleteCampaign", OrganizationMemberRole.ADMIN);

        // Act
        List<Method> handlers = new ArrayList<>();
        Set<String> handlerNames = new HashSet<>();
        for (Method method : CampaignController.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                handlers.add(method);
                handlerNames.add(method.getName());
            }
        }

        // Assert
        assertThat(handlerNames).isEqualTo(expected.keySet());
        assertThat(handlers).allSatisfy(method -> {
            assertThat(method.isAnnotationPresent(PreAuthorize.class)).isFalse();
            RequiredUserAccess access = method.getAnnotation(RequiredUserAccess.class);
            assertThat(access).as(method.getName()).isNotNull();
            assertThat(access.value()).as(method.getName()).isEqualTo(expected.get(method.getName()));
        });
    }

    @Test
    void controller_servesTheOtakCampaignsPath() {
        assertThat(CampaignController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/otak/campaigns");
    }

    @Test
    void listCampaigns_returnsTheWholeUnpagedListAnd200() {
        // Arrange
        CampaignQueryRequest request = new CampaignQueryRequest();
        List<CampaignListResponse> campaigns = List.of(mock(CampaignListResponse.class), mock(CampaignListResponse.class));
        when(campaignQueryService.list(organizationId, request)).thenReturn(campaigns);

        // Act
        ResponseEntity<CustomResponse<List<CampaignListResponse>>> response =
                controller.listCampaigns(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_LIST_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(campaigns);
        assertThat(response.getBody().pagination()).isNull();
    }

    @Test
    void createCampaign_delegatesWithHeaderOrganizationAndReturns201() {
        // Arrange
        CampaignCreateRequest request = mock(CampaignCreateRequest.class);
        CampaignDetailResponse detail = mock(CampaignDetailResponse.class);
        when(campaignService.create(organizationId, user, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<CampaignDetailResponse>> response =
                controller.createCampaign(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message()).isEqualTo(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_CREATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void getCampaign_delegatesToTheQueryServiceAndReturns200() {
        // Arrange
        UUID campaignId = UUID.randomUUID();
        CampaignDetailResponse detail = mock(CampaignDetailResponse.class);
        when(campaignQueryService.get(organizationId, campaignId)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<CampaignDetailResponse>> response =
                controller.getCampaign(user, organizationId, campaignId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_RETRIEVED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void updateCampaign_delegatesWithPathIdAndReturns200() {
        // Arrange
        UUID campaignId = UUID.randomUUID();
        CampaignUpdateRequest request = mock(CampaignUpdateRequest.class);
        CampaignDetailResponse detail = mock(CampaignDetailResponse.class);
        when(campaignService.update(organizationId, campaignId, request)).thenReturn(detail);

        // Act
        ResponseEntity<CustomResponse<CampaignDetailResponse>> response =
                controller.updateCampaign(user, organizationId, campaignId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_UPDATED);
        assertThat(response.getBody().data()).isSameAs(detail);
    }

    @Test
    void deleteCampaign_delegatesAndReturns200() {
        // Arrange
        UUID campaignId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteCampaign(user, organizationId, campaignId);

        // Assert
        verify(campaignService).delete(organizationId, campaignId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message()).isEqualTo(CampaignControllerResponseConstant.RESPONSE_CAMPAIGN_DELETED);
    }
}
