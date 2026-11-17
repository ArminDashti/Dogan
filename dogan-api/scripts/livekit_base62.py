"""jxskiss/base62 StdEncoding encode (v2) — matches LiveKit embedded TURN creds."""

_ENCODE_STD = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
_COMPACT_MASK = 0x1E
_MASK5 = 0x1F
_MASK6 = 0x3F


class _Encoder:
    def __init__(self, src: bytes) -> None:
        self.src = src
        self.pos = len(src) * 8

    def get6bits(self) -> int:
        r = self.pos & 0x7
        i = self.pos >> 3
        if r == 0:
            i, r = i - 1, 8
        b = self.src[i] >> (8 - r)
        if r < 6 and i > 0:
            b |= self.src[i - 1] << r
        return b & _MASK6

    def encode_v2(self) -> bytes:
        out = bytearray()
        while self.pos > 0:
            size = 6
            b = self.get6bits()
            if b & _COMPACT_MASK == _COMPACT_MASK:
                if self.pos > 6 or b > _MASK5:
                    size = 5
                b &= _MASK5
            out.append(_ENCODE_STD[b])
            self.pos -= size
        return bytes(out)


def encode_to_string(src: bytes) -> str:
    if not src:
        return ""
    return _Encoder(src).encode_v2().decode("ascii")