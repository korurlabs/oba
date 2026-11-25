package com.korur.oba.apps.kazan.cyberscore.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class CheckValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID checkId = UUID.randomUUID();

    @Mock
    private CheckRepository checkRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private CheckValidator validator;

    @Nested
    class EnsureCheckExists {

        @Test
        void checkOfTheOrganization_returnsIt() {
            Check check = Check.builder().name("a").build();
            when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.of(check));

            assertThat(validator.ensureCheckExists(organizationId, checkId)).isSameAs(check);
        }

        @Test
        void checkOfAnotherOrganization_throwsNotFound() {
            when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validator.ensureCheckExists(organizationId, checkId))
                    .isInstanceOfSatisfying(NotFoundException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.NOT_CHECK_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureNameIsUnique {

        @Test
        void nameFree_passes() {
            when(checkRepository.existsByOrganizationIdAndName(organizationId, "a")).thenReturn(false);

            assertThatCode(() -> validator.ensureNameIsUnique(organizationId, "a")).doesNotThrowAnyException();
        }

        @Test
        void nameTaken_throwsConflict() {
            when(checkRepository.existsByOrganizationIdAndName(organizationId, "a")).thenReturn(true);

            assertThatThrownBy(() -> validator.ensureNameIsUnique(organizationId, "a"))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.STA_CHECK_NAME_DUPLICATE.getCode()));
        }

        @Test
        void editKeepingItsOwnName_passes() {
            when(checkRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, "a", checkId)).thenReturn(false);

            assertThatCode(() -> validator.ensureNameIsUnique(organizationId, "a", checkId)).doesNotThrowAnyException();
        }

        @Test
        void editToTheNameOfAnotherCheck_throwsConflict() {
            when(checkRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, "a", checkId)).thenReturn(true);

            assertThatThrownBy(() -> validator.ensureNameIsUnique(organizationId, "a", checkId))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
