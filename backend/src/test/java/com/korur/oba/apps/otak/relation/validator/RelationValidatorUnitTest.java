package com.korur.oba.apps.otak.relation.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class RelationValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private RelationValidator relationValidator;

    private static Relation relation(RelationType type, PipelineStage stage) {
        Relation relation = Relation.builder().name("Fabrikam").type(type).stage(stage).build();
        relation.setId(UUID.randomUUID());
        return relation;
    }

    @Nested
    class EnsureRelationExists {

        @Test
        void relationOfOrganization_returnsRelation() {
            // Arrange
            Relation relation = relation(RelationType.LEAD, PipelineStage.NEW);
            when(relationRepository.findByIdAndOrganizationId(relation.getId(), organizationId))
                    .thenReturn(Optional.of(relation));

            // Act
            Relation result = relationValidator.ensureRelationExists(organizationId, relation.getId());

            // Assert
            assertSame(relation, result);
        }

        @Test
        void relationMissingOrOfAnotherOrganization_throwsRelationNotFound() {
            // Arrange: the lookup is scoped by organization, so another tenant's relation is not found
            UUID relationId = UUID.randomUUID();
            when(relationRepository.findByIdAndOrganizationId(relationId, organizationId)).thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> relationValidator.ensureRelationExists(organizationId, relationId));

            // Assert
            assertEquals(ExceptionDomainOtakError.NOT_RELATION_FOUND.getCode(), exception.getErrorCode());
            assertEquals("OTK_NOT_001", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureRelationExistsInAnyOrganization {

        @Test
        void relationExists_returnsIt() {
            Relation relation = relation(RelationType.LEAD, PipelineStage.NEW);
            when(relationRepository.findById(relation.getId())).thenReturn(Optional.of(relation));

            assertSame(relation, relationValidator.ensureRelationExists(relation.getId()));
        }

        @Test
        void relationMissing_throwsOtk001() {
            UUID relationId = UUID.randomUUID();
            when(relationRepository.findById(relationId)).thenReturn(Optional.empty());

            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> relationValidator.ensureRelationExists(relationId));

            assertEquals("OTK_NOT_001", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureOwnerIsOrganizationMember {

        private final UUID ownerUserId = UUID.randomUUID();

        @Test
        void activeMember_passes() {
            // Arrange
            OrganizationMember member = OrganizationMember.builder().status(OrganizationMemberStatus.ACTIVE).build();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.of(member));

            // Act and assert
            assertDoesNotThrow(() -> relationValidator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId));
            verify(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.OTAK, List.of(ownerUserId));
        }

        @Test
        void activeMemberWithoutOtak_throwsMemberWithoutAppAccess() {
            // Arrange
            OrganizationMember member = OrganizationMember.builder().status(OrganizationMemberStatus.ACTIVE).build();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.of(member));
            doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS,
                    Map.of("userId", ownerUserId)))
                    .when(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.OTAK, List.of(ownerUserId));

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> relationValidator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId));

            // Assert
            assertEquals("APP_VAL_002", exception.getErrorCode());
        }

        @Test
        void disabledMember_throwsOwnerNotMember() {
            // Arrange
            OrganizationMember member = OrganizationMember.builder().status(OrganizationMemberStatus.DISABLED).build();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.of(member));

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId));

            // Assert
            assertEquals(ExceptionDomainOtakError.STA_OWNER_NOT_ORGANIZATION_MEMBER.getCode(), exception.getErrorCode());
        }

        @Test
        void userOfAnotherOrganizationOrUnknown_throwsOwnerNotMember() {
            // Arrange
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.empty());

            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId));

            // Assert: the membership code wins, and the app check never runs
            assertEquals("OTK_STA_001", exception.getErrorCode());
            verify(appAccessValidator, never()).ensureUsersHaveAppAccess(any(), any(), any());
        }

        @Test
        void noOwner_passesWithoutLookup() {
            // Act
            relationValidator.ensureOwnerIsOrganizationMember(organizationId, null);

            // Assert
            verify(organizationMemberQueryService, never()).findMember(any(), any());
            verify(appAccessValidator, never()).ensureUsersHaveAppAccess(any(), any(), any());
        }
    }

    @Nested
    class EnsureRelationIsLead {

        @Test
        void lead_passes() {
            assertDoesNotThrow(() -> relationValidator.ensureRelationIsLead(relation(RelationType.LEAD, PipelineStage.WARM)));
        }

        @Test
        void customer_throwsRelationNotLead() {
            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureRelationIsLead(relation(RelationType.CUSTOMER, null)));

            // Assert
            assertEquals(ExceptionDomainOtakError.STA_RELATION_NOT_LEAD.getCode(), exception.getErrorCode());
            assertEquals("OTK_STA_002", exception.getErrorCode());
        }

        @Test
        void supplier_throwsRelationNotLead() {
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureRelationIsLead(relation(RelationType.SUPPLIER, null)));

            assertEquals(ExceptionDomainOtakError.STA_RELATION_NOT_LEAD.getCode(), exception.getErrorCode());
        }
    }

    @Nested
    class EnsureTypeChanges {

        @Test
        void otherType_passes() {
            assertDoesNotThrow(() -> relationValidator.ensureTypeChanges(relation(RelationType.LEAD, PipelineStage.WARM),
                    RelationType.CUSTOMER));
        }

        @Test
        void sameType_throwsTypeUnchanged() {
            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureTypeChanges(relation(RelationType.LEAD, PipelineStage.WARM),
                            RelationType.LEAD));

            // Assert
            assertEquals(ExceptionDomainOtakError.STA_RELATION_TYPE_UNCHANGED.getCode(), exception.getErrorCode());
            assertEquals("OTK_STA_003", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureStageFitsType {

        @Test
        void leadWithLeadStage_passes() {
            for (PipelineStage stage : new PipelineStage[] {PipelineStage.NEW, PipelineStage.QUALIFIED,
                    PipelineStage.CONTACTED, PipelineStage.WARM, PipelineStage.PROPOSAL, PipelineStage.LOST,
                    PipelineStage.PARKED}) {
                assertDoesNotThrow(() -> relationValidator.ensureStageFitsType(RelationType.LEAD, stage));
            }
        }

        @Test
        void customerWithClosedOrDirect_passes() {
            assertDoesNotThrow(() -> relationValidator.ensureStageFitsType(RelationType.CUSTOMER, PipelineStage.CLOSED));
            assertDoesNotThrow(() -> relationValidator.ensureStageFitsType(RelationType.CUSTOMER, PipelineStage.DIRECT));
        }

        @Test
        void nullStage_passesForEveryType() {
            for (RelationType type : RelationType.values()) {
                assertDoesNotThrow(() -> relationValidator.ensureStageFitsType(type, null));
            }
        }

        @Test
        void leadWithClosedOrDirect_throwsStageNotForType() {
            for (PipelineStage stage : new PipelineStage[] {PipelineStage.CLOSED, PipelineStage.DIRECT}) {
                ConflictException exception = assertThrows(ConflictException.class,
                        () -> relationValidator.ensureStageFitsType(RelationType.LEAD, stage));
                assertEquals("OTK_STA_006", exception.getErrorCode());
            }
        }

        @Test
        void customerWithLeadStage_throwsStageNotForType() {
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureStageFitsType(RelationType.CUSTOMER, PipelineStage.WARM));
            assertEquals("OTK_STA_006", exception.getErrorCode());
        }

        @Test
        void supplierWithAnyStage_throwsStageNotForType() {
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureStageFitsType(RelationType.SUPPLIER, PipelineStage.NEW));
            assertEquals("OTK_STA_006", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureStageOnlyForLead {

        @Test
        void leadWithStage_passes() {
            assertDoesNotThrow(() -> relationValidator.ensureStageOnlyForLead(RelationType.LEAD, PipelineStage.WARM));
        }

        @Test
        void leadWithoutStage_passes() {
            assertDoesNotThrow(() -> relationValidator.ensureStageOnlyForLead(RelationType.LEAD, null));
        }

        @Test
        void customerWithStage_throwsStaRelationNotLead() {
            // Act
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureStageOnlyForLead(RelationType.CUSTOMER, PipelineStage.WARM));

            // Assert
            assertEquals(ExceptionDomainOtakError.STA_RELATION_NOT_LEAD.getCode(), exception.getErrorCode());
            assertEquals("OTK_STA_002", exception.getErrorCode());
        }

        @Test
        void supplierWithStage_throwsStaRelationNotLead() {
            ConflictException exception = assertThrows(ConflictException.class,
                    () -> relationValidator.ensureStageOnlyForLead(RelationType.SUPPLIER, PipelineStage.NEW));

            assertEquals("OTK_STA_002", exception.getErrorCode());
        }

        @Test
        void customerOrSupplierWithoutStage_passes() {
            assertDoesNotThrow(() -> relationValidator.ensureStageOnlyForLead(RelationType.CUSTOMER, null));
            assertDoesNotThrow(() -> relationValidator.ensureStageOnlyForLead(RelationType.SUPPLIER, null));
        }
    }
}
