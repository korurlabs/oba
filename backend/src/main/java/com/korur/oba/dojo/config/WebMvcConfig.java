package com.korur.oba.dojo.config;

// 1. Framework-level
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor;
import com.korur.oba.dojo.security.resolver.GetUserArgumentResolver;

/**
 * Configuration for Spring MVC customizations.
 *
 * Registers the GetUser argument resolver and the organization access check of every controller
 * method.
 *
 * @see GetUserArgumentResolver
 * @see RequiredUserAccessInterceptor
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final GetUserArgumentResolver getUserArgumentResolver;
    private final RequiredUserAccessInterceptor requiredUserAccessInterceptor;

    /**
     * Registers {@link GetUserArgumentResolver} for parameters annotated with GetUser.
     *
     * @param resolvers the list of argument resolvers to add to
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(getUserArgumentResolver);
    }

    /**
     * Registers {@link RequiredUserAccessInterceptor} for every path. It is default-deny, so a
     * handler of com.korur.oba without an access rule is refused rather than left open.
     *
     * @param registry the interceptor registry to add to
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiredUserAccessInterceptor);
    }
}
