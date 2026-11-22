package com.korur.oba.shrine.document.mapper.document;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.shrine.document.dto.response.document.DocumentDetailResponse;
import com.korur.oba.shrine.document.dto.response.document.DocumentListResponse;
import com.korur.oba.shrine.document.dto.response.document.embedded.DocumentSnapshotEmbeddedResponse;
import com.korur.oba.shrine.document.dto.response.document.embedded.DocumentUserEmbeddedResponse;
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;

/**
 * MapStruct mapper from {@link Document} to its responses. The values read from other
 * domains (relation name, legal entity short name, last editor, snapshot count and the
 * snapshots) come in as parameters, resolved in batches by {@link DocumentMapperHelper}, so
 * the mapper itself reads nothing. The stored blob URLs are signed on the way out through
 * {@link BlobUrlRewriterMapper}.
 *
 * @see DocumentMapperHelper
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = { BlobUrlRewriterMapper.class })
public interface DocumentMapper {

    /**
     * Maps a document to a list item.
     *
     * @param document             the document
     * @param relationName         the name of the owning relation, or null
     * @param legalEntityShortName the short name of the issuing legal entity
     * @param snapshotCount        the number of PDF snapshots
     * @param updatedBy            the last editor
     * @return the list item
     */
    @Mapping(target = "id", source = "document.id")
    @Mapping(target = "relationId", source = "document", qualifiedByName = "relationIdOf")
    @Mapping(target = "relationName", source = "relationName")
    @Mapping(target = "legalEntityShortName", source = "legalEntityShortName")
    @Mapping(target = "snapshotCount", source = "snapshotCount")
    @Mapping(target = "updatedBy", source = "updatedBy")
    DocumentListResponse toListResponse(Document document, String relationName, String legalEntityShortName,
                                        long snapshotCount, DocumentUserEmbeddedResponse updatedBy);

    /**
     * Maps a document to its detail, with signed file URLs and its snapshots.
     *
     * @param document             the document
     * @param relationName         the name of the owning relation, or null
     * @param legalEntityShortName the short name of the issuing legal entity
     * @param updatedBy            the last editor
     * @param snapshots            the PDF snapshots, newest first; their number is the snapshot count
     * @return the detail
     */
    @Mapping(target = "id", source = "document.id")
    @Mapping(target = "relationId", source = "document", qualifiedByName = "relationIdOf")
    @Mapping(target = "relationName", source = "relationName")
    @Mapping(target = "legalEntityShortName", source = "legalEntityShortName")
    @Mapping(target = "snapshotCount", source = "snapshots", qualifiedByName = "countSnapshots")
    @Mapping(target = "updatedBy", source = "updatedBy")
    @Mapping(target = "snapshots", source = "snapshots")
    @Mapping(target = "htmlUrl", source = "document.htmlBlobUrl", qualifiedByName = "rewriteBlobUrl")
    @Mapping(target = "pdfUrl", source = "document.pdfBlobUrl", qualifiedByName = "rewriteBlobUrl")
    DocumentDetailResponse toDetailResponse(Document document, String relationName, String legalEntityShortName,
                                            DocumentUserEmbeddedResponse updatedBy,
                                            List<DocumentSnapshotEmbeddedResponse> snapshots);

    /**
     * The owning relation: the owner id when the owner is a relation, null for any other kind.
     *
     * @param document the document
     * @return the relation id, or null
     */
    @Named("relationIdOf")
    default UUID relationIdOf(Document document) {
        return document.getEntityType() == DocumentEntityType.RELATION ? document.getEntityId() : null;
    }

    /**
     * The number of PDF snapshots of a document, so the detail's count always matches the
     * snapshots it lists.
     *
     * @param snapshots the snapshots of the document
     * @return their number
     */
    @Named("countSnapshots")
    default long countSnapshots(List<DocumentSnapshotEmbeddedResponse> snapshots) {
        return snapshots.size();
    }
}
