#!/usr/bin/env python3
"""Compact one-row flow banner for the one-pager (kept physically small so the
embedded text stays legible when scaled to ~7in wide in the DOCX).

Run: ../../eightfold-candidate-transformer/.venv/bin/python make_diagram_compact.py
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

NAVY = "#1F3B73"; BLUE = "#2E5A88"; EDGE = "#6E7B91"
FILLS = ["#DCE7F5", "#F4F6FA", "#FFF3D6", "#F4F6FA", "#E5E7F0", "#E8F0FE"]
ECS = [NAVY, BLUE, "#C9A227", BLUE, NAVY, NAVY]

fig, ax = plt.subplots(figsize=(9.2, 2.45), dpi=200)
ax.set_xlim(0, 100); ax.set_ylim(0, 100); ax.axis("off")

stages = [
    ("INPUTS", "6 sources\nstructured +\nunstructured\n(any may be bad)"),
    ("EXTRACT", "detect · extract\n· normalize\n(sandboxed)"),
    ("EVIDENCE\nLEDGER", "every value =\na traced CLAIM\n(raw·value·\nsource·trust)"),
    ("MERGE +\nCONFIDENCE", "trust-ranked\nwinner; dedup\nlists; agreement\nvs conflict"),
    ("CANONICAL\nRECORD", "one internal type\n+ provenance\n+ overall conf"),
    ("PROJECT +\nVALIDATE", "runtime config →\ndefault or custom\nJSON\n(type-checked)"),
]

n = len(stages)
bw, gap = 14.7, 1.9
x0 = (100 - (n * bw + (n - 1) * gap)) / 2
ytop, bh = 88, 64
for i, (title, sub) in enumerate(stages):
    x = x0 + i * (bw + gap)
    ax.add_patch(FancyBboxPatch((x, ytop - bh), bw, bh,
                 boxstyle="round,pad=0.2,rounding_size=2.0",
                 fc=FILLS[i], ec=ECS[i], lw=1.5, mutation_aspect=0.62))
    ax.text(x + bw / 2, ytop - 11, title, ha="center", va="center",
            fontsize=7.7, fontweight="bold", color=NAVY, family="DejaVu Sans",
            linespacing=1.05)
    ax.text(x + bw / 2, ytop - bh + 20, sub, ha="center", va="center",
            fontsize=6.4, color="#33415c", family="DejaVu Sans", linespacing=1.3)
    if i < n - 1:
        ax.annotate("", xy=(x + bw + gap - 0.2, ytop - bh / 2),
                    xytext=(x + bw + 0.2, ytop - bh / 2),
                    arrowprops=dict(arrowstyle="-|>", lw=1.7, color=BLUE))

ax.text(50, 11, "deterministic & explainable   ·   robust (unknown → null, never invented)   ·   "
                "scales (independent, O(claims), parallel)",
        ha="center", va="center", fontsize=7.3, color=EDGE, fontstyle="italic",
        family="DejaVu Sans")

plt.savefig("flow_diagram_compact.png", bbox_inches="tight", pad_inches=0.08, facecolor="white")
print("wrote flow_diagram_compact.png")
