package com.korur.oba.apps.kalkan.page.service.impl.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.core.exception.enums.ExceptionDomainKalkanError;
import com.korur.oba.apps.kalkan.mock_data.KalkanMocks;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapper;
import com.korur.oba.apps.kalkan.page.mapper.KalkanPageMapperHelper;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageRepository;
import com.korur.oba.apps.kalkan.page.repository.KalkanPageTranslationRepository;
import com.korur.oba.apps.kalkan.page.service.impl.KalkanPageQueryServiceImpl;
import com.korur.oba.apps.kalkan.permission.service.KalkanAccessQueryService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KalkanPageQueryServiceImplUnitTest {

    @Mock
    private KalkanPageRepository kalkanPageRepository;
    @Mock
    private KalkanPageTranslationRepository kalkanPageTranslationRepository;
    @Mock
    private KalkanAccessQueryService kalkanAccessQueryService;
    @Mock
    private KalkanPageMapper kalkanPageMapper;
    @Mock
    private KalkanPageMapperHelper kalkanPageMapperHelper;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private KalkanPageQueryServiceImpl service;

    @Nested
    class GetEntityById {

        @Test
        void livePage_isReturnedWithoutAnOrganizationFilterOrLevelCheck() {
            // Arrange
            KalkanPage page = KalkanMocks.root(UUID.randomUUID(), "isms");
            when(kalkanPageRepository.findById(page.getId())).thenReturn(Optional.of(page));

            // Act
            KalkanPage found = service.getEntityById(page.getId());

            // Assert
            assertSame(page, found);
            verifyNoInteractions(kalkanAccessQueryService);
        }

        @Test
        void deletedOrMissingPage_throwsNotFound() {
            // Arrange
            UUID pageId = UUID.randomUUID();
            when(kalkanPageRepository.findById(pageId)).thenReturn(Optional.empty());

            // Act and Assert
            NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.getEntityById(pageId));
            assertEquals(ExceptionDomainKalkanError.NOT_PAGE_FOUND.getCode(), thrown.getErrorCode());
        }
    }
}
