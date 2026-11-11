#!/usr/bin/env python3
"""Validate only user-supplied HYPER_DOG sprite files; never generates substitutes."""
import struct
import sys
import zlib
from pathlib import Path

ASSET_DIR = Path(__file__).resolve().parents[1] / (
    "fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols")
NAMES = ("red7", "rainbow7", "bar", "grape", "replay", "cherry", "watermelon")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

def validate(path):
    raw=path.read_bytes()
    if not raw.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG")
    pos=len(PNG_SIGNATURE);ihdr=None;seen_end=False;seen_image=False
    while pos+12<=len(raw):
        length=struct.unpack_from(">I",raw,pos)[0]
        kind=raw[pos+4:pos+8];end=pos+12+length
        if end>len(raw):raise ValueError("truncated PNG chunk")
        payload=raw[pos+8:pos+8+length]
        expected=struct.unpack_from(">I",raw,pos+8+length)[0]
        if zlib.crc32(kind+payload)&0xffffffff!=expected:
            raise ValueError("PNG chunk CRC mismatch")
        if kind==b"IHDR":
            if pos!=8 or len(payload)!=13 or ihdr is not None:
                raise ValueError("invalid IHDR placement/length")
            width,height,depth,mode,compression,filter_method,interlace=struct.unpack(">IIBBBBB",payload)
            if (width,height,depth,mode,compression,filter_method,interlace)!=(256,256,8,6,0,0,0):
                raise ValueError("must be 256x256, 8-bit noninterlaced RGBA PNG")
            ihdr=(width,height)
        if kind==b"IDAT":seen_image=True
        if kind==b"IEND":
            if length!=0:raise ValueError("invalid IEND")
            seen_end=True
            if end!=len(raw):raise ValueError("trailing PNG content")
            break
        pos=end
    if not ihdr or not seen_image or not seen_end:
        raise ValueError("incomplete PNG image")

def main():
    failed=[]
    for name in NAMES:
        path=ASSET_DIR/f"{name}.png"
        try:validate(path);print("PASS",path.relative_to(ASSET_DIR))
        except (ValueError,OSError) as exc:
            failed.append((name,str(exc)))
            print("BLOCKED",name,exc)
    if failed:
        print(f"HYPER_DOG_SYMBOL_ASSETS: {len(NAMES)-len(failed)}/{len(NAMES)} PASS")
        return 1
    print("HYPER_DOG_SYMBOL_ASSETS: 7/7 PASS")
    return 0

if __name__=="__main__":
    sys.exit(main())
