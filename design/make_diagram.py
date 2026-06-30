#!/usr/bin/env python3
"""Render the high-level design flow diagram (input -> engine -> output) to PNG.

Run with the sibling venv (python-docx + matplotlib):
    ../../eightfold-candidate-transformer/.venv/bin/python make_diagram.py
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

# Palette
NAVY = "#1F3B73"
BLUE = "#2E5A88"
STRUCT = "#DCE7F5"   # structured source fill
UNSTRUCT = "#EAF2E6" # unstructured source fill
LEDGER = "#FFF3D6"   # highlighted ledger
ENGINE = "#F4F6FA"
OUT = "#E8F0FE"
PILL = "#1F3B73"
EDGE = "#6E7B91"

fig, ax = plt.subplots(figsize=(13.0, 7.0), dpi=200)
ax.set_xlim(0, 100); ax.set_ylim(0, 100); ax.axis("off")


def box(x, y, w, h, text, fc, ec=NAVY, fs=8.5, bold=False, tcolor="#15233f", lw=1.3, align="center"):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.2,rounding_size=1.6",
                                fc=fc, ec=ec, lw=lw, mutation_aspect=0.5))
    ax.text(x + (w / 2 if align == "center" else 1.2), y + h / 2, text,
            ha=align if align != "center" else "center", va="center",
            fontsize=fs, color=tcolor, fontweight="bold" if bold else "normal",
            family="DejaVu Sans", linespacing=1.25)


def arrow(x1, y1, x2, y2, color=BLUE, lw=2.0):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="-|>", lw=lw, color=color, shrinkA=2, shrinkB=2))


def label(x, y, text, fs=7.5, color=EDGE, style="italic", ha="center", bold=False):
    ax.text(x, y, text, ha=ha, va="center", fontsize=fs, color=color,
            fontstyle=style, family="DejaVu Sans", fontweight="bold" if bold else "normal")


# ---- column headers --------------------------------------------------------
for x, txt in [(9.5, "INPUTS"), (30, "EXTRACT"), (50.5, "EVIDENCE LEDGER"),
               (70, "REDUCE"), (90, "OUTPUT")]:
    ax.text(x, 96.5, txt, ha="center", va="center", fontsize=9.5,
            color=NAVY, fontweight="bold", family="DejaVu Sans")

# ---- INPUTS: 6 source boxes ------------------------------------------------
sources = [
    ("Recruiter CSV", "structured", STRUCT),
    ("ATS JSON blob", "structured", STRUCT),
    ("GitHub API", "unstructured", UNSTRUCT),
    ("LinkedIn", "unstructured", UNSTRUCT),
    ("Résumé (.pdf/.docx)", "unstructured", UNSTRUCT),
    ("Recruiter notes", "unstructured", UNSTRUCT),
]
top, bh, gap = 90, 9.5, 2.0
for i, (name, kind, fc) in enumerate(sources):
    y = top - i * (bh + gap)
    box(2, y - bh, 15, bh, f"{name}\n({kind})", fc, fs=7.6)
label(9.5, 19.0, "any may be missing,\nempty, or malformed", fs=7.2)

# ---- EXTRACT (sandboxed adapters) ------------------------------------------
box(22, 30, 17, 52, "", ENGINE, ec=BLUE, lw=1.4)
ax.text(30.5, 78, "Sandboxed adapters", ha="center", fontsize=8.4, fontweight="bold", color=NAVY)
for j, (n, sub) in enumerate([("1  detect", "read manifest"),
                              ("2  extract", "format → claims"),
                              ("3  normalize", "E.164 · YYYY-MM · ISO · skills")]):
    y = 64 - j * 13
    box(24, y, 13.5, 9.5, n, "#FFFFFF", ec=EDGE, fs=8, bold=True)
    label(30.7, y - 1.0, sub, fs=6.6)
label(30.5, 33.5, "garbage source → 0 claims\n(never crashes the run)", fs=7.0, color="#9A6A00")

# ---- EVIDENCE LEDGER -------------------------------------------------------
box(42, 30, 17, 52, "", LEDGER, ec="#C9A227", lw=1.6)
ax.text(50.5, 77, "every value is a", ha="center", fontsize=7.6, color="#7A5C00")
ax.text(50.5, 73, "traced CLAIM", ha="center", fontsize=10.5, fontweight="bold", color="#7A5C00")
claim_lines = ("field   = full_name\n"
               "raw     = 'Ada B. Lovelace'\n"
               "value   = 'Ada B. Lovelace'\n"
               "method  = csv_column:name\n"
               "source  = recruiter_csv\n"
               "trust   = 0.90\n"
               "seen_at = 2026-06-20")
ax.text(50.5, 52, claim_lines, ha="center", va="center", fontsize=7.0,
        family="DejaVu Sans Mono", color="#4a3a00", linespacing=1.5,
        bbox=dict(boxstyle="round,pad=0.5", fc="#FFFBEF", ec="#E0C879"))
label(50.5, 33.5, "63 claims for this candidate", fs=7.0, color="#9A6A00")

# ---- REDUCE: merge + confidence -> canonical -------------------------------
box(61, 55, 18, 27, "", ENGINE, ec=BLUE, lw=1.4)
ax.text(70, 78, "4  merge   ·   5  confidence", ha="center", fontsize=8.2, fontweight="bold", color=NAVY)
label(70, 73.5, "trust-ranked winner per field;\nunion + dedup for lists;\nscore from agreement vs conflict", fs=6.8)
box(63, 57, 14, 7.5, "winner: 'Ada B. Lovelace'\nconfidence 0.58 (4 disagree)", "#FFFFFF", ec=EDGE, fs=6.6)

box(61, 33, 18, 16, "CANONICAL RECORD\n(one internal type)", "#E5E7F0", ec=NAVY, fs=8.2, bold=True)
label(70, 36.5, "+ provenance, + overall 0.779", fs=6.8)

# ---- PROJECT + VALIDATE + OUTPUT -------------------------------------------
box(82, 70, 16, 12, "6 project · 7 validate", ENGINE, ec=BLUE, fs=8, bold=True)
label(90, 66.5, "runtime config reshapes;\ntype-checked at the boundary", fs=6.8)
# config doc feeding project from top
box(82, 86, 16, 8.5, "runtime config\n(select · rename · normalize · on_missing)", "#FFF7E8", ec="#C9A227", fs=6.7)
arrow(90, 86, 90, 82, color="#C9A227", lw=1.6)

box(82, 47, 16, 14, "DEFAULT schema JSON\nfull profile + provenance\n+ confidence", OUT, ec=NAVY, fs=7.2)
box(82, 30, 16, 13, "CUSTOM-config JSON\nflat contact card\n(primary_email, phone…)", OUT, ec=NAVY, fs=7.2)

# ---- main flow arrows ------------------------------------------------------
arrow(17.3, 50, 21.6, 50)     # inputs -> extract
arrow(39.3, 56, 41.6, 56)     # extract -> ledger
arrow(59.3, 60, 60.6, 68)     # ledger -> reduce(merge)
arrow(70, 54.5, 70, 49.5)     # merge -> canonical
arrow(79.3, 44, 81.6, 70, color=BLUE)  # canonical -> project
arrow(90, 69.5, 90, 61.5)     # project -> default out
arrow(88, 47, 88, 43.5)       # default -> custom (same engine)
label(93.6, 45, "same\nengine", fs=6.4, color=EDGE)

# ---- bottom band: how it solves -------------------------------------------
pills = [
    ("DETERMINISTIC & EXPLAINABLE", "pure reduction of immutable claims;\nevery field traces to source + method"),
    ("ROBUST", "per-source sandboxes; unknown → null,\nnever invented; validated before return"),
    ("SCALE", "each candidate independent & O(claims);\nstateless, parallelizable adapters"),
]
pw, px0 = 30, 4
for i, (title, sub) in enumerate(pills):
    x = px0 + i * (pw + 2)
    box(x, 4, pw, 13, "", "#EEF2FB", ec=NAVY, lw=1.3)
    ax.text(x + pw / 2, 13.4, title, ha="center", fontsize=8.2, fontweight="bold", color=NAVY)
    ax.text(x + pw / 2, 8.0, sub, ha="center", fontsize=7.0, color="#33415c", linespacing=1.3)

ax.text(50, 21.5, "How the pipeline satisfies the three constraints",
        ha="center", fontsize=8, color=EDGE, fontstyle="italic")

plt.savefig("flow_diagram.png", bbox_inches="tight", pad_inches=0.15, facecolor="white")
print("wrote flow_diagram.png")
