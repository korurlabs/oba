package com.korur.oba.dojo.security.interceptor;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.handler.ApiTokenKindHandler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves, without a database, that every controller method is classified: annotated with
 * {@link RequiredUserAccess}, listed as organization-exempt, listed as public, token-exempt (method and
 * path in a token kind's exempt endpoints), or served by the Alertmanager integration chain. The
 * interceptor refuses an unclassified method at run time; this test moves that failure to the build. It
 * scans the controller classes by reflection and merges the class and method mapping paths, so no Spring
 * context starts.
 *
 * It also proves that every security chain and every token kind is declared in the security package, so
 * the credential rules of the application are read in one place.
 */
class RequiredUserAccessCoverageUnitTest {

    private static final String BASE_PACKAGE = "com.korur.oba";
    private static final String SECURITY_PACKAGE = "com.korur.oba.dojo.security.";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** One mapped controller method with the merged path of one of its mappings and its HTTP methods. */
    private record Handler(Method method, String path, List<RequestMethod> httpMethods) {

        boolean annotated() {
            return method.isAnnotationPresent(RequiredUserAccess.class);
        }

        boolean tokenExempt() {
            return tokenExemptEndpoints().stream().anyMatch(endpoint -> PATH_MATCHER.match(endpoint.pathPattern(), path)
                    && httpMethods.stream().anyMatch(httpMethod -> httpMethod.name().equals(endpoint.method().name())));
        }

        String label() {
            return method.getDeclaringClass().getSimpleName() + "#" + method.getName() + " " + path;
        }
    }

    @Test
    void handlers_areAnnotatedExemptPublicTokenExemptOrIntegration() {
        List<String> unclassified = new ArrayList<>();

        for (Handler handler : scanHandlers()) {
            if (isIntegration(handler) || handler.annotated() || handler.tokenExempt()) {
                continue;
            }
            if (isReserved(handler) || (!isExempt(handler.path()) && !isPublic(handler.path()))) {
                unclassified.add(handler.label());
            }
        }

        assertTrue(unclassified.isEmpty(),
                "Handlers with no access rule, annotated nowhere and listed nowhere:\n" + String.join("\n", unclassified));
    }

    @Test
    void annotatedHandlers_areNeitherExemptPublicNorTokenExempt() {
        List<String> both = new ArrayList<>();

        for (Handler handler : scanHandlers()) {
            if (handler.annotated() && (isExempt(handler.path()) || isPublic(handler.path()) || handler.tokenExempt())) {
                both.add(handler.label());
            }
        }

        assertTrue(both.isEmpty(),
                "Handlers annotated and also listed as exempt, public or token-exempt:\n" + String.join("\n", both));
    }

    @Test
    void reservedPathHandlers_areAnnotatedOrTokenExempt() {
        List<String> open = new ArrayList<>();
        List<Handler> reserved = scanHandlers().stream().filter(RequiredUserAccessCoverageUnitTest::isReserved).toList();

        for (Handler handler : reserved) {
            if (!handler.annotated() && !handler.tokenExempt()) {
                open.add(handler.label());
            }
        }

        assertFalse(reserved.isEmpty(), "No handler on a token-reserved path found, the scan is broken");
        assertTrue(open.isEmpty(), "Handlers on a token-reserved path that are neither annotated nor token-exempt:\n"
                + String.join("\n", open));
    }

    @Test
    void tokenExemptEndpoints_eachMatchAnUnannotatedHandler() {
        List<Handler> handlers = scanHandlers();
        List<String> orphans = new ArrayList<>();

        for (ApiTokenEndpoint endpoint : tokenExemptEndpoints()) {
            boolean matched = handlers.stream()
                    .anyMatch(handler -> !handler.annotated() && PATH_MATCHER.match(endpoint.pathPattern(), handler.path())
                            && handler.httpMethods().stream().anyMatch(method -> method.name().equals(endpoint.method().name())));
            if (!matched) {
                orphans.add(endpoint.method() + " " + endpoint.pathPattern());
            }
        }

        assertTrue(orphans.isEmpty(), "Token-exempt endpoints that match no unannotated handler:\n"
                + String.join("\n", orphans));
    }

    @Test
    void securityFilterChainBeans_areDeclaredOnlyInTheSecurityPackage() {
        List<String> chains = new ArrayList<>();
        List<String> outside = new ArrayList<>();

        for (Class<?> component : scanClasses(new AnnotationTypeFilter(Component.class))) {
            for (Method method : component.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class) && SecurityFilterChain.class.isAssignableFrom(method.getReturnType())) {
                    String label = component.getName() + "#" + method.getName();
                    chains.add(label);
                    if (!component.getName().startsWith(SECURITY_PACKAGE)) {
                        outside.add(label);
                    }
                }
            }
        }

        assertTrue(chains.size() >= 3, "Fewer than the three security chains found, the scan is broken: " + chains);
        assertTrue(outside.isEmpty(), "Security chains declared outside " + SECURITY_PACKAGE + ":\n" + String.join("\n", outside));
    }

    @Test
    void apiTokenKindHandlers_areDeclaredOnlyInTheSecurityPackage() {
        List<Class<?>> kindHandlers = scanClasses(new AssignableTypeFilter(ApiTokenKindHandler.class));
        List<String> outside = kindHandlers.stream()
                .map(Class::getName)
                .filter(name -> !name.startsWith(SECURITY_PACKAGE))
                .toList();

        assertFalse(kindHandlers.isEmpty(), "No token kind handler found, the scan is broken");
        assertTrue(outside.isEmpty(), "Token kind handlers declared outside " + SECURITY_PACKAGE + ":\n" + String.join("\n", outside));
    }

    @Test
    void exemptPaths_eachMatchAnUnannotatedHandler() {
        List<Handler> handlers = scanHandlers();
        List<String> orphans = new ArrayList<>();

        for (String exemptPath : SecurityConstant.ORGANIZATION_EXEMPT_PATHS) {
            boolean matched = handlers.stream()
                    .anyMatch(handler -> !handler.annotated() && PATH_MATCHER.match(exemptPath, handler.path()));
            if (!matched) {
                orphans.add(exemptPath);
            }
        }

        assertTrue(orphans.isEmpty(), "ORGANIZATION_EXEMPT_PATHS entries that match no unannotated handler:\n"
                + String.join("\n", orphans));
    }

    @Test
    void messageMappingHandlers_carryNoRequiredUserAccess() {
        List<String> offenders = new ArrayList<>();
        int messageHandlers = 0;

        for (Class<?> controller : scanControllers(Controller.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(MessageMapping.class)) {
                    continue;
                }
                messageHandlers++;
                if (method.isAnnotationPresent(RequiredUserAccess.class)) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(messageHandlers > 0, "No @MessageMapping handler was found, the scan is broken");
        assertTrue(offenders.isEmpty(), "STOMP handlers carry no HTTP access rule:\n" + String.join("\n", offenders));
    }

    @Test
    void scan_findsAnnotatedExemptTokenExemptAndIntegrationHandlers() {
        List<Handler> handlers = scanHandlers();

        assertTrue(handlers.stream().anyMatch(Handler::annotated), "No annotated handler found, the scan is broken");
        assertTrue(handlers.stream().anyMatch(handler -> !handler.annotated() && isExempt(handler.path())),
                "No exempt handler found");
        assertTrue(handlers.stream().anyMatch(Handler::tokenExempt), "No token-exempt handler found");
        assertTrue(handlers.stream().anyMatch(RequiredUserAccessCoverageUnitTest::isIntegration),
                "No integration-chain handler found");
        assertFalse(handlers.stream().filter(RequiredUserAccessCoverageUnitTest::isIntegration).anyMatch(Handler::annotated),
                "An integration-chain handler is annotated; its chain answers for it, not the role check");
    }

    private static List<ApiTokenEndpoint> tokenExemptEndpoints() {
        return SecurityConstant.KAYA_MACHINE_TOKEN_EXEMPT_ENDPOINTS;
    }

    private static boolean isReserved(Handler handler) {
        return SecurityConstant.KAYA_MACHINE_TOKEN_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, handler.path()));
    }

    private static boolean isIntegration(Handler handler) {
        return Arrays.stream(SecurityConstant.INTEGRATION_CHAIN_PATHS)
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, handler.path()));
    }

    private static boolean isExempt(String path) {
        return Arrays.stream(SecurityConstant.ORGANIZATION_EXEMPT_PATHS).anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static boolean isPublic(String path) {
        return Arrays.stream(SecurityConstant.PUBLIC_PATHS).anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static List<Handler> scanHandlers() {
        List<Handler> handlers = new ArrayList<>();
        for (Class<?> controller : scanControllers(RestController.class)) {
            List<String> classPaths = pathsOf(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                List<RequestMethod> httpMethods = List.of(mapping.method());
                for (String classPath : classPaths) {
                    for (String methodPath : pathsOf(mapping)) {
                        handlers.add(new Handler(method, join(classPath, methodPath), httpMethods));
                    }
                }
            }
        }
        assertFalse(handlers.isEmpty(), "No controller method was found, the scan is broken");
        return handlers;
    }

    private static List<String> pathsOf(RequestMapping mapping) {
        if (mapping == null) {
            return List.of("");
        }
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length == 0 ? List.of("") : List.of(paths);
    }

    private static String join(String classPath, String methodPath) {
        String joined = classPath + methodPath;
        if (joined.isEmpty()) {
            return "/";
        }
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    private static List<Class<?>> scanControllers(Class<? extends java.lang.annotation.Annotation> annotation) {
        return scanClasses(new AnnotationTypeFilter(annotation));
    }

    private static List<Class<?>> scanClasses(TypeFilter filter) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(filter);
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned class cannot be loaded: " + definition.getBeanClassName(), e);
            }
            // A class declared inside the test sources is a fixture, not part of the application.
            if (!controller.getProtectionDomain().getCodeSource().getLocation().toString().contains("/test-classes/")) {
                controllers.add(controller);
            }
        }
        return controllers;
    }
}
