package com.korur.oba.apps.kaya.delivery;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;

/**
 * Where the Kaya web app runs, so that a proposal made from the CLI answers with the page its
 * reviewers open. Without the property the production host is used.
 *
 * @param webUrl the web app's origin, without a trailing path
 */
@ConfigurationProperties(prefix = "kaya.delivery")
public record DeliveryProposalProperties(
        @DefaultValue(DeliveryConstant.WEB_URL_DEFAULT)
        String webUrl
) {
}
