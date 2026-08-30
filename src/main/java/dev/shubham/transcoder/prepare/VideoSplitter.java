package dev.shubham.transcoder.prepare;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Splits the source into <em>keyframe-aligned</em> segments — the real parallel-processing
 * unit. A segment is only independently transcodable if it starts on a keyframe (GOP
 * boundary), so the target length is approximate.
 *
 * <p>Option A (default): {@code -c copy} keyframe-snapped — cheap, uneven lengths.
 * Option B (fallback): force a fixed GOP grid, then split — uniform, at the cost of a
 * re-encode. Target length is {@code pipeline.segment-target-seconds}.
 */
@Service
public class VideoSplitter {

    /** @return the produced segment files, in playback order. */
    public List<Path> split(Path sourceFile, int targetSeconds) {
        // TODO ffmpeg segment muxer, keyframe-snapped (-c copy) by default.
        throw new UnsupportedOperationException("not implemented");
    }
}
