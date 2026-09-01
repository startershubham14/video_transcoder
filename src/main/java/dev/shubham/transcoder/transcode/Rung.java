package dev.shubham.transcoder.transcode;

/**
 * An output resolution rung in the adaptive ladder. Persisted as its {@code label}
 * (the {@code segments.rung} TEXT column).
 */
public enum Rung {
    R720P("720p", 720, "2500k"),
    R480P("480p", 480, "1000k"),
    R360P("360p", 360, "600k");

    private final String label;
    private final int height;
    private final String videoBitrate;

    Rung(String label, int height, String videoBitrate) {
        this.label = label;
        this.height = height;
        this.videoBitrate = videoBitrate;
    }

    public String label() {
        return label;
    }

    public int height() {
        return height;
    }

    public String videoBitrate() {
        return videoBitrate;
    }

    /** Resolve a rung from its persisted {@code segments.rung} label (e.g. "720p"). */
    public static Rung fromLabel(String label) {
        for (Rung rung : values()) {
            if (rung.label.equals(label)) {
                return rung;
            }
        }
        throw new IllegalArgumentException("unknown rung label: " + label);
    }
}
