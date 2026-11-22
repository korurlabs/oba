package com.korur.oba.apps.kaya.pack.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.pack.constant.PackConstant;
import com.korur.oba.apps.kaya.pack.constant.service.PackServiceLogConstant;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.mapper.PackMapper;
import com.korur.oba.apps.kaya.pack.repository.PackRepository;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;

/**
 * The {@link PackQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PackQueryServiceImpl implements PackQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PackQueryServiceImpl.class);

    private final PackRepository packRepository;
    private final PackMapper packMapper;
    private final LoggingService loggingService;

    @Override
    public List<PackResponse> getPacks(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.LISTING_PACKS, organizationId);
        return packMapper.toResponses(packRepository.findByOrganizationIdOrderByNameAsc(organizationId));
    }

    @Override
    public Pack getEntityById(UUID organizationId, UUID packId) {
        // Looked up within the organization, so a pack of another organization answers the same
        // 404 as an unknown id.
        return packRepository.findByIdAndOrganizationId(packId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_NOT_FOUND, packId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(PackConstant.FIELD_PACK_ID, packId.toString()));
        });
    }

    @Override
    public List<Pack> getEntitiesByIds(UUID organizationId, Collection<UUID> packIds) {
        if (packIds.isEmpty()) {
            return List.of();
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.LOADING_PACKS, packIds.size(), organizationId);
        return packRepository.findByOrganizationIdAndIdIn(organizationId, packIds);
    }

    @Override
    public List<UUID> getRequiredPackIds(UUID organizationId) {
        return packRepository.findByOrganizationIdAndRequiredTrueOrderByNameAsc(organizationId).stream()
                .map(Pack::getId)
                .toList();
    }

    @Override
    public List<Pack> getChain(UUID organizationId, UUID packId) {
        Pack pack = getEntityById(organizationId, packId);
        int limit = packCount(organizationId);
        List<Pack> chain = new ArrayList<>();
        chain.add(pack);
        while (pack.getParentPackId() != null) {
            UUID parentId = pack.getParentPackId();
            Optional<Pack> parent = chain.size() < limit
                    ? packRepository.findByIdAndOrganizationId(parentId, organizationId)
                    : Optional.empty();
            if (parent.isEmpty()) {
                // The foreign key and the fixed parent rule this out; a chain that still loops or
                // leaves the organization would compose another tenant's files or never end.
                loggingService.error(LOG_PREFIX, LogLayer.SERVICE, PackServiceLogConstant.PACK_CHAIN_BROKEN,
                        packId, organizationId, parentId, chain.size());
                throw new InternalServerErrorException(ExceptionInternalError.UNEXPECTED,
                        Map.of(PackConstant.FIELD_PACK_ID, packId.toString()));
            }
            pack = parent.get();
            chain.add(pack);
        }
        return chain.reversed();
    }

    @Override
    public List<UUID> getDescendantIds(UUID organizationId, UUID packId) {
        int limit = packCount(organizationId);
        List<UUID> descendants = new ArrayList<>();
        Set<UUID> seen = new HashSet<>(Set.of(packId));
        List<UUID> level = List.of(packId);
        while (!level.isEmpty() && seen.size() <= limit) {
            List<UUID> next = new ArrayList<>();
            for (Pack child : packRepository.findByOrganizationIdAndParentPackIdIn(organizationId, level)) {
                if (seen.add(child.getId())) {
                    next.add(child.getId());
                }
            }
            descendants.addAll(next);
            level = next;
        }
        return descendants;
    }

    @Override
    public Optional<Pack> findByName(UUID organizationId, String name) {
        return packRepository.findByOrganizationIdAndName(organizationId, name);
    }

    /**
     * The number of packs of the organization, the most steps a parent chain
     * or a descendant walk can take. It bounds collections, which hold at most
     * Integer.MAX_VALUE elements, so it is read as an int; a count beyond that
     * cannot occur and fails loudly instead of letting a walk run unbounded.
     *
     * @throws ArithmeticException when the count does not fit an int
     */
    private int packCount(UUID organizationId) {
        return Math.toIntExact(packRepository.countByOrganizationId(organizationId));
    }
}
