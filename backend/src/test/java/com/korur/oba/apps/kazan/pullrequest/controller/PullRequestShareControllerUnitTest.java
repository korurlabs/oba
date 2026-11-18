package com.korur.oba.apps.kazan.pullrequest.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.pullrequest.constant.controller.PullRequestShareControllerResponseConstant;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.apps.kazan.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kazan.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kazan.pullrequest.service.PullRequestShareService;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class PullRequestShareControllerUnitTest {

    @Mock
    private PullRequestShareService pullRequestShareService;
    @Mock
    private PullRequestShareQueryService pullRequestShareQueryService;

    private PullRequestShareController controller;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();
    private final User user = PullRequestMocks.user("Ada", "Lovelace");
    private final PullRequestShareResponse shareResponse = PullRequestMocks.shareResponse(shareId, user, List.of());

    @BeforeEach
    void setUp() {
        controller = new PullRequestShareController(pullRequestShareService, pullRequestShareQueryService,
                new LoggingService());
    }

    @Test
    void createShare_returns201WithTheCreatedShare() {
        // Arrange
        PullRequestShareCreateRequest request = PullRequestMocks.createRequest(PullRequestShareStatus.IN_REVIEW,
                "please look", List.of());
        when(pullRequestShareService.createShare(user, organizationId, request)).thenReturn(shareResponse);

        // Act
        ResponseEntity<CustomResponse<PullRequestShareResponse>> response =
                controller.createShare(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_CREATED);
        assertThat(response.getBody().data()).isEqualTo(shareResponse);
    }

    @Test
    void getShare_readsThroughTheQueryServiceAndReturns200() {
        // Arrange
        when(pullRequestShareQueryService.getShare(user, organizationId, shareId)).thenReturn(shareResponse);

        // Act
        ResponseEntity<CustomResponse<PullRequestShareResponse>> response =
                controller.getShare(user, organizationId, shareId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_RETRIEVED);
        assertThat(response.getBody().data()).isEqualTo(shareResponse);
    }

    @Test
    void editShare_returns200WithTheEditedShare() {
        // Arrange
        PullRequestShareUpdateRequest request = PullRequestMocks.updateRequest(PullRequestShareStatus.MERGED, null,
                List.of());
        when(pullRequestShareService.editShare(user, organizationId, shareId, request)).thenReturn(shareResponse);

        // Act
        ResponseEntity<CustomResponse<PullRequestShareResponse>> response =
                controller.editShare(user, organizationId, shareId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_EDITED);
        assertThat(response.getBody().data()).isEqualTo(shareResponse);
    }

    @Test
    void editShare_callerIsNotTheAuthor_propagatesForbidden() {
        // Arrange
        PullRequestShareUpdateRequest request = PullRequestMocks.updateRequest(PullRequestShareStatus.MERGED, null,
                List.of());
        when(pullRequestShareService.editShare(user, organizationId, shareId, request)).thenThrow(
                new ForbiddenException(ExceptionDomainKazanError.SEC_CANNOT_EDIT_OTHERS_SHARE));

        // Act and assert
        assertThatThrownBy(() -> controller.editShare(user, organizationId, shareId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteShare_delegatesAndReturns200() {
        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteShare(user, organizationId, shareId);

        // Assert
        verify(pullRequestShareService).deleteShare(user, organizationId, shareId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_DELETED);
    }

    @Test
    void approveShare_setsTheApprovalAndReturns200() {
        // Arrange
        when(pullRequestShareService.setApproval(user, organizationId, shareId, true)).thenReturn(shareResponse);

        // Act
        ResponseEntity<CustomResponse<PullRequestShareResponse>> response =
                controller.approveShare(user, organizationId, shareId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_APPROVED);
        assertThat(response.getBody().data()).isEqualTo(shareResponse);
    }

    @Test
    void withdrawShareApproval_clearsTheApprovalAndReturns200() {
        // Arrange
        when(pullRequestShareService.setApproval(user, organizationId, shareId, false)).thenReturn(shareResponse);

        // Act
        ResponseEntity<CustomResponse<PullRequestShareResponse>> response =
                controller.withdrawShareApproval(user, organizationId, shareId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_APPROVAL_WITHDRAWN);
        assertThat(response.getBody().data()).isEqualTo(shareResponse);
    }

    @Test
    void listShares_delegatesAndReturns200WithTheSharesAndPagination() {
        // Arrange
        PullRequestShareQueryRequest query = new PullRequestShareQueryRequest();
        Page<PullRequestShareResponse> page = new PageImpl<>(List.of(shareResponse), PageRequest.of(0, 25), 1);
        when(pullRequestShareQueryService.listShares(user, organizationId, query)).thenReturn(page);

        // Act
        ResponseEntity<CustomResponse<List<PullRequestShareResponse>>> response =
                controller.listShares(user, organizationId, query);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_LIST_RETRIEVED);
        assertThat(response.getBody().data()).containsExactly(shareResponse);
        assertThat(response.getBody().pagination()).isNotNull();
        assertThat(response.getBody().pagination().totalElements()).isEqualTo(1);
        assertThat(response.getBody().pagination().currentPage()).isEqualTo(1);
        assertThat(response.getBody().pagination().pageSize()).isEqualTo(25);
    }

    @Test
    void previewShare_delegatesAndReturns200WithThePreview() {
        // Arrange
        PullRequestShareCreateRequest request = PullRequestMocks.createRequest(PullRequestShareStatus.IN_REVIEW,
                "please look", List.of());
        PullRequestSharePreviewResponse preview = new PullRequestSharePreviewResponse(null, "Mobile menu");
        when(pullRequestShareQueryService.previewShare(user, organizationId, request)).thenReturn(preview);

        // Act
        ResponseEntity<CustomResponse<PullRequestSharePreviewResponse>> response =
                controller.previewShare(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().message())
                .isEqualTo(PullRequestShareControllerResponseConstant.RESPONSE_PULL_REQUEST_SHARE_PREVIEWED);
        assertThat(response.getBody().data()).isEqualTo(preview);
        verifyNoInteractions(pullRequestShareService);
    }
}
