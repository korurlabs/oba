package com.korur.oba.apps.kaya.session.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.dto.projection.SessionCountProjection;
import com.korur.oba.apps.kaya.session.dto.projection.SessionSubscriptionProjection;
import com.korur.oba.apps.kaya.session.dto.response.SessionCountSummaryResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionDetailResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionFiltersResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionHandoffSummaryResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionLinkResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionListResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionSkillUseResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionSubagentResponse;
import com.korur.oba.apps.kaya.session.dto.response.SessionUpdatedStreamResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterMachineResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterSubscriptionResponse;
import com.korur.oba.apps.kaya.session.dto.response.embedded.SessionFilterUserResponse;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.entity.SessionEvent;
import com.korur.oba.apps.kaya.session.entity.SessionHandoffSnapshot;
import com.korur.oba.apps.kaya.session.entity.SessionLink;
import com.korur.oba.apps.kaya.session.entity.SessionSkillUse;
import com.korur.oba.apps.kaya.session.entity.SessionSubagent;
import com.korur.oba.apps.kaya.session.helper.HandoffStatusParser;

/**
 * Maps sessions and their child rows to responses. The names of the repository, user and machine
 * come from other domains and are passed in by the caller, which resolves them for a whole page.
 */
@Mapper(componentModel = "spring", imports = HandoffStatusParser.class)
public interface SessionMapper {

    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "createdAt", source = "session.createdAt")
    @Mapping(target = "links", source = "links")
    SessionListResponse toListResponse(Session session, String repositoryName, String userDisplayName, String machineName,
            String identityConflictUserDisplayName, List<SessionLink> links);

    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "createdAt", source = "session.createdAt")
    @Mapping(target = "updatedAt", source = "session.updatedAt")
    @Mapping(target = "links", source = "links")
    @Mapping(target = "subagents", source = "subagents")
    @Mapping(target = "skillUses", source = "skillUses")
    @Mapping(target = "handoffs", source = "handoffs")
    @Mapping(target = "releaseNumber", source = "releaseNumber")
    SessionDetailResponse toDetailResponse(Session session, String repositoryName, String userDisplayName,
            String machineName, String identityConflictUserDisplayName, List<SessionLink> links,
            List<SessionSubagent> subagents, List<SessionSkillUse> skillUses, List<SessionHandoffSnapshot> handoffs,
            Integer releaseNumber);

    SessionLinkResponse toLinkResponse(SessionLink link);

    List<SessionLinkResponse> toLinkResponses(List<SessionLink> links);

    SessionSubagentResponse toSubagentResponse(SessionSubagent subagent);

    SessionSkillUseResponse toSkillUseResponse(SessionSkillUse skillUse);

    @Mapping(target = "status", expression = "java(HandoffStatusParser.statusOf(snapshot.getContent()))")
    SessionHandoffSummaryResponse toHandoffSummaryResponse(SessionHandoffSnapshot snapshot);

    @Mapping(target = "status", expression = "java(HandoffStatusParser.statusOf(snapshot.getContent()))")
    SessionHandoffResponse toHandoffResponse(SessionHandoffSnapshot snapshot);

    List<SessionHandoffResponse> toHandoffResponses(List<SessionHandoffSnapshot> snapshots);

    SessionCountSummaryResponse toCountSummaryResponse(SessionCountProjection projection);

    SessionFilterUserResponse toFilterUserResponse(UUID id, String displayName);

    SessionFilterMachineResponse toFilterMachineResponse(UUID id, String machineName, UUID userId);

    SessionFilterSubscriptionResponse toFilterSubscriptionResponse(SessionSubscriptionProjection projection);

    List<SessionFilterSubscriptionResponse> toFilterSubscriptionResponses(List<SessionSubscriptionProjection> projections);

    SessionFiltersResponse toFiltersResponse(List<SessionFilterUserResponse> users, List<SessionFilterMachineResponse> machines,
            List<String> models, List<SessionFilterSubscriptionResponse> subscriptions);

    SessionUpdatedStreamResponse toUpdatedStreamResponse(String sessionId, Instant lastEventAt);

    /**
     * An event as the recorder wrote it: the payload with seq, at, event and agent_id put back,
     * the shape the session view replays.
     */
    default Map<String, Object> toEventResponse(SessionEvent event) {
        Map<String, Object> map = new HashMap<>(event.getPayload());
        map.put(SessionConstant.EVENT_SEQ, event.getSeq());
        map.put(SessionConstant.EVENT_AT, event.getAt().toString());
        map.put(SessionConstant.EVENT_NAME, event.getEvent());
        if (event.getAgentId() != null) {
            map.put(SessionConstant.EVENT_AGENT_ID, event.getAgentId());
        }
        return map;
    }

    default List<Map<String, Object>> toEventResponses(List<SessionEvent> events) {
        return events.stream().map(this::toEventResponse).toList();
    }
}
