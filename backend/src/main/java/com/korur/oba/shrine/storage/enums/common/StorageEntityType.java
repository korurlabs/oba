package com.korur.oba.shrine.storage.enums.common;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Getter;

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.shrine.access.enums.ResourceAccessType;

/**
 * Entity types that can have attached storage files (attachments or photos).
 * Shared across attachment and photo sub-domains.
 *
 * uniqueContentAcrossType says whether an upload is refused when the same
 * content is already attached to any entity of the type. Chat messages and
 * Kalkan pages keep their own copy per entity instead: a file is sent in chat
 * again as a matter of course, and a Kalkan page holds its own evidence, which
 * cannot be deleted and must not depend on another page of any organization.
 *
 * uniqueContentPerEntity says whether an upload is refused when the same
 * content is already attached to the same entity. A Kalkan page, a mail and a
 * relation keep one copy of a file each: the same file attached twice to one of
 * them would only be sent or listed twice. Another entity of the type may hold
 * the same content.
 *
 * resourceAccessType names the {@link ResourceAccessType} whose access rules
 * guard the attachments of the type. A type without one has no rule that could
 * tie its files to an organization, so its attachments are refused.
 */
@AllArgsConstructor
public enum StorageEntityType {
    ORGANIZATION("ORGANIZATION", true, false, null),
    USER("USER", true, false, null),
    INVOICE("INVOICE", true, false, null),
    TICKET("TICKET", true, false, ResourceAccessType.TICKET),
    CHAT_MESSAGE("CHAT_MESSAGE", false, false, ResourceAccessType.CHAT_MESSAGE),
    KALKAN_PAGE("KALKAN_PAGE", false, true, ResourceAccessType.KALKAN_PAGE),
    MAIL("MAIL", false, true, ResourceAccessType.MAIL),
    RELATION("RELATION", false, true, ResourceAccessType.RELATION);

    @Getter
    private final String type;
    @Getter
    private final boolean uniqueContentAcrossType;
    @Getter
    private final boolean uniqueContentPerEntity;
    private final ResourceAccessType resourceAccessType;

    /**
     * The access type whose rules guard the attachments of this entity type.
     *
     * @return the access type, or empty when attachments of this type are not reachable through storage
     */
    public Optional<ResourceAccessType> findResourceAccessType() {
        return Optional.ofNullable(resourceAccessType);
    }
}
