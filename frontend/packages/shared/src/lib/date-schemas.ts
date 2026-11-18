import { z } from "zod";

/**
 * An API instant: ISO 8601 with `Z` or an offset, kept as a string until it is
 * shown. A value without a zone is rejected, because its moment would depend on
 * the zone of whoever wrote it.
 */
export const instantSchema = z.iso.datetime({ offset: true });

/** A calendar day, `YYYY-MM-DD`, that means the same day for every viewer. */
export const dateOnlySchema = z.iso.date();
