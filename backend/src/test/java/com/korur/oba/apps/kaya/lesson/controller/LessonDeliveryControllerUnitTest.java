package com.korur.oba.apps.kaya.lesson.controller;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// 2. Third-party
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// 3. Java SDK
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.constant.ExceptionHandlerLogConstant;
import com.korur.oba.dojo.exception.handler.mvc_layer.GlobalExceptionHandler;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.request.LessonDeliveryQueryRequest;
import com.korur.oba.apps.kaya.lesson.dto.response.LessonDeliveryResponse;
import com.korur.oba.apps.kaya.lesson.helper.LessonDeliveryRateLimiter;
import com.korur.oba.apps.kaya.lesson.mapper.LessonMapperImpl;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;
import com.korur.oba.apps.kaya.lesson.service.LessonService;
import com.korur.oba.apps.kaya.lesson.service.impl.LessonQueryServiceImpl;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.session.service.SessionQueryService;

import static com.korur.oba.apps.kaya.lesson.mock_data.LessonDeliveryMocks.rows;
import static com.korur.oba.apps.kaya.lesson.mock_data.LessonDeliveryMocks.stateOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The conditional answer of the lesson list, from the query string through the real binding, query
 * service and If-None-Match check, with only the database and the other domains mocked.
 */
@ExtendWith(MockitoExtension.class)
class LessonDeliveryControllerUnitTest {

    private static final String DELIVERY = "/kaya/delivery/lessons";
    private static final String REMOTE_URL = "remoteUrl";
    private static final String PAGE = "page";
    private static final String ETAG = "etag";
    private static final String REMOTE = "git@github.com:example/app.git";
    private static final int LESSONS = 123;
    private static final String PAGED_ETAG = "\"v2-[0-9a-f]{64}\"";
    private static final String UNPAGED_ETAG = "\"v2u-[0-9a-f]{64}\"";

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private SessionQueryService sessionQueryService;
    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private OrganizationAccessQueryService organizationAccessQueryService;
    @Mock
    private LessonService lessonService;
    @Mock
    private LoggingService loggingService;

    private final MachinePrincipal principal =
            new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "laptop");
    private LessonDeliveryController controller;

    @BeforeEach
    void setUp() {
        LessonQueryServiceImpl lessonQueryService = new LessonQueryServiceImpl(lessonRepository, sessionQueryService,
                kayaUserQueryService, repositoryQueryService, organizationAccessQueryService,
                new LessonDeliveryRateLimiter(Clock.systemUTC()), new LessonMapperImpl(), loggingService);
        controller = new LessonDeliveryController(lessonQueryService, lessonService, loggingService);
    }

    /** What a CLI sees of one request: the status, the ETag header and how many lessons the body holds. */
    private record Answer(int status, String etag, int lessons) {
    }

    private void givenLessons(List<LessonDeliveryProjection> lessons) {
        when(repositoryQueryService.getRepositories(principal.organizationId())).thenReturn(List.of());
        when(lessonRepository.findDeliveryState(principal.organizationId(), null))
                .thenAnswer(invocation -> stateOf(lessons));
        lenient().when(lessonRepository.findDeliveryLessons(eq(principal.organizationId()), eq(null), any()))
                .thenAnswer(invocation -> {
                    Pageable page = invocation.getArgument(2);
                    return lessons.stream().skip(page.getOffset()).limit(page.getPageSize()).toList();
                });
    }

    private static MockHttpServletRequest query(String page) {
        MockHttpServletRequest http = new MockHttpServletRequest(HttpMethod.GET.name(), DELIVERY);
        http.setParameter(REMOTE_URL, REMOTE);
        if (page != null) {
            http.setParameter(PAGE, page);
        }
        return http;
    }

    private static LessonDeliveryQueryRequest bind(MockHttpServletRequest http) {
        LessonDeliveryQueryRequest request = new LessonDeliveryQueryRequest();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(request);
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.bind(http);
        assertFalse(binder.getBindingResult().hasErrors());
        return request;
    }

    /** Sends the list request, with page only when page is not null, as a CLI does. */
    private Answer getLessons(String page, String ifNoneMatch) {
        MockHttpServletRequest http = query(page);
        if (ifNoneMatch != null) {
            http.addHeader(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<CustomResponse<List<LessonDeliveryResponse>>> response =
                controller.getLessons(principal, bind(http), new ServletWebRequest(http, servletResponse));

        String etag = servletResponse.getHeader(HttpHeaders.ETAG);
        return response == null
                ? new Answer(servletResponse.getStatus(), etag, 0)
                : new Answer(response.getStatusCode().value(), etag, response.getBody().data().size());
    }

    @Nested
    class GetLessons {

        @Test
        void page1WithTheEtagOfAnUnpagedRequest_returnsThePageWithData() {
            // Arrange: a CLI that sends no page stores this ETag next to the newest 100 of 123.
            givenLessons(rows(LESSONS));
            Answer unpaged = getLessons(null, null);

            // Act: the CLI that reads every page sends it back with page 1.
            Answer page1 = getLessons("1", unpaged.etag());

            // Assert
            assertEquals(HttpStatus.OK.value(), unpaged.status());
            assertEquals(100, unpaged.lessons());
            assertTrue(unpaged.etag().matches(UNPAGED_ETAG), unpaged.etag());
            assertEquals(HttpStatus.OK.value(), page1.status());
            assertEquals(100, page1.lessons());
            assertTrue(page1.etag().matches(PAGED_ETAG), page1.etag());
        }

        @Test
        void unpagedRequestWithItsOwnEtag_returns304() {
            // Arrange
            givenLessons(rows(LESSONS));
            String etag = getLessons(null, null).etag();

            // Act
            Answer again = getLessons(null, etag);

            // Assert
            assertEquals(HttpStatus.NOT_MODIFIED.value(), again.status());
            assertEquals(etag, again.etag());
            assertEquals(0, again.lessons());
        }

        @Test
        void page1WithItsOwnEtag_returns304() {
            // Arrange
            givenLessons(rows(LESSONS));
            String etag = getLessons("1", null).etag();

            // Act
            Answer again = getLessons("1", etag);

            // Assert
            assertEquals(HttpStatus.NOT_MODIFIED.value(), again.status());
            assertEquals(etag, again.etag());
        }

        @Test
        void unpagedRequestWithTheEtagOfPage1_returnsTheLessons() {
            // Arrange: a checkout that goes back to a CLI that sends no page holds the paged ETag.
            givenLessons(rows(LESSONS));
            String paged = getLessons("1", null).etag();

            // Act
            Answer unpaged = getLessons(null, paged);

            // Assert
            assertEquals(HttpStatus.OK.value(), unpaged.status());
            assertEquals(100, unpaged.lessons());
            assertNotEquals(paged, unpaged.etag());
        }
    }

    @Nested
    class Binding {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        private Set<String> invalidFields(LessonDeliveryQueryRequest request) {
            return validator.validate(request).stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }

        @Test
        void withoutPage_leavesThePageUnset() {
            // Act
            LessonDeliveryQueryRequest unpaged = bind(query(null));
            LessonDeliveryQueryRequest paged = bind(query("1"));

            // Assert
            assertNull(unpaged.getPage());
            assertEquals(1, paged.getPage());
        }

        @Test
        void etagOfTheUnpagedForm_isRefusedWhereThePagedFormPasses() {
            // Arrange
            String digest = stateOf(rows(LESSONS)).getDigest();
            MockHttpServletRequest unpagedForm = query("2");
            unpagedForm.setParameter(ETAG, "\"v2u-" + digest + "\"");
            MockHttpServletRequest pagedForm = query("2");
            pagedForm.setParameter(ETAG, "\"v2-" + digest + "\"");

            // Act and assert
            assertEquals(Set.of(ETAG), invalidFields(bind(unpagedForm)));
            assertTrue(invalidFields(bind(pagedForm)).isEmpty());
        }

        @Test
        void weakEtagOfThePagedForm_passesWhereTheWeakUnpagedFormIsRefused() {
            // Arrange: a proxy that compresses the answer sends the ETag header as W/"...".
            String digest = stateOf(rows(LESSONS)).getDigest();
            MockHttpServletRequest weakPaged = query("2");
            weakPaged.setParameter(ETAG, "W/\"v2-" + digest + "\"");
            MockHttpServletRequest weakUnpaged = query("2");
            weakUnpaged.setParameter(ETAG, "W/\"v2u-" + digest + "\"");
            MockHttpServletRequest weakUnquoted = query("2");
            weakUnquoted.setParameter(ETAG, "W/v2-" + digest);

            // Act and assert
            assertTrue(invalidFields(bind(weakPaged)).isEmpty());
            assertEquals(Set.of(ETAG), invalidFields(bind(weakUnpaged)));
            assertEquals(Set.of(ETAG), invalidFields(bind(weakUnquoted)));
        }
    }

    /**
     * A read that runs past its time limit, dispatched through a standalone MockMvc with the real
     * exception handler, so the answer is the one the API serves.
     */
    @Nested
    class ReadTimeLimit {

        private static final String READ_TIMED_OUT = "GEN_UPS_004";

        private MockMvc mockMvc;

        @BeforeEach
        void setUpMockMvc() {
            HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType().equals(MachinePrincipal.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                              NativeWebRequest request, WebDataBinderFactory binderFactory) {
                    return principal;
                }
            };
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setCustomArgumentResolvers(principalResolver)
                    .setControllerAdvice(new GlobalExceptionHandler(loggingService))
                    .build();
        }

        @Test
        void listCancelledAtTheLimit_answers503InsteadOf500() throws Exception {
            // Arrange: the database cancels the statement, which the repository reports as a query timeout.
            when(repositoryQueryService.getRepositories(principal.organizationId())).thenReturn(List.of());
            when(lessonRepository.findDeliveryState(principal.organizationId(), null))
                    .thenThrow(new QueryTimeoutException("canceling statement due to user request"));

            // Act and assert
            mockMvc.perform(get(DELIVERY).param(REMOTE_URL, REMOTE).param(PAGE, "1"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value(READ_TIMED_OUT));
            verify(loggingService).error(any(LogPrefix.class), eq(LogLayer.HANDLER),
                    eq(ExceptionHandlerLogConstant.LOG_DATABASE_TIMEOUT), any(QueryTimeoutException.class), any());
        }

        @Test
        void searchStartedAfterTheLimit_answers503InsteadOf500() throws Exception {
            // Arrange: a statement that would start after the limit is refused before it reaches the database.
            when(repositoryQueryService.getRepositories(principal.organizationId())).thenReturn(List.of());
            when(lessonRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenThrow(new TransactionTimedOutException("Transaction timed out"));

            // Act and assert
            mockMvc.perform(get(DELIVERY + "/search").param(REMOTE_URL, REMOTE).param("search", "flyway"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value(READ_TIMED_OUT));
        }
    }
}
