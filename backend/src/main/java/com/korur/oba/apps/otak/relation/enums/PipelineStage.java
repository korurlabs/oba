package com.korur.oba.apps.otak.relation.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * Where a relation stands on the battlefield. Mapped by name to the PostgreSQL enum
 * otak_pipeline_stage_enum, in the same order.
 *
 * A lead has one of {@link #LEAD_STAGES}, a customer one of {@link #CUSTOMER_STAGES}, and a
 * supplier no stage; a check constraint on the table enforces it. {@link #CLOSED} is the
 * won end of the pipeline: a lead moved there becomes a customer and keeps the stage.
 */
public enum PipelineStage {

    // Every new lead starts here.
    NEW,

    QUALIFIED,

    CONTACTED,

    WARM,

    PROPOSAL,

    // Won: the lead became a customer.
    CLOSED,

    // Inactive: the lead was lost.
    LOST,

    // Inactive: the lead is set aside for now.
    PARKED,

    // A customer that never was a lead.
    DIRECT;

    /**
     * Stages a lead can have.
     */
    public static final List<PipelineStage> LEAD_STAGES = List.of(NEW, QUALIFIED, CONTACTED, WARM, PROPOSAL, LOST, PARKED);

    /**
     * Lead stages the list leaves out unless the stage filter names them.
     */
    public static final List<PipelineStage> INACTIVE_LEAD_STAGES = List.of(LOST, PARKED);

    /**
     * Stages a customer can have; Otak sets them, a user never does.
     */
    public static final List<PipelineStage> CUSTOMER_STAGES = List.of(CLOSED, DIRECT);
}
