package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.transcode.Rung;

import java.util.List;

/**
 * Strategy for deriving the output ladder from the source resolution. Kept swappable so
 * "which rungs for this source" can evolve (fixed ladder now, quality-aware later) without
 * editing prepare logic. The default implementation is {@link FixedLadderPolicy}.
 */
public interface LadderPolicy {

    /** Rungs to produce for a source of the given height (never upscaling). */
    List<Rung> rungsFor(int sourceHeight);
}
