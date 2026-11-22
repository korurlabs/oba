package com.korur.oba.apps.kalkan.page.mapper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.constant.KalkanPageConstant;
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageTitleProjection;
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageTranslation;
import com.korur.oba.apps.kalkan.page.enums.KalkanLocale;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Sets the relations {@link KalkanPageMapper} leaves out: the organization, parent and root of a
 * page, the page of a translation, and the acting user; and resolves the names and paths the
 * responses show.
 */
@Component
@RequiredArgsConstructor
public class KalkanPageMapperHelper {

    private final UserQueryService userQueryService;

    // -------------Relations-----------//

    /**
     * Builds a new page under a parent, or a new root page when the parent is null. A child
     * carries its parent's root, so access to it is one lookup.
     *
     * @param organizationId the organization of the request
     * @param parent         the live parent; null for a root page
     * @param slug           the slug
     * @param position       the order among the siblings
     * @param compliance     the compliance mark
     * @param userId         the creating user
     * @return the unsaved page
     */
    public KalkanPage newPage(UUID organizationId, KalkanPage parent, String slug, int position, boolean compliance,
                              UUID userId) {
        return KalkanPage.builder()
                .organizationId(organizationId)
                .parent(parent)
                .rootId(parent == null ? null : parent.getAccessRootId())
                .slug(slug)
                .position(position)
                .compliance(compliance)
                .createdById(userId)
                .updatedById(userId)
                .build();
    }

    /**
     * Sets the page and the creating user of a new translation.
     *
     * @param translation the translation from {@link KalkanPageMapper#toTranslationEntity}
     * @param page        its page
     * @param userId      the creating user
     */
    public void setCreateRelations(KalkanPageTranslation translation, KalkanPage page, UUID userId) {
        translation.setPage(page);
        translation.setCreatedById(userId);
        translation.setUpdatedById(userId);
    }

    /**
     * Moves a page under a new parent and its root.
     *
     * @param page   the page
     * @param parent the new live parent
     */
    public void setParent(KalkanPage page, KalkanPage parent) {
        page.setParent(parent);
        page.setRootId(parent.getAccessRootId());
    }

    // -------------Display-----------//

    /**
     * The path of slugs from the root to the page. The ancestors are loaded with the page, so this
     * runs no query.
     *
     * @param page a live page
     * @return the slugs joined by a slash
     */
    public String pathOf(KalkanPage page) {
        List<String> slugs = new ArrayList<>();
        for (KalkanPage current = page; current != null; current = current.getParent()) {
            slugs.add(current.getSlug());
        }
        Collections.reverse(slugs);
        return String.join(KalkanPageConstant.PATH_SEPARATOR, slugs);
    }

    /**
     * The number of levels from the root to the page, a root counting as one.
     *
     * @param page a live page
     * @return the page's depth
     */
    public int depthOf(KalkanPage page) {
        int depth = 0;
        for (KalkanPage current = page; current != null; current = current.getParent()) {
            depth++;
        }
        return depth;
    }

    /**
     * The display names of users in one query: the full name, else the email, as the other apps
     * show members.
     *
     * @param userIds the users
     * @return the name per user found
     */
    public Map<UUID, String> displayNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (User user : userQueryService.getUserEntitiesByIds(userIds.stream().distinct().toList())) {
            names.put(user.getId(), displayNameOf(user));
        }
        return names;
    }

    /**
     * Groups title rows by page, each page's titles in locale order.
     *
     * @param rows the title rows of one or more pages
     * @return the titles per page that has at least one
     */
    public Map<UUID, Map<KalkanLocale, String>> titlesByPage(Collection<KalkanPageTitleProjection> rows) {
        Map<UUID, Map<KalkanLocale, String>> titles = new HashMap<>();
        for (KalkanPageTitleProjection row : rows) {
            titles.computeIfAbsent(row.getPageId(), id -> new EnumMap<>(KalkanLocale.class))
                    .put(row.getLocale(), row.getTitle());
        }
        return titles;
    }

    /**
     * The one title a list shows for a page: the one in the requested locale, else the first in
     * locale order, so a page written in one language only still shows a title.
     *
     * @param titles the page's titles in locale order; null when it has none
     * @param locale the requested locale; null to take the first title
     * @return the title; null when the page has no title
     */
    public String titleIn(Map<KalkanLocale, String> titles, KalkanLocale locale) {
        if (titles == null || titles.isEmpty()) {
            return null;
        }
        if (locale != null && titles.containsKey(locale)) {
            return titles.get(locale);
        }
        return titles.values().iterator().next();
    }

    private static String displayNameOf(User user) {
        UserProfile profile = user.getProfile();
        String firstName = profile == null || profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile == null || profile.getLastName() == null ? "" : profile.getLastName().trim();
        String name = (firstName + KalkanPageConstant.NAME_SEPARATOR + lastName).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
