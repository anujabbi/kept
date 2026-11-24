#!/usr/bin/env python3
"""Put a rendered KEPT screen into the Play Store screenshot frame used in docs/play/screenshots/.

    python scripts/play-screenshots.py RAW.png OUT.png --caption "First line|Second line" --bg dawn

The frame (first made for 2.0.1, issue #161; this script reproduces it for 2.1, issue #185):
a 1080 x 1920 opaque RGB canvas in one Daybreak colour, a two-line caption in Bricolage
Grotesque ExtraBold centred in the top band, and the screen 687 px wide with its top at y = 380,
clipped to a 52 px rounded rectangle over a soft shadow (with a faint ring on a dark ground).

RAW.png is a real screen, not a mock-up: render it with a Robolectric/Roborazzi test at font
scale 1.0 and `qualifiers = "w393dp-h851dp-420dpi"` (the frame's aspect), from the fixtures the
screenshot tests already use (`HomeFixtures`, `LockFixtures`, `WeeklyLockFixtures`,
`NightLockFixtures`), for example

    rule.setContent { Shot(1f) { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
    rule.onRoot().captureRoboImage("build/playshots/lock_night.png")

run with `./gradlew testDebugUnitTest --tests "*PlayShots*" -Proborazzi.test.record=true`. That
test is a throwaway and is not committed, so it never runs with the suite.

Needs Pillow. Check the result with `--check OUT.png` (size, mode, no alpha).
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / "app/src/main/res/font/bricolage_grotesque_display_extrabold.ttf"

W, H = 1080, 1920
PHONE_X, PHONE_Y, PHONE_W, PHONE_H = 197, 380, 687, 1488
RADIUS = 52
FONT_PX, LINE_PITCH, CAPTION_CENTRE_Y, TRACKING = 80, 82, 191, -0.3
# The shadow is ink on a light ground. On a dark ground it is black, and a faint 3 px cream ring
# keeps a dark screen apart from it.
SHADOW_DY, SHADOW_BLUR = 24, 28
SHADOW_ALPHA, SHADOW_ALPHA_DARK = 0.37, 0.52
RING_PX, RING_ALPHA = 3, 0.215

INK, CREAM = (0x1E, 0x1B, 0x3C), (0xFF, 0xF4, 0xE4)
# Daybreak palette grounds, and whether the caption on them is light.
GROUNDS = {
    "dawn": ((0xFF, 0xD0, 0xA6), False),
    "night": ((0x2B, 0x27, 0x60), True),
    "cream": ((0xFF, 0xF9, 0xF1), False),
    "rose": ((0xE9, 0xA9, 0x9A), False),
    "lilac": ((0xEC, 0xE9, 0xFF), False),
    "tide": ((0xBF, 0xD9, 0xF2), False),
    "purple": ((0x5B, 0x4B, 0xD6), True),
    "gold": ((0xFF, 0xC2, 0x3D), False),
}
# The launcher icon's dawn sky, top to bottom.
DAWN_GRADIENT = [(0.0, (0xE9, 0xA9, 0x9A)), (0.55, (0xFF, 0xD0, 0xA6)), (1.0, (0xFF, 0xC2, 0x3D))]


def ground(name):
    if name == "dawn-gradient":
        col = Image.new("RGB", (1, H))
        for y in range(H):
            t = y / (H - 1)
            for (t0, c0), (t1, c1) in zip(DAWN_GRADIENT, DAWN_GRADIENT[1:]):
                if t0 <= t <= t1:
                    k = (t - t0) / (t1 - t0)
                    col.putpixel((0, y), tuple(round(a + (b - a) * k) for a, b in zip(c0, c1)))
                    break
        return col.resize((W, H)), False
    colour, light = GROUNDS[name]
    return Image.new("RGB", (W, H), colour), light


def draw_line(draw, font, text, centre_x, baseline_y, fill):
    widths = [font.getlength(ch) for ch in text]
    total = sum(widths) + TRACKING * (len(text) - 1)
    x = centre_x - total / 2
    for ch, w in zip(text, widths):
        draw.text((x, baseline_y), ch, font=font, fill=fill, anchor="ls")
        x += w + TRACKING


def compose(raw_path, out_path, caption, bg):
    canvas, light = ground(bg)
    lines = caption.split("|")
    if len(lines) > 2:
        sys.exit("a caption is one or two lines")

    # Shadow: the phone's shape in ink, moved down and blurred.
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (PHONE_X, PHONE_Y + SHADOW_DY, PHONE_X + PHONE_W - 1, PHONE_Y + PHONE_H - 1 + SHADOW_DY),
        RADIUS, fill=round(255 * (SHADOW_ALPHA_DARK if light else SHADOW_ALPHA)),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR))
    canvas.paste(Image.new("RGB", (W, H), (0, 0, 0) if light else INK), (0, 0), shadow)
    if light:
        ring = Image.new("L", (W * 2, H * 2), 0)
        ImageDraw.Draw(ring).rounded_rectangle(
            ((PHONE_X - RING_PX) * 2, (PHONE_Y - RING_PX) * 2, (PHONE_X + PHONE_W + RING_PX) * 2 - 1, (PHONE_Y + PHONE_H + RING_PX) * 2 - 1),
            (RADIUS + RING_PX) * 2, fill=round(255 * RING_ALPHA),
        )
        canvas.paste(Image.new("RGB", (W, H), CREAM), (0, 0), ring.resize((W, H), Image.LANCZOS))

    # The screen, scaled to the frame and clipped to rounded corners (mask drawn 4x for smooth edges).
    raw = Image.open(raw_path).convert("RGB")
    if abs(raw.height / raw.width - PHONE_H / PHONE_W) > 0.01:
        sys.exit(f"{raw_path}: {raw.width}x{raw.height} is not the frame's aspect; render at w393dp-h851dp")
    screen = raw.resize((PHONE_W, PHONE_H), Image.LANCZOS)
    mask = Image.new("L", (PHONE_W * 4, PHONE_H * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, PHONE_W * 4 - 1, PHONE_H * 4 - 1), RADIUS * 4, fill=255)
    canvas.paste(screen, (PHONE_X, PHONE_Y), mask.resize((PHONE_W, PHONE_H), Image.LANCZOS))

    font = ImageFont.truetype(str(FONT), FONT_PX)
    draw = ImageDraw.Draw(canvas)
    ascent = 59  # cap height of the 80 px cut, so the block centres on its letters
    first = CAPTION_CENTRE_Y - ((len(lines) - 1) * LINE_PITCH - ascent) / 2
    for i, line in enumerate(lines):
        if font.getlength(line) > W - 2 * 60:
            sys.exit(f"caption line too wide: {line!r}")
        draw_line(draw, font, line, W / 2, first + i * LINE_PITCH, CREAM if light else INK)

    canvas.save(out_path, optimize=True)
    check(out_path)


def check(path):
    im = Image.open(path)
    ok = im.size == (W, H) and im.mode == "RGB"
    print(f"{path}: {im.size[0]}x{im.size[1]} {im.mode} {'ok' if ok else 'WRONG'}")
    if not ok:
        sys.exit(1)


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("raw")
    p.add_argument("out", nargs="?")
    p.add_argument("--caption", help='one or two lines, split with "|"')
    p.add_argument("--bg", choices=sorted(GROUNDS) + ["dawn-gradient"])
    p.add_argument("--check", action="store_true", help="only check RAW is 1080x1920 RGB with no alpha")
    a = p.parse_args()
    if a.check:
        return check(a.raw)
    if not (a.out and a.caption and a.bg):
        p.error("OUT, --caption and --bg are required")
    compose(a.raw, a.out, a.caption, a.bg)


if __name__ == "__main__":
    main()
