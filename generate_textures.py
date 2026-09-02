#!/usr/bin/env python3
"""Generate Amethyst Door's textures: the two halves of the door, and the item.

The stock is vanilla's amethyst block, read out of the jar, with a door cut into
it: a frame around the edge, a panel inset, and a handle. Nothing here invents a
colour, so the door reads as the same material as the geode it opens onto.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow, the
same script generated art approach as the rest of the suite. Deterministic:
re-running produces identical bytes.

Usage: python3 generate_textures.py [path/to/minecraft.jar]
"""

import glob
import os
import struct
import sys
import zipfile
import zlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCK = os.path.join(HERE, "src/main/resources/assets/amethyst-door-justfatlard/textures/block")
ITEM = os.path.join(HERE, "src/main/resources/assets/amethyst-door-justfatlard/textures/item")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the sprite is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))




def amethyst():
    """The block's own tones, darkest first: shadow, body, highlight."""
    counts = Counter(px for row in vanilla("block/amethyst_block.png") for px in row if px[3])
    tones = sorted((px for px, _ in counts.most_common(6)),
                   key=lambda p: 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2])
    return tones[0], tones[len(tones) // 2], tones[-1]


def stock():
    """A fresh 16x16 of amethyst to cut a door out of."""
    return [row[:] for row in vanilla("block/amethyst_block.png")[:16]]


def frame(face, shadow, light, top_edge, bottom_edge):
    """A border round the door's edge: lit on the top and left, shadowed opposite."""
    for y in range(16):
        for x in range(16):
            edge = x == 0 or x == 15 or (top_edge and y == 0) or (bottom_edge and y == 15)
            if not edge:
                continue
            face[y][x] = light if (x == 0 or (top_edge and y == 0)) else shadow
    return face


def panel(face, shadow, top, bottom):
    """An inset panel, which is what makes it read as a door rather than a slab."""
    for y in range(top, bottom + 1):
        for x in range(3, 13):
            if x in (3, 12) or y in (top, bottom):
                face[y][x] = shadow
    return face


def build_top():
    shadow, body, light = amethyst()
    face = frame(stock(), shadow, light, True, False)
    panel(face, shadow, 3, 11)
    # The handle, on the hinge-opposite side, two pixels so it reads at a glance
    face[8][13] = light
    face[9][13] = shadow
    return face


def build_bottom():
    shadow, body, light = amethyst()
    face = frame(stock(), shadow, light, False, True)
    panel(face, shadow, 2, 12)
    return face


def build_item():
    """The door as you carry it: a tall narrow slab of the same stone."""
    shadow, body, light = amethyst()
    source = vanilla("block/amethyst_block.png")[:16]
    sprite = [[CLEAR] * 16 for _ in range(16)]

    for y in range(1, 15):
        for x in range(5, 12):
            sprite[y][x] = source[y][x]
            if x in (5, 11) or y in (1, 14):
                sprite[y][x] = shadow if x == 11 or y == 14 else light

    # Panel and handle, small enough to survive being an inventory icon
    for y in range(4, 12):
        for x in range(7, 10):
            if x in (7, 9) or y in (4, 11):
                sprite[y][x] = shadow
    sprite[8][10] = light
    return sprite


if __name__ == "__main__":
    write_png(os.path.join(BLOCK, "amethyst_door_top.png"), build_top())
    write_png(os.path.join(BLOCK, "amethyst_door_bottom.png"), build_bottom())
    write_png(os.path.join(ITEM, "amethyst_door.png"), build_item())
