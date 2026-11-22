package com.korur.oba.shrine.document.mapper.document;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.shrine.document.constant.document.DocumentConstant;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.dto.response.document.DocumentListResponse;
import com.korur.oba.shrine.document.dto.response.document.embedded.DocumentSnapshotEmbeddedResponse;
import com.korur.oba.shrine.document.dto.response.document.embedded.DocumentUserEmbeddedResponse;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

/**
 * Builds the document responses with the values other domains own: the relation name
 * ({@link RelationQueryService}), the legal entity short name ({@link LegalEntityQueryService}),
 * the last editor's display name ({@link UserQueryService}) and the snapshots
 * ({@link AttachmentQueryService}). Every value is read in one batch per page, never once per
 * row, and through the owning domain's query service, never its repository.
 *
 * Callers: {@link com.korur.oba.shrine.document.service.document.impl.DocumentQueryServiceImpl}
 * for reads and {@link com.korur.oba.shrine.document.service.document.impl.DocumentServiceImpl}
 * for the response of every write. The caller has checked access to the owners first.
 */
@Component
@RequiredArgsConstructor
public class DocumentMapperHelper {

    private final DocumentMapper documentMapper;
    private final RelationQueryService relationQueryService;
    private final LegalEntityQueryService legalEntityQueryService;
    private final UserQueryService userQueryService;
    private final AttachmentQueryService attachmentQueryService;

    /**
     * Maps a page of documents to list items with five reads in all: relation names, legal
     * entities, editors and snapshot counts, each once for the whole page.
     *
     * @param organizationId the organization of the request
     * @param documents      the documents of the page
     * @return the list items, in the order of the documents
     */
    public List<DocumentListResponse> toListResponses(UUID organizationId, List<Document> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> relationNames = relationNames(organizationId, documents);
        Map<UUID, String> shortNames = legalEntityShortNames(organizationId);
        Map<UUID, String> editors = displayNames(documents.stream().map(Document::getUpdatedById)
                .collect(Collectors.toSet()));
        Map<UUID, Long> snapshotCounts = attachmentQueryService.countAttachmentsByDocuments(organizationId,
                documents.stream().map(Document::getId).toList());
        return documents.stream()
                .map(document -> documentMapper.toListResponse(document,
                        relationNames.get(document.getEntityId()),
                        shortNames.get(document.getLegalEntityId()),
                        snapshotCounts.getOrDefault(document.getId(), 0L),
                        new DocumentUserEmbeddedResponse(document.getUpdatedById(),
                                editors.get(document.getUpdatedById()))))
                .toList();
    }

    /**
     * Maps one document to its detail, with its snapshots and the outreach title of each.
     *
     * @param organizationId the organization of the request
     * @param document       the document
     * @return the detail
     */
    public DocumentDetailResponse toDetailResponse(UUID organizationId, Document document) {
        String relationName = relationNames(organizationId, List.of(document)).get(document.getEntityId());
        String shortName = legalEntityQueryService.getEntity(organizationId, document.getLegalEntityId())
                .getShortName();
        String editor = displayNames(Set.of(document.getUpdatedById())).get(document.getUpdatedById());
        return documentMapper.toDetailResponse(document, relationName, shortName,
                new DocumentUserEmbeddedResponse(document.getUpdatedById(), editor),
                snapshots(organizationId, document));
    }

    /**
     * Reads the names of the owning relations of the documents in one query.
     *
     * @param organizationId the organization of the request
     * @param documents      the documents
     * @return the name per relation id
     */
    public Map<UUID, String> relationNames(UUID organizationId, Collection<Document> documents) {
        Set<UUID> relationIds = documents.stream()
                .map(documentMapper::relationIdOf)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        return relationQueryService.getNames(organizationId, relationIds);
    }

    /**
     * Reads the outreach title of every mail of the document's relation, the mails its
     * snapshots can sit on, in one query.
     *
     * @param organizationId the organization of the request
     * @param document       the document
     * @return the outreach title per mail id; empty for a document not owned by a relation
     */
    public Map<UUID, String> outreachTitles(UUID organizationId, Document document) {
        UUID relationId = documentMapper.relationIdOf(document);
        return relationId == null ? Map.of() : relationQueryService.getMailOutreachTitles(organizationId, relationId);
    }

    /**
     * The snapshots of a document, newest first, each with the outreach title of its mail.
     */
    private List<DocumentSnapshotEmbeddedResponse> snapshots(UUID organizationId, Document document) {
        List<AttachmentResponse> attachments = attachmentQueryService.listAttachmentsByDocument(organizationId,
                document.getId());
        if (attachments.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> titles = outreachTitles(organizationId, document);
        return attachments.stream()
                .map(attachment -> new DocumentSnapshotEmbeddedResponse(attachment.id(), attachment.entityId(),
                        titles.get(attachment.entityId()), attachment.createdAt()))
                .toList();
    }

    /**
     * The short name of every legal entity of the organization, one read; an organization
     * has a handful of legal entities, so the whole list is cheaper than a lookup per id.
     */
    private Map<UUID, String> legalEntityShortNames(UUID organizationId) {
        return legalEntityQueryService.list(organizationId).stream()
                .collect(Collectors.toMap(LegalEntityListResponse::id, LegalEntityListResponse::shortName));
    }

    /**
     * The display name of each given user found: first and last name, or the email when the
     * profile has no name. A user that no longer exists is absent.
     */
    private Map<UUID, String> displayNames(Set<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        for (User user : userQueryService.getUserEntitiesByIds(List.copyOf(userIds))) {
            names.put(user.getId(), displayNameOf(user));
        }
        return names;
    }

    private static String displayNameOf(User user) {
        UserProfile profile = user.getProfile();
        String firstName = profile == null || profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile == null || profile.getLastName() == null ? "" : profile.getLastName().trim();
        String name = (firstName + DocumentConstant.NAME_SEPARATOR + lastName).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
