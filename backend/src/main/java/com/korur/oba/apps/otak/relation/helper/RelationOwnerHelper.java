package com.korur.oba.apps.otak.relation.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.response.RelationOwnerResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Resolves the owners of relations to the id and name the client shows. The owner
 * is stored as a plain user id, so the names are read once per page of relations
 * instead of once per relation.
 */
@Component
@RequiredArgsConstructor
public class RelationOwnerHelper {

    private final UserQueryService userQueryService;

    /**
     * Reads the display names of the owners of the given relations.
     *
     * @param relations the relations whose owners are named
     * @return the display name per owner user id; empty when no relation has an owner
     */
    public Map<UUID, String> ownerNames(Collection<Relation> relations) {
        List<UUID> ownerIds = relations.stream()
                .map(Relation::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return userQueryService.getUserEntitiesByIds(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, RelationOwnerHelper::displayNameOf));
    }

    /**
     * Builds the owner of one relation from names read before.
     *
     * @param relation   the relation
     * @param ownerNames the display name per owner user id
     * @return the owner, or null when the relation has none
     */
    public RelationOwnerResponse ownerOf(Relation relation, Map<UUID, String> ownerNames) {
        UUID ownerUserId = relation.getOwnerUserId();
        if (ownerUserId == null) {
            return null;
        }
        return new RelationOwnerResponse(ownerUserId, ownerNames.get(ownerUserId));
    }

    /**
     * Builds the owner of a single relation, reading its name.
     *
     * @param relation the relation
     * @return the owner, or null when the relation has none
     */
    public RelationOwnerResponse ownerOf(Relation relation) {
        return ownerOf(relation, ownerNames(List.of(relation)));
    }

    /**
     * The profile's first and last name, or the sign-in email while the profile holds
     * no name yet, as for a user invited before the first sign-in.
     */
    private static String displayNameOf(User user) {
        UserProfile profile = user.getProfile();
        String firstName = profile == null || profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile == null || profile.getLastName() == null ? "" : profile.getLastName().trim();
        String name = (firstName + UserConstant.FULL_NAME_SEPARATOR + lastName).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
