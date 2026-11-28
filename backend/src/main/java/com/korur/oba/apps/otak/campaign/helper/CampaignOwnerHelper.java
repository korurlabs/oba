package com.korur.oba.apps.otak.campaign.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.response.CampaignOwnerResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Resolves the owners of campaigns to the id and name the client shows. The owner is stored
 * as a plain user id, so the names are read once for the whole list instead of once per
 * campaign.
 */
@Component
@RequiredArgsConstructor
public class CampaignOwnerHelper {

    private final UserQueryService userQueryService;

    /**
     * Reads the display names of the owners of the given campaigns.
     *
     * @param campaigns the campaigns whose owners are named
     * @return the display name per owner user id; empty when no campaign has an owner
     */
    public Map<UUID, String> ownerNames(Collection<Campaign> campaigns) {
        List<UUID> ownerIds = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            UUID ownerUserId = campaign.getOwnerUserId();
            if (ownerUserId != null && !ownerIds.contains(ownerUserId)) {
                ownerIds.add(ownerUserId);
            }
        }
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> ownerNames = new HashMap<>();
        for (User user : userQueryService.getUserEntitiesByIds(ownerIds)) {
            ownerNames.put(user.getId(), displayNameOf(user));
        }
        return ownerNames;
    }

    /**
     * Builds the owner of one campaign from names read before.
     *
     * @param campaign   the campaign
     * @param ownerNames the display name per owner user id
     * @return the owner, or null when the campaign has none
     */
    public CampaignOwnerResponse ownerOf(Campaign campaign, Map<UUID, String> ownerNames) {
        UUID ownerUserId = campaign.getOwnerUserId();
        if (ownerUserId == null) {
            return null;
        }
        return new CampaignOwnerResponse(ownerUserId, ownerNames.get(ownerUserId));
    }

    /**
     * Builds the owner of a single campaign, reading its name.
     *
     * @param campaign the campaign
     * @return the owner, or null when the campaign has none
     */
    public CampaignOwnerResponse ownerOf(Campaign campaign) {
        return ownerOf(campaign, ownerNames(List.of(campaign)));
    }

    /**
     * The profile's first and last name, or the sign-in email while the profile holds no name
     * yet, as for a user invited before the first sign-in.
     */
    private static String displayNameOf(User user) {
        UserProfile profile = user.getProfile();
        String firstName = profile == null || profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile == null || profile.getLastName() == null ? "" : profile.getLastName().trim();
        String name = (firstName + UserConstant.FULL_NAME_SEPARATOR + lastName).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
