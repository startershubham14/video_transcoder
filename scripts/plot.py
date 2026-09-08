#!/usr/bin/env python3
"""Render the scaling benchmark as an SVG chart (stdlib only — no matplotlib).

Reads the machine-readable results `bench.py` appends to `scripts/bench_results.csv`
(columns: `workers,wallclock_min,throughput_jobs_min`) and writes a workers-vs-throughput
chart to `docs/img/scaling.svg`, which the README "Results" section embeds. SVG so it stays
crisp at any zoom and needs no plotting dependency (matching bench.py's stdlib-only style).

  python scripts/bench.py samples/clip.mp4 --copies 8 --label 1 --runs 3   # appends a row
  python scripts/bench.py samples/clip.mp4 --copies 8 --label 2 --runs 3
  python scripts/bench.py samples/clip.mp4 --copies 8 --label 4 --runs 3
  python scripts/plot.py                                                    # regenerate the chart

The chart shows measured throughput (bars) against the ideal-linear reference (dashed), so the
plateau where FFmpeg saturates the cores is visible at a glance. Re-run whenever the CSV changes
and commit the regenerated SVG.

Usage:
  python scripts/plot.py [--csv scripts/bench_results.csv] [--out docs/img/scaling.svg]
                         [--title "..."]
"""
import argparse
import csv
from pathlib import Path

# --- palette (readable on GitHub light and dark; the card carries its own white ground) ---
BG = "#ffffff"
BORDER = "#d0d7de"
INK = "#1f2328"
MUTED = "#656d76"
BAR = "#2f81f7"
BAR_TOP = "#1f6feb"
IDEAL = "#8250df"
GRID = "#eaeef2"

W, H = 720, 420
PAD_L, PAD_R, PAD_T, PAD_B = 64, 24, 64, 72


def _esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def load(csv_path: Path):
    if not csv_path.is_file():
        raise SystemExit(f"no results yet: {csv_path} (run scripts/bench.py first)")
    rows = []
    with csv_path.open(newline="") as fh:
        for r in csv.DictReader(fh):
            rows.append((int(r["workers"]), float(r["wallclock_min"]),
                         float(r["throughput_jobs_min"])))
    if not rows:
        raise SystemExit(f"{csv_path} has a header but no data rows")
    # Keep the last measurement per worker count, then sort by worker count.
    latest = {}
    for workers, wall, tput in rows:
        latest[workers] = (wall, tput)
    return sorted((w, *latest[w]) for w in latest)


def render(rows, title):
    workers = [r[0] for r in rows]
    tputs = [r[2] for r in rows]
    base_w, base_t = workers[0], tputs[0]
    ideal = [base_t * (w / base_w) for w in workers]  # linear-scaling reference
    y_max = max(max(tputs), max(ideal)) * 1.15 or 1.0

    plot_w = W - PAD_L - PAD_R
    plot_h = H - PAD_T - PAD_B
    x0, y0 = PAD_L, PAD_T
    y_base = y0 + plot_h

    def yx(v):
        return y_base - (v / y_max) * plot_h

    n = len(rows)
    slot = plot_w / n
    bar_w = min(slot * 0.5, 90)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}" font-family="-apple-system,Segoe UI,Roboto,sans-serif">',
        f'<rect x="0.5" y="0.5" width="{W-1}" height="{H-1}" rx="10" '
        f'fill="{BG}" stroke="{BORDER}"/>',
        f'<text x="{PAD_L}" y="32" font-size="17" font-weight="600" fill="{INK}">{_esc(title)}</text>',
        f'<text x="{PAD_L}" y="50" font-size="12" fill="{MUTED}">Throughput (jobs/min) vs transcode workers</text>',
    ]

    # y gridlines + labels (5 steps)
    steps = 5
    for i in range(steps + 1):
        v = y_max * i / steps
        gy = yx(v)
        parts.append(f'<line x1="{x0}" y1="{gy:.1f}" x2="{x0+plot_w}" y2="{gy:.1f}" '
                     f'stroke="{GRID}"/>')
        parts.append(f'<text x="{x0-8}" y="{gy+4:.1f}" font-size="11" fill="{MUTED}" '
                     f'text-anchor="end">{v:.0f}</text>')

    # ideal-linear reference line (dashed) + points
    pts = []
    for i, w in enumerate(workers):
        cx = x0 + slot * (i + 0.5)
        pts.append(f"{cx:.1f},{yx(ideal[i]):.1f}")
    parts.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="{IDEAL}" '
                 f'stroke-width="2" stroke-dasharray="6 5" opacity="0.85"/>')
    for i, w in enumerate(workers):
        cx = x0 + slot * (i + 0.5)
        parts.append(f'<circle cx="{cx:.1f}" cy="{yx(ideal[i]):.1f}" r="3" fill="{IDEAL}"/>')

    # bars + value labels + speedup + x labels
    for i, w in enumerate(workers):
        cx = x0 + slot * (i + 0.5)
        bx = cx - bar_w / 2
        by = yx(tputs[i])
        bh = y_base - by
        parts.append(f'<rect x="{bx:.1f}" y="{by:.1f}" width="{bar_w:.1f}" height="{bh:.1f}" '
                     f'rx="4" fill="{BAR}"/>')
        parts.append(f'<rect x="{bx:.1f}" y="{by:.1f}" width="{bar_w:.1f}" height="4" '
                     f'rx="2" fill="{BAR_TOP}"/>')
        parts.append(f'<text x="{cx:.1f}" y="{by-8:.1f}" font-size="13" font-weight="600" '
                     f'fill="{INK}" text-anchor="middle">{tputs[i]:.1f}</text>')
        speedup = tputs[i] / base_t if base_t else 0
        parts.append(f'<text x="{cx:.1f}" y="{by-24:.1f}" font-size="11" fill="{MUTED}" '
                     f'text-anchor="middle">{speedup:.2f}×</text>')
        parts.append(f'<text x="{cx:.1f}" y="{y_base+22:.1f}" font-size="13" fill="{INK}" '
                     f'text-anchor="middle">{w}</text>')

    # x axis label + legend
    parts.append(f'<text x="{x0+plot_w/2:.1f}" y="{H-30}" font-size="12" fill="{MUTED}" '
                 f'text-anchor="middle">transcode workers</text>')
    lx, ly = x0, H - 18
    parts.append(f'<rect x="{lx}" y="{ly-9}" width="14" height="10" rx="2" fill="{BAR}"/>')
    parts.append(f'<text x="{lx+20}" y="{ly}" font-size="11" fill="{MUTED}">measured</text>')
    parts.append(f'<line x1="{lx+92}" y1="{ly-4}" x2="{lx+118}" y2="{ly-4}" stroke="{IDEAL}" '
                 f'stroke-width="2" stroke-dasharray="6 5"/>')
    parts.append(f'<text x="{lx+124}" y="{ly}" font-size="11" fill="{MUTED}">ideal linear</text>')

    parts.append("</svg>")
    return "\n".join(parts)


def main():
    root = Path(__file__).resolve().parent.parent
    ap = argparse.ArgumentParser(description="Render the scaling benchmark as an SVG chart.")
    ap.add_argument("--csv", type=Path, default=root / "scripts" / "bench_results.csv")
    ap.add_argument("--out", type=Path, default=root / "docs" / "img" / "scaling.svg")
    ap.add_argument("--title", default="Transcode throughput scales with worker count")
    args = ap.parse_args()

    rows = load(args.csv)
    svg = render(rows, args.title)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(svg, encoding="utf-8")
    print(f"wrote {args.out} ({len(rows)} worker counts: {[r[0] for r in rows]})")


if __name__ == "__main__":
    main()
