package com.korur.oba.apps.kazan.milestone.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.dto.projection.MilestoneDependencyProjection;
import com.korur.oba.apps.kazan.milestone.dto.projection.MilestoneTicketCountProjection;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDependencyResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kazan.milestone.mapper.MilestoneMapper;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;

/**
 * Builds milestone responses with their ticket progress, health and dependencies.
 *
 * Counts and dependencies each come from a fixed number of queries per call,
 * never one per milestone, so a roadmap of many milestones costs the same
 * round trips as a single milestone.
 */
@Component
@RequiredArgsConstructor
public class MilestoneHelper {

    private static final Comparator<Milestone> BY_TARGET_DATE = Comparator
            .comparing(Milestone::getTargetDate)
            .thenComparing(Milestone::getName, String.CASE_INSENSITIVE_ORDER);

    private final TicketRepository ticketRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneDependencyRepository milestoneDependencyRepository;
    private final MilestoneMapper milestoneMapper;
    private final MilestoneHealthResolver milestoneHealthResolver;
    private final Clock clock;

    /**
     * Maps milestones to list responses sorted by target date, then name.
     *
     * @param milestones      the milestones
     * @param visibleBoardIds boards the user is a member of; dependencies on
     *                        milestones of other boards are left out
     * @return list responses with progress, health and dependencies
     */
    public List<MilestoneListResponse> toListResponses(List<Milestone> milestones, Collection<UUID> visibleBoardIds) {
        if (milestones.isEmpty()) {
            return List.of();
        }
        List<UUID> milestoneIds = milestones.stream().map(Milestone::getId).toList();
        Map<UUID, MilestoneTicketCountProjection> counts = loadCounts(milestoneIds);
        Map<UUID, List<Milestone>> dependencies = loadDependencies(milestoneIds, visibleBoardIds);
        LocalDate today = today();
        return milestones.stream()
                .sorted(BY_TARGET_DATE)
                .map(milestone -> {
                    long done = doneCount(counts.get(milestone.getId()));
                    long total = totalCount(counts.get(milestone.getId()));
                    MilestoneHealth health = milestoneHealthResolver.resolve(milestone, done, total, today);
                    return milestoneMapper.toListResponse(milestone, done, total, health,
                            toDependencyResponses(milestone, dependencies.get(milestone.getId())));
                })
                .toList();
    }

    /**
     * Maps a milestone to a detail response.
     *
     * @param milestone       the milestone
     * @param visibleBoardIds boards the user is a member of; dependencies on
     *                        milestones of other boards are left out
     * @return detail response with progress, health and dependencies
     */
    public MilestoneDetailResponse toDetailResponse(Milestone milestone, Collection<UUID> visibleBoardIds) {
        List<UUID> milestoneIds = List.of(milestone.getId());
        Map<UUID, MilestoneTicketCountProjection> counts = loadCounts(milestoneIds);
        Map<UUID, List<Milestone>> dependencies = loadDependencies(milestoneIds, visibleBoardIds);
        long done = doneCount(counts.get(milestone.getId()));
        long total = totalCount(counts.get(milestone.getId()));
        MilestoneHealth health = milestoneHealthResolver.resolve(milestone, done, total, today());
        return milestoneMapper.toDetailResponse(milestone, done, total, health,
                toDependencyResponses(milestone, dependencies.get(milestone.getId())));
    }

    /**
     * Tells whether the dependent starts before the milestone it depends on is
     * due. A dependent without a start date is judged by its target date. The
     * flag only warns: dates are never shifted.
     *
     * @param dependent the dependent milestone
     * @param dependsOn the milestone it depends on
     * @return true when the dependent's start, or target without one, is before dependsOn's target
     */
    public boolean isConflict(Milestone dependent, Milestone dependsOn) {
        LocalDate dependentStart = dependent.getStartDate() != null
                ? dependent.getStartDate()
                : dependent.getTargetDate();
        return dependentStart.isBefore(dependsOn.getTargetDate());
    }

    /**
     * Loads the live milestones each of the given milestones depends on, keeping
     * only those on visible boards. Two queries in total: the edges, then the
     * milestones at their other end.
     */
    private Map<UUID, List<Milestone>> loadDependencies(List<UUID> milestoneIds, Collection<UUID> visibleBoardIds) {
        List<MilestoneDependencyProjection> edges = milestoneDependencyRepository.findLiveDependencies(milestoneIds);
        if (edges.isEmpty()) {
            return Map.of();
        }
        Set<UUID> dependsOnIds = edges.stream()
                .map(MilestoneDependencyProjection::getDependsOnMilestoneId)
                .collect(Collectors.toSet());
        Set<UUID> visible = new HashSet<>(visibleBoardIds);
        Map<UUID, Milestone> dependsOnById = milestoneRepository.findAllById(dependsOnIds).stream()
                .filter(dependsOn -> visible.contains(dependsOn.getBoard().getId()))
                .collect(Collectors.toMap(Milestone::getId, Function.identity()));

        Map<UUID, List<Milestone>> dependencies = new HashMap<>();
        for (MilestoneDependencyProjection edge : edges) {
            Milestone dependsOn = dependsOnById.get(edge.getDependsOnMilestoneId());
            if (dependsOn != null) {
                dependencies.computeIfAbsent(edge.getMilestoneId(), id -> new ArrayList<>()).add(dependsOn);
            }
        }
        return dependencies;
    }

    /**
     * Maps the milestones a dependent depends on, sorted by target date, with their conflict flags.
     */
    private List<MilestoneDependencyResponse> toDependencyResponses(Milestone dependent, List<Milestone> dependsOn) {
        if (dependsOn == null) {
            return List.of();
        }
        return dependsOn.stream()
                .sorted(BY_TARGET_DATE)
                .map(other -> milestoneMapper.toDependencyResponse(other, isConflict(dependent, other)))
                .toList();
    }

    /**
     * Runs the aggregate count query and indexes the rows by milestone ID.
     * Milestones without tickets have no row.
     */
    private Map<UUID, MilestoneTicketCountProjection> loadCounts(List<UUID> milestoneIds) {
        Map<UUID, MilestoneTicketCountProjection> counts = new HashMap<>();
        for (MilestoneTicketCountProjection row : ticketRepository.countByMilestoneIds(milestoneIds)) {
            counts.put(row.getMilestoneId(), row);
        }
        return counts;
    }

    private static long doneCount(MilestoneTicketCountProjection row) {
        return row == null || row.getDoneCount() == null ? 0L : row.getDoneCount();
    }

    private static long totalCount(MilestoneTicketCountProjection row) {
        return row == null || row.getTotalCount() == null ? 0L : row.getTotalCount();
    }

    /**
     * Today as the UTC day of the injected clock. Milestone health and the home
     * deadline window both use the UTC day, so every caller gets the same health
     * for the same moment, whatever the viewer's zone. The viewer's day is applied
     * only by the clients, for their own displays.
     */
    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
