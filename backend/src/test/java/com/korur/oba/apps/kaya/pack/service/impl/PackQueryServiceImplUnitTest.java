package com.korur.oba.apps.kaya.pack.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.mapper.PackMapper;
import com.korur.oba.apps.kaya.pack.repository.PackRepository;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class PackQueryServiceImplUnitTest {

    @Mock
    private PackRepository packRepository;
    @Mock
    private PackMapper packMapper;

    private PackQueryServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PackQueryServiceImpl(packRepository, packMapper, new LoggingService());
    }

    private Pack pack(UUID parentPackId) {
        Pack pack = Pack.builder().organizationId(organizationId).name("pack").parentPackId(parentPackId).build();
        pack.setId(UUID.randomUUID());
        return pack;
    }

    @Nested
    class GetDescendantIds {

        @Test
        void tree_returnsEveryLevelBelowThePack() {
            // Arrange
            Pack root = pack(null);
            Pack child = pack(root.getId());
            Pack grandchild = pack(child.getId());
            when(packRepository.countByOrganizationId(organizationId)).thenReturn(3L);
            when(packRepository.findByOrganizationIdAndParentPackIdIn(organizationId, List.of(root.getId())))
                    .thenReturn(List.of(child));
            when(packRepository.findByOrganizationIdAndParentPackIdIn(organizationId, List.of(child.getId())))
                    .thenReturn(List.of(grandchild));
            when(packRepository.findByOrganizationIdAndParentPackIdIn(organizationId, List.of(grandchild.getId())))
                    .thenReturn(List.of());

            // Act
            List<UUID> result = service.getDescendantIds(organizationId, root.getId());

            // Assert
            assertThat(result).containsExactly(child.getId(), grandchild.getId());
        }

        @Test
        void walkReachingThePackCount_stopsEvenWhenRowsKeepComing() {
            // Arrange
            Pack root = pack(null);
            Pack child = pack(root.getId());
            when(packRepository.countByOrganizationId(organizationId)).thenReturn(1L);
            when(packRepository.findByOrganizationIdAndParentPackIdIn(organizationId, List.of(root.getId())))
                    .thenReturn(List.of(child));

            // Act
            List<UUID> result = service.getDescendantIds(organizationId, root.getId());

            // Assert
            assertThat(result).containsExactly(child.getId());
            verify(packRepository, never()).findByOrganizationIdAndParentPackIdIn(organizationId,
                    List.of(child.getId()));
        }

        @Test
        void packCountBeyondIntRange_failsInsteadOfWalkingUnbounded() {
            // Arrange
            when(packRepository.countByOrganizationId(organizationId)).thenReturn(Integer.MAX_VALUE + 1L);

            // Act and Assert
            assertThatThrownBy(() -> service.getDescendantIds(organizationId, UUID.randomUUID()))
                    .isInstanceOf(ArithmeticException.class);
            verify(packRepository, never()).findByOrganizationIdAndParentPackIdIn(any(), anyCollection());
        }

        @Test
        void largestIntPackCount_isAccepted() {
            // Arrange
            UUID packId = UUID.randomUUID();
            when(packRepository.countByOrganizationId(organizationId)).thenReturn((long) Integer.MAX_VALUE);
            when(packRepository.findByOrganizationIdAndParentPackIdIn(organizationId, List.of(packId)))
                    .thenReturn(List.of());

            // Act
            List<UUID> result = service.getDescendantIds(organizationId, packId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetChain {

        @Test
        void chain_returnsTheRootFirst() {
            // Arrange
            Pack root = pack(null);
            Pack child = pack(root.getId());
            when(packRepository.findByIdAndOrganizationId(child.getId(), organizationId))
                    .thenReturn(Optional.of(child));
            when(packRepository.findByIdAndOrganizationId(root.getId(), organizationId))
                    .thenReturn(Optional.of(root));
            when(packRepository.countByOrganizationId(organizationId)).thenReturn(2L);

            // Act
            List<Pack> result = service.getChain(organizationId, child.getId());

            // Assert
            assertThat(result).containsExactly(root, child);
        }

        @Test
        void chainLongerThanThePackCount_isRefused() {
            // Arrange
            Pack child = pack(UUID.randomUUID());
            when(packRepository.findByIdAndOrganizationId(child.getId(), organizationId))
                    .thenReturn(Optional.of(child));
            when(packRepository.countByOrganizationId(organizationId)).thenReturn(1L);

            // Act and Assert
            assertThatThrownBy(() -> service.getChain(organizationId, child.getId()))
                    .isInstanceOf(InternalServerErrorException.class);
        }

        @Test
        void packCountBeyondIntRange_failsBeforeWalking() {
            // Arrange
            Pack child = pack(UUID.randomUUID());
            when(packRepository.findByIdAndOrganizationId(child.getId(), organizationId))
                    .thenReturn(Optional.of(child));
            when(packRepository.countByOrganizationId(organizationId)).thenReturn(Long.MAX_VALUE);

            // Act and Assert
            assertThatThrownBy(() -> service.getChain(organizationId, child.getId()))
                    .isInstanceOf(ArithmeticException.class);
            verify(packRepository, never()).findByIdAndOrganizationId(child.getParentPackId(), organizationId);
        }
    }
}
