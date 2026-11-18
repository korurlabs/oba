package com.korur.oba.apps.kaya.release.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.entity.RepositoryPack;

/**
 * The packs each repository composes, by position.
 */
@Repository
public interface RepositoryPackRepository extends JpaRepository<RepositoryPack, UUID>,
        JpaSpecificationExecutor<RepositoryPack> {

    List<RepositoryPack> findByRepositoryIdOrderByPositionAsc(UUID repositoryId);

    /**
     * The repositories that compose a pack, in id order, the order their releases are locked in.
     *
     * @param packId the pack
     * @return the repository ids
     */
    @Query("SELECT rp.repositoryId FROM RepositoryPack rp WHERE rp.packId = :packId ORDER BY rp.repositoryId")
    List<UUID> findRepositoryIdsByPackId(@Param("packId") UUID packId);

    /**
     * The repositories that compose any of the packs, each once, in id order, the order their
     * releases are locked in.
     *
     * @param packIds the packs, not empty
     * @return the repository ids
     */
    @Query("SELECT DISTINCT rp.repositoryId FROM RepositoryPack rp WHERE rp.packId IN :packIds ORDER BY rp.repositoryId")
    List<UUID> findRepositoryIdsByPackIdIn(@Param("packIds") Collection<UUID> packIds);

    /**
     * The folders a pack is mounted at across all repositories, each once, in order. A pack
     * belongs to one organization, so these are folders of that organization's repositories.
     *
     * @param packId the pack
     * @return the mount folders
     */
    @Query("SELECT DISTINCT rp.rootPath FROM RepositoryPack rp WHERE rp.packId = :packId AND rp.rootPath IS NOT NULL "
            + "ORDER BY rp.rootPath")
    List<String> findRootPathsByPackId(@Param("packId") UUID packId);

    /**
     * The mounts of the repositories, the rows with a mount folder.
     *
     * @param repositoryIds the repositories, not empty
     * @return the rows, by folder
     */
    List<RepositoryPack> findByRepositoryIdInAndRootPathIsNotNullOrderByRootPathAsc(Collection<UUID> repositoryIds);

    /**
     * Removes a repository's pack order, flushed before the new rows are inserted so the unique
     * repository and pack pair never sees the old and the new row at once.
     *
     * @param repositoryId the repository
     * @return the number of removed rows
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RepositoryPack rp WHERE rp.repositoryId = :repositoryId")
    int deleteByRepositoryId(@Param("repositoryId") UUID repositoryId);
}
