#!/usr/bin/env python3
"""Independent cross-check of the QRIS TLV/CRC logic used by QrisEncoder.kt."""
BASE = ("00020101021126610014COM.GO-JEK.WWW01189360091439663050810210G9663050810303UMI51440014ID"
        ".CO.QRIS.WWW0215ID10254671365660303UMI5204549953033605802ID5917ALTOMEDIA, Grosir6008"
        "KARAWANG61054136162070703A016304D21A")


def crc16(data):
    crc = 0xFFFF
    for ch in data:
        crc ^= ord(ch) << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) if (crc & 0x8000) else (crc << 1)
            crc &= 0xFFFF
    return "%04X" % crc


def parse(p):
    out, i = [], 0
    while i + 4 <= len(p):
        tag, ln = p[i:i + 2], int(p[i + 2:i + 4])
        out.append((tag, p[i + 4:i + 4 + ln]))
        i += 4 + ln
    return out


def build(amount):
    stripped = [e for e in parse(BASE) if e[0] not in ("63", "54")]
    out, ins = [], False
    for e in stripped:
        out.append(e)
        if e[0] == "53":
            out.append(("54", str(amount)))
            ins = True
    if not ins:
        out.append(("54", str(amount)))
    ready = "".join(t + "%02d" % len(v) + v for t, v in out) + "6304"
    return ready + crc16(ready)


print("static base CRC ok:",
      crc16(BASE[:-4]) == BASE[-4:], "->", BASE[-4:], crc16(BASE[:-4]))

for amt in (10000, 100000, 215000, 1255000):
    q = build(amt)
    tlvs = parse(q)
    tags = [t for t, _ in tlvs]
    amount_val = [v for t, v in tlvs if t == "54"]
    crc_ok = crc16(q[:-4]) == q[-4:]
    order_ok = tags.index("54") == tags.index("53") + 1
    print(f"amt={amt:>9} crc_ok={crc_ok} tag54_position_ok={order_ok} "
          f"amt_tag={amount_val} len={len(q)}")
    print("   ", q)
