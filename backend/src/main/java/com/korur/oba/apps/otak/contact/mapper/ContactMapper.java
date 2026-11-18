package com.korur.oba.apps.otak.contact.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.contact.dto.request.ContactCreateRequest;
import com.korur.oba.apps.otak.contact.dto.request.ContactUpdateRequest;
import com.korur.oba.apps.otak.contact.dto.response.ContactDetailResponse;
import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.entity.Contact;

/**
 * MapStruct mapper between {@link Contact} and its request and response records. The
 * generated implementation is a Spring bean with constructor injection.
 *
 * The mapper copies plain fields only. The fields that carry a domain rule are ignored on
 * every request mapping and set by
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl} instead:
 *
 * - organizationId and relationId come from the relation the service read within the
 *   request's organization, never from the request, so a contact can not be written into
 *   another organization or relation;
 * - kind, mainPersonContact and mainBusinessContact are set by the service, because the main
 *   contact rule (one main contact per kind, the first of a kind is main, the previous main
 *   one is unmarked first) needs the stored contacts and an order of writes the mapper can
 *   not see;
 * - id, createdAt and updatedAt belong to JPA and the auditing of the base entity.
 *
 * Used by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl} for create
 * and update, and by {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl}
 * for the list.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ContactMapper {

    /**
     * Maps a contact create request to a new entity: name, role, email, email type, phone,
     * LinkedIn URL, decision maker and source URL.
     *
     * The organization and the relation come from the checked request and the loaded
     * relation in the service. The kind and the main flags are set by the service too,
     * because it unmarks the previous main contact of the kind before it marks this one.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}.
     *
     * @param request the validated create payload
     * @return a new, unsaved contact without organization, relation, kind or main flags
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "relationId", ignore = true)
    @Mapping(target = "kind", ignore = true)
    @Mapping(target = "mainPersonContact", ignore = true)
    @Mapping(target = "mainBusinessContact", ignore = true)
    Contact toEntity(ContactCreateRequest request);

    /**
     * Writes every field of an update request onto the contact; null clears an optional
     * field. This is the full replace of the PUT: the mapper copies each field as sent,
     * with no null-skipping, so a client that omits an optional field clears it.
     *
     * The kind and the main flags are set in the service, which orders a kind change
     * before the flag writes so the unique indexes never see two main contacts of a kind.
     * The source URL is kept: it records where the person was found and is set on create
     * only, so the update request has no such field.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update},
     * after the main flags are settled.
     *
     * @param contact the managed contact to change in place
     * @param request the validated update payload
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "relationId", ignore = true)
    @Mapping(target = "sourceUrl", ignore = true)
    @Mapping(target = "kind", ignore = true)
    @Mapping(target = "mainPersonContact", ignore = true)
    @Mapping(target = "mainBusinessContact", ignore = true)
    void updateEntity(@MappingTarget Contact contact, ContactUpdateRequest request);

    /**
     * Maps a contact to a list item, {@link ContactListResponse}: every field but the
     * timestamps, which no list shows.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl#list}.
     *
     * @param contact the stored contact
     * @return the list item
     */
    ContactListResponse toListResponse(Contact contact);

    /**
     * Maps a contact to the detail response, {@link ContactDetailResponse}: every field,
     * the timestamps included.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}.
     *
     * @param contact the saved contact
     * @return the detail response
     */
    ContactDetailResponse toDetailResponse(Contact contact);
}
