package com.korur.oba.dojo.security.interceptor;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.MessageMapping;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.apps.kaya.core.security.constant.KayaSecurityConstant;
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.security.constant.SecurityConstant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves, without a database, that every controller method reachable with a user JWT is classified:
 * annotated with {@link RequiredUserAccess}, listed as organization-exempt, listed as public, or
 * served by a machine chain. The interceptor refuses an unclassified method at run time; this test
 * moves that failure to the build. It scans the controller classes by reflection and merges the class
 * and method mapping paths, so no Spring context starts.
 */
class RequiredUserAccessCoverageUnitTest {

    private static final String BASE_PACKAGE = "com.korur.oba";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** One mapped controller method with the merged path of one of its mappings. */
    private record Handler(Method method, String path) {

        boolean annotated() {
            return method.isAnnotationPresent(RequiredUserAccess.class);
        }

        String label() {
            return method.getDeclaringClass().getSimpleName() + "#" + method.getName() + " " + path;
        }
    }

    @Test
    void userChainHandlers_areAnnotatedExemptPublicOrMachine() {
        List<String> unclassified = new ArrayList<>();

        for (Handler handler : scanHandlers()) {
            if (isMachine(handler) || handler.annotated()) {
                continue;
            }
            if (!isExempt(handler.path()) && !isPublic(handler.path())) {
                unclassified.add(handler.label());
            }
        }

        assertTrue(unclassified.isEmpty(),
                "Handlers with no access rule, annotated nowhere and listed nowhere:\n" + String.join("\n", unclassified));
    }

    @Test
    void annotatedHandlers_areNeitherExemptNorPublic() {
        List<String> both = new ArrayList<>();

        for (Handler handler : scanHandlers()) {
            if (handler.annotated() && (isExempt(handler.path()) || isPublic(handler.path()))) {
                both.add(handler.label());
            }
        }

        assertTrue(both.isEmpty(), "Handlers annotated and also listed as exempt or public:\n" + String.join("\n", both));
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
    void scan_findsAnnotatedExemptAndMachineHandlers() {
        List<Handler> handlers = scanHandlers();

        assertTrue(handlers.stream().anyMatch(Handler::annotated), "No annotated handler found, the scan is broken");
        assertTrue(handlers.stream().anyMatch(handler -> !handler.annotated() && isExempt(handler.path())),
                "No exempt handler found");
        assertTrue(handlers.stream().anyMatch(RequiredUserAccessCoverageUnitTest::isMachine),
                "No machine-chain handler found");
        assertFalse(handlers.stream().filter(RequiredUserAccessCoverageUnitTest::isMachine).anyMatch(Handler::annotated),
                "A machine-chain handler is annotated; its chain never reaches the interceptor");
    }

    private static boolean isMachine(Handler handler) {
        return Stream.concat(Arrays.stream(KayaSecurityConstant.MACHINE_TOKEN_PATHS),
                        Stream.of(AlertmanagerConstant.ALERTMANAGER_WEBHOOK_PATH_PATTERN))
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
                for (String classPath : classPaths) {
                    for (String methodPath : pathsOf(mapping)) {
                        handlers.add(new Handler(method, join(classPath, methodPath)));
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
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned controller cannot be loaded: " + definition.getBeanClassName(), e);
            }
            // A controller declared inside the test sources is a fixture, not part of the application.
            if (!controller.getProtectionDomain().getCodeSource().getLocation().toString().contains("/test-classes/")) {
                controllers.add(controller);
            }
        }
        return controllers;
    }
}
