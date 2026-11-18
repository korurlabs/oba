package com.korur.oba.dojo.logging.enums.unit;

// 1. Framework-level

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogPrefix;

class LogPrefixUnitTest {

    /**
     * Builds an empty class with the given fully qualified name, because the prefix depends only on
     * the package of the class and the tests must not depend on which production classes exist.
     */
    private static Class<?> classNamed(String fullyQualifiedName) {
        return new ByteBuddy()
                .subclass(Object.class)
                .name(fullyQualifiedName)
                .make()
                .load(LogPrefixUnitTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

    @Nested
    class FromClass {

        @Test
        void kazanAppClass_resolvesAppAndArea() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.apps.kazan.ticket.service.TicketProbeA");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("kazan.ticket", domain);
        }

        @Test
        void kazanAppClassInDeepPackage_resolvesAppAndArea() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.apps.kazan.ticket.service.impl.helper.TicketProbeB");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("kazan.ticket", domain);
        }

        @Test
        void dojoClass_resolvesModuleAndArea() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.dojo.security.SecurityProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("dojo.security", domain);
        }

        @Test
        void connectClass_resolvesModuleAndArea() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.connect.github.service.GithubProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("connect.github", domain);
        }

        @Test
        void shrineClass_resolvesModuleAndArea() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.shrine.identity.service.IdentityProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("shrine.identity", domain);
        }

        @Test
        void classDirectlyUnderApp_resolvesAppAndFirstSegmentAsModule() {
            // Arrange: only two segments after the base, so the apps rule does not apply
            Class<?> clazz = classNamed("com.korur.oba.apps.kazan.AppRootProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("apps.kazan", domain);
        }

        @Test
        void classDirectlyUnderApps_resolvesToAppsModule() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.apps.AppsRootProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("apps", domain);
        }

        @Test
        void classDirectlyUnderModule_resolvesToModuleOnly() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.dojo.DojoRootProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("dojo", domain);
        }

        @Test
        void classInBasePackage_returnsUnknown() {
            // Arrange: the package equals the base without the trailing dot
            Class<?> clazz = classNamed("com.korur.oba.BaseProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("unknown", domain);
        }

        @Test
        void classOutsideBasePackage_returnsUnknown() {
            // Arrange
            Class<?> clazz = classNamed("com.example.ForeignProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("unknown", domain);
        }

        @Test
        void legacyBasePackage_returnsUnknown() {
            // Arrange
            Class<?> clazz = classNamed("com.sensey.kazan.domain.ticket.LegacyProbe");

            // Act
            String domain = LogPrefix.fromClass(clazz).getDomain();

            // Assert
            assertEquals("unknown", domain);
        }

        @Test
        void sameClassTwice_returnsCachedInstance() {
            // Arrange
            Class<?> clazz = classNamed("com.korur.oba.dojo.logging.CacheProbe");

            // Act
            LogPrefix first = LogPrefix.fromClass(clazz);
            LogPrefix second = LogPrefix.fromClass(clazz);

            // Assert
            assertSame(first, second);
        }
    }
}
