package com.korur.oba.apps.otak.mail.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;

/**
 * Maps a {@link MailRequest} or a {@link MailTemplate} to a new {@link Mail}, and writes a
 * request onto a stored mail. MapStruct generates the implementation as a Spring bean with
 * constructor injection.
 *
 * Every field the server owns is ignored: the id and timestamps, the organization and the
 * relation (set by the service from the checked organization and the outreach). The template
 * link is set only by the copy from a template, never by a request.
 *
 * Used by {@link com.korur.oba.apps.otak.mail.service.impl.MailServiceImpl}.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MailMapper {

    /**
     * Maps a mail written by hand to a new, unsaved mail without a template link.
     *
     * @param request the validated mail request
     * @return a new entity without id, organization or relation
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "relationId", ignore = true)
    @Mapping(target = "mailTemplateId", ignore = true)
    Mail toEntity(MailRequest request);

    /**
     * Copies the subject, body and language of a mail template into a new, unsaved mail that
     * records the template as its source. The mail is a copy, so a later template edit does
     * not change it.
     *
     * @param mailTemplate the mail template of the organization
     * @return a new entity without id, organization or relation
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "relationId", ignore = true)
    @Mapping(target = "mailTemplateId", source = "id")
    Mail fromTemplate(MailTemplate mailTemplate);

    /**
     * Writes the subject, body and language of a request onto a stored mail, a full update.
     * The organization, the relation and the template link never change.
     *
     * @param mail    the stored mail, changed in place
     * @param request the validated mail request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "relationId", ignore = true)
    @Mapping(target = "mailTemplateId", ignore = true)
    void updateEntity(@MappingTarget Mail mail, MailRequest request);
}
