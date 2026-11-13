#!/usr/bin/env python3
"""Regenerates every raster icon of the project.

The launcher artwork is original: a dot with three NFC waves leaving it, drawn on a teal gradient.
Nothing is downloaded and no third party artwork is used, which keeps the repository free of
non-free assets (an F-Droid requirement).

Usage:
    pip install pillow
    python3 tools/make_icons.py

Outputs:
    app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png
    fastlane/metadata/android/{en-US,ru-RU}/images/icon.png
    fastlane/metadata/android/{en-US,ru-RU}/images/featureGraphic.png

The vector versions of the same drawing live in
    app/src/main/res/drawable/ic_nfc_wave.xml
    app/src/main/res/drawable/ic_launcher_foreground.xml
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
FASTLANE = os.path.join(ROOT, "fastlane", "metadata", "android")

# Geometry, expressed in the 108x108 viewport of an adaptive icon.
VIEWPORT = 108.0
CENTRE_X = 44.0
CENTRE_Y = 54.0
DOT_RADIUS = 5.6
ARC_RADII = (12.0, 20.8, 29.6)
ARC_SPAN_DEG = 58.0
STROKE = 6.8
# Centre of the whole glyph, used to place it on the feature graphic.
GLYPH_CENTRE_X = CENTRE_X + ARC_RADII[-1] / 2.0 - DOT_RADIUS / 2.0
GLYPH_CENTRE_Y = CENTRE_Y

GRADIENT_TOP = (10, 124, 134)      # 0A7C86
GRADIENT_BOTTOM = (0, 56, 59)      # 00383B
WAVE_COLOUR = (255, 255, 255, 255)
TEXT_COLOUR = (255, 255, 255, 255)
SUBTLE_COLOUR = (180, 226, 230, 255)

MASTER = 2048                      # everything is rendered here and downscaled with Lanczos
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
STORE_ICON = 512
FEATURE_WIDTH = 1024
FEATURE_HEIGHT = 500

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def mix(first, second, ratio):
    return tuple(
        int(round(first[i] + (second[i] - first[i]) * ratio)) for i in range(len(first))
    )


def vertical_gradient(width, height, top, bottom):
    """Builds a gradient by stretching a one pixel wide column."""
    column = Image.new("RGB", (1, height))
    column.putdata([mix(top, bottom, y / max(1, height - 1)) for y in range(height)])
    return column.resize((width, height), Image.Resampling.NEAREST)


def fit_font(draw, text, max_width, start_size, path, minimum=22):
    """Largest font size that keeps the text inside max_width."""
    size = start_size
    while size > minimum:
        font = ImageFont.truetype(path, size)
        box = draw.textbbox((0, 0), text, font=font)
        if box[2] - box[0] <= max_width:
            return font
        size -= 2
    return ImageFont.truetype(path, minimum)


def draw_glyph(draw, centre_x, centre_y, scale, colour):
    width = max(1, int(round(STROKE * scale)))
    for radius_units in ARC_RADII:
        radius = radius_units * scale
        draw.arc(
            [centre_x - radius, centre_y - radius, centre_x + radius, centre_y + radius],
            start=-ARC_SPAN_DEG,
            end=ARC_SPAN_DEG,
            fill=colour,
            width=width,
        )
    dot = DOT_RADIUS * scale
    draw.ellipse(
        [centre_x - dot, centre_y - dot, centre_x + dot, centre_y + dot], fill=colour
    )


def launcher_icon(size, circular):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    if circular:
        mask_draw.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        mask_draw.rounded_rectangle(
            [0, 0, size - 1, size - 1], radius=int(round(size * 0.19)), fill=255
        )
    image.paste(vertical_gradient(size, size, GRADIENT_TOP, GRADIENT_BOTTOM), (0, 0), mask)

    scale = size / VIEWPORT
    draw = ImageDraw.Draw(image)
    draw_glyph(draw, CENTRE_X * scale, CENTRE_Y * scale, scale, WAVE_COLOUR)
    return image


def feature_graphic(title, subtitle):
    image = Image.new("RGBA", (FEATURE_WIDTH, FEATURE_HEIGHT), (0, 0, 0, 0))
    image.paste(
        vertical_gradient(FEATURE_WIDTH, FEATURE_HEIGHT, GRADIENT_TOP, GRADIENT_BOTTOM),
        (0, 0),
    )
    draw = ImageDraw.Draw(image)

    scale = 2.7
    centre_x = 235 - (GLYPH_CENTRE_X - CENTRE_X) * scale
    centre_y = FEATURE_HEIGHT / 2 - (GLYPH_CENTRE_Y - CENTRE_Y) * scale
    draw_glyph(draw, centre_x, centre_y, scale, WAVE_COLOUR)

    text_x = 420
    max_text_width = FEATURE_WIDTH - text_x - 70
    if os.path.exists(FONT_BOLD):
        title_font = fit_font(draw, title, max_text_width, 96, FONT_BOLD)
        subtitle_font = fit_font(
            draw,
            subtitle,
            max_text_width,
            int(title_font.size / 2.1),
            FONT_REGULAR if os.path.exists(FONT_REGULAR) else FONT_BOLD,
        )
    else:  # pragma: no cover - only when the font package is missing
        title_font = ImageFont.load_default()
        subtitle_font = title_font

    title_box = draw.textbbox((0, 0), title, font=title_font)
    subtitle_box = draw.textbbox((0, 0), subtitle, font=subtitle_font)
    block_height = (title_box[3] - title_box[1]) + 24 + (subtitle_box[3] - subtitle_box[1])
    top = (FEATURE_HEIGHT - block_height) / 2 - title_box[1]
    draw.text((text_x, top), title, font=title_font, fill=TEXT_COLOUR)
    draw.text(
        (text_x, top + (title_box[3] - title_box[1]) + 24),
        subtitle,
        font=subtitle_font,
        fill=SUBTLE_COLOUR,
    )
    return image


def save(image, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG")
    print("wrote", os.path.relpath(path, ROOT), image.size[0], "x", image.size[1])


def main():
    square = launcher_icon(MASTER, circular=False)
    circular = launcher_icon(MASTER, circular=True)

    for density, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        save(square.resize((size, size), Image.Resampling.LANCZOS),
             os.path.join(folder, "ic_launcher.png"))
        save(circular.resize((size, size), Image.Resampling.LANCZOS),
             os.path.join(folder, "ic_launcher_round.png"))

    store_icon = square.resize((STORE_ICON, STORE_ICON), Image.Resampling.LANCZOS)
    for locale, subtitle in (
        ("en-US", "10 slots: copy, transmit, delete"),
        ("ru-RU", "10 слотов: копировать, передать, удалить"),
    ):
        images = os.path.join(FASTLANE, locale, "images")
        save(store_icon, os.path.join(images, "icon.png"))
        title = "NFC Cardholder"
        save(feature_graphic(title, subtitle), os.path.join(images, "featureGraphic.png"))


if __name__ == "__main__":
    main()
