package com.korur.oba.apps.kaizen.chat.enums;

/**
 * Defines who reads a chat channel.
 */
public enum ChatChannelVisibility {

    // Every active member of the organization reads the channel.
    PUBLIC,

    // Only the users on the channel's member list read the channel.
    PRIVATE
}
