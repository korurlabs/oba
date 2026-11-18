package com.korur.oba.shrine.access.controller.unit;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

// 3. Java SDK
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.access.controller.AppGrantController;
import com.korur.oba.shrine.access.controller.OrganizationGroupController;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves by reflection that every request handler of the group and app grant controllers demands the
 * organization ADMIN role. A handler added without the annotation, or with a lower role, fails the build.
 */
class AccessControllersAdminRoleUnitTest {

    private static final List<Class<?>> CONTROLLERS =
            List.of(OrganizationGroupController.class, AppGrantController.class);

    @Test
    void handlers_allCarryRequiredUserAccessAdmin() {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : CONTROLLERS) {
            List<Method> handlers = handlersOf(controller);
            assertFalse(handlers.isEmpty(), controller.getSimpleName() + " has no request handler");
            for (Method handler : handlers) {
                RequiredUserAccess access = handler.getAnnotation(RequiredUserAccess.class);
                if (access == null || access.value() != OrganizationMemberRole.ADMIN) {
                    violations.add(controller.getSimpleName() + "#" + handler.getName()
                            + " -> " + (access == null ? "no @RequiredUserAccess" : access.value()));
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Handlers that do not require OrganizationMemberRole.ADMIN:\n" + String.join("\n", violations));
    }

    private static List<Method> handlersOf(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();
    }
}
