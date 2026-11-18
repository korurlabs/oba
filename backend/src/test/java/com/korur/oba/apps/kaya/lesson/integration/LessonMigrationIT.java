package com.korur.oba.apps.kaya.lesson.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.entity.Lesson;
import com.korur.oba.apps.kaya.lesson.repository.LessonRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The lesson schema refuses rows outside the limits the API promises, whatever the application does,
 * and lessons go with their repository and organization but never with their author.
 */
class LessonMigrationIT extends AbstractLessonIT {

    @Autowired
    private LessonRepository lessonRepository;

    private Lesson.LessonBuilder<?, ?> lesson(Fixture fixture, String title, String body) {
        return Lesson.builder()
                .title(title)
                .body(body)
                .organizationId(fixture.tenant().organization().getId())
                .userId(fixture.ada().user().getId());
    }

    private long lessonCount(Fixture fixture) {
        return count("SELECT count(*) FROM kaya.lessons WHERE organization_id = ?", fixture.tenant().organization().getId());
    }

    @Test
    void saveLesson_limitsExactlyMet_isAccepted() throws Exception {
        Fixture fixture = newFixture();
        Lesson edge = lesson(fixture, "t".repeat(300), "b".repeat(4000)).repositoryId(UUID.fromString(fixture.repositoryId())).build();

        Lesson saved = lessonRepository.saveAndFlush(edge);

        assertEquals(300, saved.getTitle().length());
        assertEquals(1L, lessonCount(fixture));
    }

    @Test
    void saveLesson_emptyTitle_isRejected() throws Exception {
        Fixture fixture = newFixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> lessonRepository.saveAndFlush(lesson(fixture, "", "body").build()));
    }

    @Test
    void saveLesson_titleOver300_isRejected() throws Exception {
        Fixture fixture = newFixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> lessonRepository.saveAndFlush(lesson(fixture, "t".repeat(301), "body").build()));
    }

    @Test
    void saveLesson_emptyBody_isRejected() throws Exception {
        Fixture fixture = newFixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> lessonRepository.saveAndFlush(lesson(fixture, "title", "").build()));
    }

    @Test
    void saveLesson_bodyOver4000_isRejected() throws Exception {
        Fixture fixture = newFixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> lessonRepository.saveAndFlush(lesson(fixture, "title", "b".repeat(4001)).build()));
    }

    @Test
    void saveLesson_unknownRepository_isRejected() throws Exception {
        Fixture fixture = newFixture();

        assertThrows(DataIntegrityViolationException.class, () -> lessonRepository
                .saveAndFlush(lesson(fixture, "title", "body").repositoryId(UUID.randomUUID()).build()));
    }

    @Test
    void deleteRepository_withLessons_cascadesToTheirLessonsOnly() throws Exception {
        Fixture fixture = newFixture();
        UUID repositoryId = UUID.fromString(fixture.repositoryId());
        lessonRepository.saveAndFlush(lesson(fixture, "repo lesson", "body").repositoryId(repositoryId).build());
        lessonRepository.saveAndFlush(lesson(fixture, "wide lesson", "body").build());

        jdbcTemplate.update("DELETE FROM kaya.sessions WHERE repository_id = ?", repositoryId);
        jdbcTemplate.update("DELETE FROM kaya.repositories WHERE id = ?", repositoryId);

        assertEquals(1L, lessonCount(fixture));
        assertEquals(1L, count("SELECT count(*) FROM kaya.lessons WHERE organization_id = ? AND repository_id IS NULL",
                fixture.tenant().organization().getId()));
    }

    @Test
    void deleteUser_whoAuthoredALesson_isRefused() throws Exception {
        Fixture fixture = newFixture();
        lessonRepository.saveAndFlush(lesson(fixture, "title", "body").build());

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate
                .update("DELETE FROM shrine.users WHERE id = ?", fixture.ada().user().getId()));
        assertEquals(1L, lessonCount(fixture));
    }
}
