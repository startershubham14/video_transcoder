package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.transcode.Rung;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Fixed ladder: every rung strictly below the source height (no upscaling). e.g. a 1080p
 * source → {720p, 480p, 360p}; a 480p source → {360p}; a 360p source → {}.
 */
@Component
public class FixedLadderPolicy implements LadderPolicy {

    @Override
    public List<Rung> rungsFor(int sourceHeight) {
        return Arrays.stream(Rung.values())
                .filter(rung -> rung.height() < sourceHeight)
                .toList();
    }
}
