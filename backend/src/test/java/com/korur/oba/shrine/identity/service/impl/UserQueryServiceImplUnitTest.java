package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.dto.UserQueryRequest;
import com.korur.oba.shrine.identity.dto.response.UserListResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.mapper.UserMapper;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @InjectMocks
    private UserQueryServiceImpl userQueryService;

    private static User user() {
        User user = User.builder().email("user@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Nested
    class GetAllUsers {

        @Test
        void callerWithColleagues_readsColleagueSetOfCaller() {
            UUID callerId = UUID.randomUUID();
            User colleague = user();
            UserListResponse response = org.mockito.Mockito.mock(UserListResponse.class);
            when(organizationMemberQueryService.getColleagueUserIds(callerId))
                    .thenReturn(Set.of(callerId, colleague.getId()));
            when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(colleague)));
            when(userMapper.toListResponse(colleague)).thenReturn(response);

            Page<UserListResponse> result = userQueryService.getAllUsers(callerId, new UserQueryRequest());

            assertEquals(1, result.getTotalElements());
            assertEquals(response, result.getContent().get(0));
            verify(organizationMemberQueryService).getColleagueUserIds(callerId);
            verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        void emptyColleagueSet_stillQueriesWithRestrictedSpecification() {
            UUID callerId = UUID.randomUUID();
            when(organizationMemberQueryService.getColleagueUserIds(callerId)).thenReturn(Set.of());
            when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<UserListResponse> result = userQueryService.getAllUsers(callerId, new UserQueryRequest());

            assertEquals(0, result.getTotalElements());
        }
    }

    @Nested
    class FindUserByKeycloakId {

        @Test
        void knownKeycloakId_returnsTheUserFromOneRepositoryLookup() {
            // Arrange
            String keycloakId = UUID.randomUUID().toString();
            User user = User.builder().keycloakId(keycloakId).build();
            user.setId(UUID.randomUUID());
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            // Act
            Optional<User> result = userQueryService.findUserByKeycloakId(keycloakId);

            // Assert
            assertThat(result).containsSame(user);
            verify(userRepository).findByKeycloakId(keycloakId);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(userMapper);
        }

        @Test
        void unknownKeycloakId_returnsEmpty() {
            // Arrange
            String keycloakId = UUID.randomUUID().toString();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            // Act
            Optional<User> result = userQueryService.findUserByKeycloakId(keycloakId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class LockUserForKeyShare {

        @Test
        void knownUser_takesTheKeyShareLockOnly() {
            UUID userId = UUID.randomUUID();
            when(userRepository.lockForKeyShare(userId)).thenReturn(Optional.of(userId));

            userQueryService.lockUserForKeyShare(userId);

            verify(userRepository).lockForKeyShare(userId);
            verifyNoMoreInteractions(userRepository);
        }

        @Test
        void unknownUser_throwsNotFound() {
            UUID userId = UUID.randomUUID();
            when(userRepository.lockForKeyShare(userId)).thenReturn(Optional.empty());

            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> userQueryService.lockUserForKeyShare(userId));

            assertEquals(ExceptionDomainIdentityError.NOT_USER_FOUND.getCode(), exception.getErrorCode());
        }
    }
}
