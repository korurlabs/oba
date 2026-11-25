package com.korur.oba.shrine.identity.enums;

/**
 * Who a user account stands for. Every account is a person unless an organization ADMIN registered
 * it as an agent, which then carries a {@link com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail}.
 */
public enum UserKind {

    // A person who signs in through Keycloak.
    HUMAN,

    // An agent of one organization that acts through its own API token or that Oba runs itself.
    AGENT
}
