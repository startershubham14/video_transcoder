package dev.shubham.transcoder.packaging;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link Packager} for a configured {@link OutputMode}. Spring injects every
 * {@code Packager} bean; this indexes them by {@link Packager#mode()} so adding a new
 * output mode is a new bean + enum constant, with no caller changes.
 */
@Component
public class PackagerFactory {

    private final Map<OutputMode, Packager> byMode = new EnumMap<>(OutputMode.class);

    public PackagerFactory(List<Packager> packagers) {
        for (Packager packager : packagers) {
            byMode.put(packager.mode(), packager);
        }
    }

    public Packager forMode(OutputMode mode) {
        Packager packager = byMode.get(mode);
        if (packager == null) {
            throw new IllegalStateException("No Packager registered for output mode " + mode);
        }
        return packager;
    }
}
