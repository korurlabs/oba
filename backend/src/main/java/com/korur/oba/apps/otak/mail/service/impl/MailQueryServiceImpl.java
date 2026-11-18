package com.korur.oba.apps.otak.mail.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.repository.MailRepository;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.mail.validator.MailValidator;

/**
 * Implementation of the mail reads declared by {@link MailQueryService}.
 *
 * Every method runs in a read-only transaction (the class-level {@link Transactional}); a
 * caller that already holds a read-write transaction joins it. No debug line is written,
 * because the outreach that reads its mail logs the read.
 *
 * @see MailServiceImpl
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailQueryServiceImpl implements MailQueryService {

    private final MailRepository mailRepository;
    private final MailValidator mailValidator;

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link MailValidator#ensureMailExists(UUID, UUID)}.
     */
    @Override
    public Mail getEntity(UUID organizationId, UUID mailId) {
        return mailValidator.ensureMailExists(organizationId, mailId);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link MailValidator#ensureMailExists(UUID)}.
     */
    @Override
    public Mail getEntityById(UUID mailId) {
        return mailValidator.ensureMailExists(mailId);
    }

    /**
     * {@inheritDoc}
     *
     * Reads through {@link MailRepository#findIdsByOrganizationIdAndRelationId}.
     */
    @Override
    public List<UUID> getIdsByRelation(UUID organizationId, UUID relationId) {
        return mailRepository.findIdsByOrganizationIdAndRelationId(organizationId, relationId);
    }
}
