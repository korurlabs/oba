package com.korur.oba.apps.otak.mailtemplate.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateCreateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateListResponse;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;

/**
 * Maps a {@link MailTemplateCreateRequest} to a new {@link MailTemplate}, and a mail
 * template to a {@link MailTemplateListResponse} or a {@link MailTemplateDetailResponse}.
 * MapStruct generates the implementation as a Spring bean with constructor injection.
 *
 * A mail template has no association to another entity (its organization is a plain id),
 * so the mapper needs no MapperHelper.
 *
 * There is no update mapping: the update writes the trimmed name and the content itself, in
 * {@link com.korur.oba.apps.otak.mailtemplate.service.impl.MailTemplateServiceImpl}, the same
 * way the create does, so both store the columns alike.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MailTemplateMapper {

    /**
     * Maps a create request to a new, unsaved entity. The service then writes the trimmed
     * name and sets the organization from the checked request header, never from the body.
     * The id and the timestamps are set by the database and JPA.
     *
     * @param request the validated create request
     * @return a new entity without id or organization
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    MailTemplate toEntity(MailTemplateCreateRequest request);

    /**
     * Maps a mail template to a list item: the name, language, subject and last change,
     * without the body.
     *
     * @param mailTemplate the stored mail template
     * @return the list item
     */
    MailTemplateListResponse toListResponse(MailTemplate mailTemplate);

    /**
     * Maps a mail template to the detail response, with every field.
     *
     * @param mailTemplate the stored mail template
     * @return the detail response
     */
    MailTemplateDetailResponse toDetailResponse(MailTemplate mailTemplate);
}
