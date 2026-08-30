package dev.shubham.transcoder.prepare;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for keyframe-aligned segmentation. The concrete adapter is {@link VideoSplitter}.
 */
public interface Splitter {

    /** @return the produced segment files, in playback order. */
    List<Path> split(Path sourceFile, int targetSeconds);
}
