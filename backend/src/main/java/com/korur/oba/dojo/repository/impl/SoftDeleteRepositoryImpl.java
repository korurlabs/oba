package com.korur.oba.dojo.repository.impl;

import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

public class SoftDeleteRepositoryImpl<T extends SoftDeleteBaseEntity, ID extends Serializable>
        extends SimpleJpaRepository<T, ID> implements SoftDeleteRepository<T, ID> {

    private final EntityManager entityManager;

    public SoftDeleteRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    private String getTableName() {
        Table table = getDomainClass().getAnnotation(Table.class);
        if (table == null) {
            return getDomainClass().getSimpleName().toLowerCase();
        }
        String schema = table.schema();
        String name = table.name();
        return schema.isEmpty() ? name : schema + "." + name;
    }

    @Override
    public Optional<T> findByIdWithDeleted(ID id) {
        String tableName = getTableName();
        String sql = "SELECT * FROM " + tableName + " WHERE id = :id";
        Object result = entityManager.createNativeQuery(sql, getDomainClass()).setParameter("id", id).getSingleResult();
        return Optional.ofNullable((T) result);
    }

    @Override
    public List<T> findAllWithDeleted() {
        String tableName = getTableName();
        String sql = "SELECT * FROM " + tableName;
        return entityManager.createNativeQuery(sql, getDomainClass()).getResultList();
    }

    @Override
    public Page<T> findAllWithDeleted(Pageable pageable) {
        String tableName = getTableName();

        String sql = "SELECT * FROM " + tableName + " LIMIT :limit OFFSET :offset";
        List<T> results = entityManager.createNativeQuery(sql, getDomainClass())
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();

        // Count total
        String countSql = "SELECT COUNT(*) FROM " + tableName;
        Number total = (Number) entityManager.createNativeQuery(countSql).getSingleResult();

        return new PageImpl<>(results, pageable, total.longValue());
    }

    @Override
    public List<T> findAllDeleted() {
        String tableName = getTableName();
        String sql = "SELECT * FROM " + tableName + " WHERE deleted = true";
        return entityManager.createNativeQuery(sql, getDomainClass()).getResultList();
    }

    @Override
    public Page<T> findAllDeleted(Pageable pageable) {
        String tableName = getTableName();

        String sql = "SELECT * FROM " + tableName + " WHERE deleted = true LIMIT :limit OFFSET :offset";
        List<T> results = entityManager.createNativeQuery(sql, getDomainClass())
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();

        // Count total
        String countSql = "SELECT COUNT(*) FROM " + tableName + " WHERE deleted = true";
        Number total = (Number) entityManager.createNativeQuery(countSql).getSingleResult();

        return new PageImpl<>(results, pageable, total.longValue());
    }

    @Override
    @Transactional
    public void deleteByIdWithoutRelations(ID id) {
        entityManager.createNativeQuery("update " + getTableName() + " set deleted = true where id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void restoreById(ID id) {
        entityManager.createNativeQuery("update " + getTableName() + " set deleted = false where id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void restoreAllByIds(Iterable<ID> ids) {
        entityManager.createNativeQuery("update " + getTableName() + " set deleted = false where id in :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }
}