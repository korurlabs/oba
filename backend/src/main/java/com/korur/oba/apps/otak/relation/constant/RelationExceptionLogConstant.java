package com.korur.oba.apps.otak.relation.constant;

/**
 * Log message constants for relation domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RelationExceptionLogConstant {

    private RelationExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a relation that the organization does not have, missing or of another organization.
     */
    public static final String RELATION_NOT_FOUND = "Relation not found: id={}, organizationId={}";

    /**
     * Log message for a relation id that no organization has, in a lookup that does not know the organization
     * yet.
     */
    public static final String RELATION_NOT_FOUND_BY_ID = "Relation not found: id={}";

    /**
     * Log message for an owner who is not an active member of the organization.
     */
    public static final String OWNER_NOT_ORGANIZATION_MEMBER =
            "Relation owner is not an active member: organizationId={}, ownerUserId={}";

    /**
     * Log message for a stage change on a relation that is not a lead.
     */
    public static final String RELATION_NOT_LEAD = "Stage refused, relation is not a lead: id={}, type={}";

    /**
     * Log message for a stage given on the creation of a relation that is not a lead.
     */
    public static final String STAGE_ON_NON_LEAD = "Stage refused on create, relation is not a lead: type={}";

    /**
     * Log message for a stage that the relation's type cannot have.
     */
    public static final String STAGE_NOT_FOR_TYPE = "Stage refused, not a stage of the type: type={}, stage={}";

    /**
     * Log message for a type change to the type the relation already has.
     */
    public static final String RELATION_TYPE_UNCHANGED = "Type change refused, type unchanged: id={}, type={}";
}
