package com.korur.oba.apps.kazan.chat.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Number of users on the member list of one private channel.
 */
public interface ChatChannelMemberCountProjection {

    UUID getChannelId();

    Integer getMemberCount();
}
