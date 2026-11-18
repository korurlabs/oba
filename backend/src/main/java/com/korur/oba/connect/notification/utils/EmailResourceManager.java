package com.korur.oba.connect.notification.utils;

// 1. Framework-level
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.korur.oba.connect.notification.constant.NotificationConstant;

// 2. Third-party
import lombok.Getter;

// 3. Java SDK
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Getter
public class EmailResourceManager {

    private static final String CSS_CLASSPATH = NotificationConstant.CSS_CLASSPATH;
    private static final String LOGO_CLASSPATH = NotificationConstant.LOGO_CLASSPATH;

    private final String css;
    private final String logo;

    public EmailResourceManager(ResourceLoader resourceLoader) {
        this.css = loadCss(resourceLoader);
        this.logo = loadLogo();
    }

    private String loadCss(ResourceLoader resourceLoader) {
        try {
            var resource = resourceLoader.getResource(CSS_CLASSPATH);
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                }
            } else {
                return getDefaultCss();
            }
        } catch (IOException ex) {
            return getDefaultCss();
        }
    }

    private String loadLogo() {
        try {
            ClassPathResource logoResource = new ClassPathResource(LOGO_CLASSPATH);
            try (InputStream in = logoResource.getInputStream()) {
                return Base64.getEncoder().encodeToString(StreamUtils.copyToByteArray(in));
            }
        } catch (IOException ex) {
            return null;
        }
    }

    private String getDefaultCss() {
        return """
                /* Sensey Email Templates — Default Styles */
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #555555; background-color: #F5F0E8; padding: 20px; }
                .email-container { max-width: 600px; margin: 0 auto; background-color: #FFFFFF; border-radius: 10px; overflow: hidden; box-shadow: 0 0 10px rgba(0, 0, 0, 0.08); }
                .email-header { background: linear-gradient(135deg, #C75775 0%, #D4728A 100%); color: #FFFFFF; padding: 24px 30px; text-align: center; font-size: 22px; font-weight: bold; }
                .email-body { padding: 30px; font-size: 15px; line-height: 1.6; color: #555555; }
                .email-footer { background-color: #1A1A1A; color: #BBBBBB; padding: 20px; text-align: center; font-size: 13px; }
                .email-footer a { color: #D4728A; text-decoration: none; }
                .button { display: inline-block; padding: 12px 24px; margin-top: 10px; background-color: #C75775; color: #FFFFFF; text-decoration: none; border-radius: 6px; font-weight: bold; }
                .action-link { color: #C75775; text-decoration: underline; font-weight: bold; }
                """;
    }
}
