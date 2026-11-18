package com.korur.oba.apps.kaya.render.dto.embedded;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A file that {@link com.korur.oba.apps.kaya.render.service.RenderService#parse} would refuse, as
 * {@link com.korur.oba.apps.kaya.render.service.RenderQueryService#screen} reports it.
 *
 * @param path      the delivered path
 * @param errorCode KAY_CFG_027 for INVALID_FILE or KAY_CFG_028 for NOT_DELIVERABLE
 * @param reason    what is wrong, in words
 */
public record FileRefusal(
        String path,
        String errorCode,
        String reason
) {
}
