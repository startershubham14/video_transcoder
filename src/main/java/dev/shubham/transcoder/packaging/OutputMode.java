package dev.shubham.transcoder.packaging;

/**
 * Final packaging target. MP4 is the milestone (standalone per-rung files, plays in any
 * {@code <video>} tag); HLS is the goal (per-rung media playlists + a master {@code .m3u8}
 * for adaptive bitrate). Selected by {@code pipeline.output-mode}.
 */
public enum OutputMode {
    MP4,
    HLS
}
