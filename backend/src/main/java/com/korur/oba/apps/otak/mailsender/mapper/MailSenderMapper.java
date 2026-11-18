package com.korur.oba.apps.otak.mailsender.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;

/**
 * Maps mail sender entities to DTOs. A sender has no association, so the mapper needs no
 * helper; the service builds the entity, because every field of it comes from the checked
 * request context or from Microsoft Graph rather than from the request body as is.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MailSenderMapper {

    /**
     * Maps a mail sender to a list item.
     */
    MailSenderListResponse toListResponse(MailSender mailSender);
}
