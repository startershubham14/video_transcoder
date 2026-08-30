package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.transcode.Rung;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Derives the output ladder from the <em>real</em> probed resolution, never upscaling.
 * e.g. a 1080p source → {720p, 480p, 360p}; a 480p source → {360p}.
 */
@Service
public class OutputLadderService {

    public List<Rung> deriveLadder(int sourceHeight) {
        // TODO pick rungs strictly below the source height.
        throw new UnsupportedOperationException("not implemented");
    }
}
