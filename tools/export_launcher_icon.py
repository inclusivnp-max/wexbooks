#!/usr/bin/env python3
"""Export the Android vector sources to SVG/PNG and legacy WebP (rsvg-convert + Pillow)."""
import io
from pathlib import Path
import subprocess
from xml.etree import ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ART = ROOT / 'artwork/launcher'
ART.mkdir(parents=True, exist_ok=True)
A = '{http://schemas.android.com/apk/res/android}'


def color(value):
    if len(value) == 9:
        return '#' + value[3:], str(int(value[1:3], 16) / 255)
    return value, '1'


def layer(name):
    root = ET.parse(RES / f'drawable/ic_launcher_{name}.xml').getroot()
    defs, shapes = [], []
    for i, p in enumerate(root.findall('path')):
        attrs = {'d': p.attrib[A + 'pathData']}
        gradient = p.find('.//gradient')
        if gradient is not None:
            ident = f'{name}{i}'
            stops = []
            for stop in gradient:
                c, alpha = color(stop.attrib[A + 'color'])
                stops.append(f'<stop offset="{stop.attrib[A + "offset"]}" stop-color="{c}" stop-opacity="{alpha}"/>')
            defs.append(f'<linearGradient id="{ident}" gradientUnits="userSpaceOnUse" x1="{gradient.attrib[A + "startX"]}" y1="{gradient.attrib[A + "startY"]}" x2="{gradient.attrib[A + "endX"]}" y2="{gradient.attrib[A + "endY"]}">{"".join(stops)}</linearGradient>')
            attrs['fill'] = f'url(#{ident})'
        else:
            attrs['fill'], attrs['fill-opacity'] = color(p.attrib.get(A + 'fillColor', '#000000'))
        if A + 'strokeColor' in p.attrib:
            attrs['stroke'], attrs['stroke-opacity'] = color(p.attrib[A + 'strokeColor'])
            attrs['stroke-width'] = p.attrib[A + 'strokeWidth']
            attrs['stroke-linecap'] = p.attrib.get(A + 'strokeLineCap', 'butt')
        shapes.append(ET.tostring(ET.Element('path', attrs), encoding='unicode'))
    return ''.join(defs), ''.join(shapes)


bg_defs, bg = layer('background')
fg_defs, fg = layer('foreground')
_, mono = layer('monochrome')


def svg(mask=None, themed=False):
    clip = '' if mask is None else '<clipPath id="mask">' + mask + '</clipPath>'
    content = '<path fill="#DDEBE5" d="M0,0H108V108H0Z"/><g fill="#224C51">' + mono.replace('fill="#FFFFFF"', 'fill="#224C51"') + '</g>' if themed else bg + fg
    group = '' if mask is None else ' clip-path="url(#mask)"'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72"><title>WexBooks — open book</title><defs>{bg_defs}{fg_defs}{clip}</defs><g{group}>{content}</g></svg>'


def raster(source, size):
    data = subprocess.check_output(['rsvg-convert', '-w', str(size), '-h', str(size)], input=source.encode())
    return Image.open(io.BytesIO(data)).convert('RGBA')


circle = '<circle cx="54" cy="54" r="36"/>'
rounded = '<rect x="18" y="18" width="72" height="72" rx="16"/>'
for name, source in [('wexbooks-icon', svg()), ('wexbooks-icon-round', svg(circle)), ('wexbooks-icon-themed', svg(circle, True))]:
    (ART / f'{name}.svg').write_text(source + '\n')
    raster(source, 512).save(ART / f'{name}.png')
for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    for suffix, mask in [('', rounded), ('_round', circle)]:
        raster(svg(mask), size).save(RES / f'mipmap-{density}/ic_launcher{suffix}.webp', lossless=True)
print('Exported 3 SVG + 3 PNG previews and 10 matching legacy icons from Android vectors.')
