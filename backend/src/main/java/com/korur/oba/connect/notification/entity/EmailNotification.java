package com.korur.oba.connect.notification.entity;

import com.korur.oba.connect.notification.dto.Mail;
import com.korur.oba.connect.notification.enums.EmailTemplateType;
import com.korur.oba.dojo.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Table(name = "notifications", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class EmailNotification extends BaseEntity {

    @Column(name = "sender", columnDefinition = "TEXT DEFAULT ''", nullable = false)
    private String sender;

    @Column(name = "send", nullable = false)
    private boolean send;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "email_template_type", nullable = false)
    private EmailTemplateType emailTemplateType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = true)
    private Mail mail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipients", columnDefinition = "jsonb", nullable = true)
    @Builder.Default
    private List<String> recipients = new ArrayList<>();

}
