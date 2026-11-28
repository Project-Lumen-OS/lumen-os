#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""payload_wire.py: the protobuf wire format of an update payload manifest read without a schema (fields, varints,
PartitionInfo), and the image digest it is checked against. One reader for the payload tools of tools/ota
(sign_payload.py, delta_proof.py, check_release_assets.py) and tools/release/delta.py."""
import hashlib


def varint(buf, i):
    v = s = 0
    while True:
        if i >= len(buf):
            raise ValueError("truncated varint")
        b = buf[i]
        i += 1
        v |= (b & 0x7F) << s
        if not b & 0x80:
            return v, i
        s += 7
        if s > 63:
            raise ValueError("varint too long")


def enc_varint(v):
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def fields(buf):
    """[(field number, wire type, value, raw bytes of the whole field)] in file order."""
    out, i = [], 0
    while i < len(buf):
        start = i
        tag, i = varint(buf, i)
        num, wt = tag >> 3, tag & 7
        if wt == 0:
            val, i = varint(buf, i)
        elif wt == 1:
            val, i = buf[i:i + 8], i + 8
        elif wt == 2:
            n, i = varint(buf, i)
            val, i = buf[i:i + n], i + n
        elif wt == 5:
            val, i = buf[i:i + 4], i + 4
        else:
            raise ValueError(f"wire type {wt} not supported")
        if i > len(buf):
            raise ValueError("truncated field")
        out.append((num, wt, val, buf[start:i]))
    return out


def partition_info(raw):
    d = {n: v for n, _, v, _ in fields(raw)}
    return d.get(1), (d.get(2) or b"").hex()


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 22), b""):
            h.update(b)
    return h.hexdigest()
