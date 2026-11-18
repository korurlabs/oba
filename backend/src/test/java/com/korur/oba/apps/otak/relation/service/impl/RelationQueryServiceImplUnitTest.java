package com.korur.oba.apps.otak.relation.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.projection.RelationMainContactProjection;
import com.korur.oba.apps.otak.relation.dto.request.RelationQueryRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerCandidateResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper;
import com.korur.oba.apps.otak.relation.mapper.RelationMapperImpl;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.apps.otak.relation.validator.RelationValidator;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class RelationQueryServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private RelationValidator relationValidator;

    @Mock
    private RelationOwnerHelper relationOwnerHelper;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private LoggingService loggingService;

    private RelationQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        // The real mapper runs, so the mapped fields and the role conversion are the production ones.
        service = new RelationQueryServiceImpl(relationRepository, new RelationMapperImpl(), relationValidator,
                relationOwnerHelper, organizationMemberQueryService, appAccessQueryService, loggingService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OrganizationMemberSummaryProjection summary(UUID userId, String email, String firstName,
                                                               String role) {
        return new OrganizationMemberSummaryProjection() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public String getFirstName() {
                return firstName;
            }

            @Override
            public String getLastName() {
                return null;
            }

            @Override
            public String getRole() {
                return role;
            }
        };
    }

    @Nested
    class ListOwnerCandidates {

        @Test
        void activeMembers_listedWithOtakAccessInNameOrder() {
            // Arrange
            UUID zoeId = UUID.randomUUID();
            UUID adaId = UUID.randomUUID();
            UUID unnamedId = UUID.randomUUID();
            OrganizationMemberSummaryProjection zoe = summary(zoeId, "zoe@example.com", "Zoe", "MEMBER");
            OrganizationMemberSummaryProjection ada = summary(adaId, "ada@example.com", "ada", "GUEST");
            OrganizationMemberSummaryProjection unnamed = summary(unnamedId, "new@example.com", null, "ADMIN");
            when(organizationMemberQueryService.listActiveMemberSummaries(organizationId, NOW))
                    .thenReturn(List.of(zoe, unnamed, ada));
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.OTAK,
                    List.of(zoeId, unnamedId, adaId))).thenReturn(Set.of(zoeId, unnamedId));

            // Act
            List<RelationOwnerCandidateResponse> result = service.listOwnerCandidates(organizationId);

            // Assert: names ignore case, a member without a name comes last
            assertThat(result).extracting(RelationOwnerCandidateResponse::userId)
                    .containsExactly(adaId, zoeId, unnamedId);
            assertThat(result.getFirst().otakAccess()).isFalse();
            assertThat(result.getFirst().role()).isEqualTo(OrganizationMemberRole.GUEST);
            assertThat(result.getFirst().email()).isEqualTo("ada@example.com");
            assertThat(result.get(1).otakAccess()).isTrue();
            assertThat(result.get(2).otakAccess()).isTrue();
            assertThat(result.get(2).role()).isEqualTo(OrganizationMemberRole.ADMIN);
        }

        @Test
        void noActiveMember_returnsEmpty() {
            // Arrange
            when(organizationMemberQueryService.listActiveMemberSummaries(organizationId, NOW)).thenReturn(List.of());
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.OTAK, List.of()))
                    .thenReturn(Set.of());

            // Act
            List<RelationOwnerCandidateResponse> result = service.listOwnerCandidates(organizationId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class MainContactNames {

        private Relation relation(String name) {
            Relation relation = Relation.builder().name(name).type(RelationType.LEAD).stage(PipelineStage.NEW)
                    .organizationId(organizationId).build();
            relation.setId(UUID.randomUUID());
            return relation;
        }

        private RelationMainContactProjection mainContact(UUID relationId, String name, String email,
                                                          boolean person) {
            return new RelationMainContactProjection() {
                @Override
                public UUID getRelationId() {
                    return relationId;
                }

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getEmail() {
                    return email;
                }

                @Override
                public Boolean getMainPersonContact() {
                    return person;
                }
            };
        }

        private RelationMainContactProjection mainContact(UUID relationId, String name) {
            return mainContact(relationId, name, null, true);
        }

        @SuppressWarnings("unchecked")
        @Test
        void list_mapsTheMainContactNameAndLeavesAbsentOnesNull() {
            // Arrange
            Relation withMain = relation("Fabrikam");
            Relation withoutMain = relation("Tailspin");
            when(relationRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(withMain, withoutMain)));
            when(relationRepository.findMainContacts(organizationId,
                    List.of(withMain.getId(), withoutMain.getId())))
                    .thenReturn(List.of(mainContact(withMain.getId(), "Jan de Vries")));

            // Act
            Page<RelationListResponse> result = service.list(organizationId, new RelationQueryRequest());

            // Assert
            assertThat(result.getContent()).extracting(RelationListResponse::mainPersonContactName)
                    .containsExactly("Jan de Vries", null);
        }

        @SuppressWarnings("unchecked")
        @Test
        void list_mapsPersonNameAndEmailAndBusinessEmailToTheirOwnFields() {
            // Arrange
            Relation both = relation("Fabrikam");
            Relation onlyPerson = relation("Tailspin");
            Relation onlyBusiness = relation("Globex");
            Relation none = relation("Stof");
            when(relationRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(both, onlyPerson, onlyBusiness, none)));
            when(relationRepository.findMainContacts(organizationId,
                    List.of(both.getId(), onlyPerson.getId(), onlyBusiness.getId(), none.getId())))
                    .thenReturn(List.of(
                            mainContact(both.getId(), "Jan de Vries", "jan@fabrikam.example", true),
                            mainContact(both.getId(), "Info", "info@fabrikam.example", false),
                            mainContact(onlyPerson.getId(), "Anna", "anna@tailspin.example", true),
                            mainContact(onlyBusiness.getId(), "Info", "info@globex.example", false)));

            // Act
            List<RelationListResponse> result = service.list(organizationId, new RelationQueryRequest())
                    .getContent();

            // Assert: a relation without a main of a kind has null in that kind's fields
            assertThat(result).extracting(RelationListResponse::mainPersonContactName)
                    .containsExactly("Jan de Vries", "Anna", null, null);
            assertThat(result).extracting(RelationListResponse::mainPersonContactEmail)
                    .containsExactly("jan@fabrikam.example", "anna@tailspin.example", null, null);
            assertThat(result).extracting(RelationListResponse::mainBusinessContactEmail)
                    .containsExactly("info@fabrikam.example", null, "info@globex.example", null);
        }

        @SuppressWarnings("unchecked")
        @Test
        void listPipeline_mapsPersonAndBusinessContactFields() {
            // Arrange
            Relation lead = relation("Fabrikam");
            when(relationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lead));
            when(relationRepository.findMainContacts(organizationId, List.of(lead.getId())))
                    .thenReturn(List.of(
                            mainContact(lead.getId(), "Jan de Vries", "jan@fabrikam.example", true),
                            mainContact(lead.getId(), "Info", "info@fabrikam.example", false)));

            // Act
            List<RelationListResponse> result = service.listPipeline(organizationId);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().mainPersonContactEmail()).isEqualTo("jan@fabrikam.example");
            assertThat(result.getFirst().mainBusinessContactEmail()).isEqualTo("info@fabrikam.example");
        }

        @SuppressWarnings("unchecked")
        @Test
        void list_emptyPage_doesNotQueryTheMainContacts() {
            // Arrange
            when(relationRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            // Act
            Page<RelationListResponse> result = service.list(organizationId, new RelationQueryRequest());

            // Assert
            assertThat(result.getContent()).isEmpty();
            verify(relationRepository, never()).findMainContacts(any(), any());
        }

        @SuppressWarnings("unchecked")
        @Test
        void listPipeline_mapsTheMainContactName() {
            // Arrange
            Relation lead = relation("Fabrikam");
            when(relationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lead));
            when(relationRepository.findMainContacts(organizationId, List.of(lead.getId())))
                    .thenReturn(List.of(mainContact(lead.getId(), "Jan de Vries")));

            // Act
            List<RelationListResponse> result = service.listPipeline(organizationId);

            // Assert
            assertThat(result).extracting(RelationListResponse::mainPersonContactName).containsExactly("Jan de Vries");
        }

        @SuppressWarnings("unchecked")
        @Test
        void listPipeline_noRelation_doesNotQueryTheMainContacts() {
            // Arrange
            when(relationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

            // Act
            List<RelationListResponse> result = service.listPipeline(organizationId);

            // Assert
            assertThat(result).isEmpty();
            verify(relationRepository, never()).findMainContacts(any(), any());
        }
    }

    @Nested
    class GetEntityById {

        @Test
        void relationExists_returnsTheValidatorsRelationWithoutOrganizationScope() {
            Relation relation = Relation.builder().id(UUID.randomUUID()).name("Fabrikam").build();
            when(relationValidator.ensureRelationExists(relation.getId())).thenReturn(relation);

            assertThat(service.getEntityById(relation.getId())).isSameAs(relation);
        }
    }
}
