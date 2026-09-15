"""
fix_replay.py — чинит биомную палитру в реплеях Minecraft 1.21.1
Стратегия: заменяет все биомные PalettedContainer на single-value.
Блоки НЕ трогаются — мир рендерится нормально, только туман/цвет неба
будет одинаковым везде (minor visual difference).

Использование:
    python fix_replay.py <файл.mcpr>
    python fix_replay.py <файл.mcpr> <результат.mcpr>
"""

import struct, zipfile, io, os, sys, math

CHUNK_PACKET_ID = 0x27  # level_chunk_with_light в MC 1.21.1
PLAINS_BIOME    = 1     # fallback биом (plains в глобальном реестре 1.21.1)


# ─── VarInt ──────────────────────────────────────────────────────────────────

def read_varint(data: bytes, pos: int):
    result = shift = 0
    while pos < len(data):
        b = data[pos]; pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80): return result, pos
        shift += 7
    raise ValueError(f"Truncated VarInt @ {pos}")

def write_varint(v: int) -> bytes:
    out = bytearray()
    while True:
        b = v & 0x7F; v >>= 7
        if v: b |= 0x80
        out.append(b);
        if not v: break
    return bytes(out)


# ─── NBT skipper ─────────────────────────────────────────────────────────────

def _skip_payload(d, p, t):
    if   t == 0: return p
    elif t in (1,): return p+1
    elif t in (2,): return p+2
    elif t in (3,5): return p+4
    elif t in (4,6): return p+8
    elif t == 7: n=struct.unpack_from(">i",d,p)[0]; return p+4+n
    elif t == 8: n=struct.unpack_from(">H",d,p)[0]; return p+2+n
    elif t == 9:
        et=d[p]; p+=1; n=struct.unpack_from(">i",d,p)[0]; p+=4
        for _ in range(n): p=_skip_payload(d,p,et)
        return p
    elif t == 10:
        while True:
            ct=d[p]; p+=1
            if ct==0: break
            nl=struct.unpack_from(">H",d,p)[0]; p+=2+nl
            p=_skip_payload(d,p,ct)
        return p
    elif t == 11: n=struct.unpack_from(">i",d,p)[0]; return p+4+n*4
    elif t == 12: n=struct.unpack_from(">i",d,p)[0]; return p+4+n*8
    else: raise ValueError(f"Unknown NBT type {t} @ {p}")

def skip_nbt(d, p):
    """Network NBT (MC 1.20.2+): корневой тег без имени"""
    t=d[p]; p+=1
    if t==0: return p
    # В network NBT нет имени у корневого тега — сразу payload
    return _skip_payload(d,p,t)


# ─── PalettedContainer helpers ───────────────────────────────────────────────

def fix_paletted_container_blocks(data: bytes, pos: int):
    """
    Прочитать и починить блок-палитру секции.
    Зажимает выходящие за границы индексы к максимально допустимому.
    bits=0      → single value (без изменений)
    bits 1..8   → indirect: зажимаем значения > pal_len-1
    bits 9+     → direct (глобальная палитра, без таблицы, без изменений)
    """
    out = bytearray()
    bits = data[pos]; pos += 1
    out.append(bits)

    if bits == 0:
        val, pos = read_varint(data, pos)
        out.extend(write_varint(val))
        dlen, pos = read_varint(data, pos)
        out.extend(write_varint(dlen))
        pos += dlen * 8

    elif bits <= 8:
        pal_len, pos = read_varint(data, pos)
        out.extend(write_varint(pal_len))
        for _ in range(pal_len):
            v, pos = read_varint(data, pos)
            out.extend(write_varint(v))

        dlen, pos = read_varint(data, pos)
        longs = []
        for _ in range(dlen):
            l = struct.unpack_from(">Q", data, pos)[0]; pos += 8
            longs.append(l)

        max_idx = pal_len - 1
        epl = 64 // bits
        mask = (1 << bits) - 1
        new_longs = []
        for l in longs:
            new_l = 0
            for e in range(epl):
                v = (l >> (e * bits)) & mask
                if v > max_idx: v = max_idx  # зажать
                new_l |= v << (e * bits)
            new_longs.append(new_l)

        out.extend(write_varint(dlen))
        for l in new_longs:
            out.extend(struct.pack(">Q", l))

    else:
        # Direct (15 бит для блоков) — глобальная палитра, без таблицы
        dlen, pos = read_varint(data, pos)
        out.extend(write_varint(dlen))
        for _ in range(dlen):
            l = struct.unpack_from(">Q", data, pos)[0]; pos += 8
            out.extend(struct.pack(">Q", l))

    return pos, bytes(out)


def skip_and_replace_biome_palette(data: bytes, pos: int):
    """
    Прочитать биом-палитру секции, заменить на single-value.
    Возвращает (new_pos, new_bytes, first_biome_id).
    bits=0       → уже single, оставляем
    bits 1..3    → indirect: берём первый биом из палитры
    bits 4+      → direct: используем PLAINS_BIOME
    """
    bits = data[pos]; pos += 1

    if bits == 0:
        val, pos = read_varint(data, pos)
        dlen, pos = read_varint(data, pos)
        pos += dlen * 8
        # Single value — оставляем как есть
        new_bytes = bytes([0]) + write_varint(val) + write_varint(0)
        return pos, new_bytes, val

    elif bits <= 3:
        pal_len, pos = read_varint(data, pos)
        first_biome = None
        for i in range(pal_len):
            v, pos = read_varint(data, pos)
            if i == 0: first_biome = v
        dlen, pos = read_varint(data, pos)
        pos += dlen * 8
        biome = first_biome if first_biome is not None else PLAINS_BIOME
        new_bytes = bytes([0]) + write_varint(biome) + write_varint(0)
        return pos, new_bytes, biome

    else:
        # Direct — пропускаем, ставим plains
        dlen, pos = read_varint(data, pos)
        pos += dlen * 8
        new_bytes = bytes([0]) + write_varint(PLAINS_BIOME) + write_varint(0)
        return pos, new_bytes, PLAINS_BIOME


# ─── Секции чанка ────────────────────────────────────────────────────────────

def _validate_block_bytes(blk: bytes):
    """Бросить исключение если indirect блок-палитра имеет 0 записей."""
    bits = blk[0]
    if 0 < bits <= 8:
        pal_len, _ = read_varint(blk, 1)
        if pal_len == 0:
            raise ValueError("Block indirect palette has 0 entries")

def fix_sections(section_data: bytes):
    """
    Пройти по секциям чанка, заменить биомные палитры.
    Если парсинг падает или данные невалидны — бросаем исключение (чанк будет выброшен).
    """
    pos = 0
    out = bytearray()
    fixed_count = 0

    while pos < len(section_data):
        if pos + 2 > len(section_data):
            raise ValueError(f"Обрезанная секция: {len(section_data)-pos} байт осталось")

        # Block count (short)
        out.extend(section_data[pos:pos+2]); pos += 2

        # Block states — фиксим и валидируем
        new_pos, blk_bytes = fix_paletted_container_blocks(section_data, pos)
        _validate_block_bytes(blk_bytes)
        out.extend(blk_bytes); pos = new_pos

        # Biomes — заменяем на single-value
        new_pos, bio_bytes, _ = skip_and_replace_biome_palette(section_data, pos)
        out.extend(bio_bytes); pos = new_pos
        fixed_count += 1

    # Инвариант: должны потребить ровно все байты секций
    if pos != len(section_data):
        raise ValueError(f"Размер секций не совпал: pos={pos}, ожидалось {len(section_data)}")

    return bytes(out), fixed_count


# ─── Поиск данных секций в пакете ────────────────────────────────────────────

def _quick_valid_sections(data: bytes) -> bool:
    """Быстрая проверка: похоже ли это на данные секций чанка?"""
    if len(data) < 200:
        return False
    # Первые 2 байта — block count (short, 0..4096). Старший байт 0x00-0x10.
    if data[0] > 0x10:
        return False
    # 3-й байт — bits_per_entry для блоков. Типичные значения: 0,4,5,6,7,8,15
    if len(data) > 2 and data[2] not in {0, 4, 5, 6, 7, 8, 15}:
        return False
    # Попробуем спарсить первую секцию
    try:
        pos = 2
        new_pos, _ = fix_paletted_container_blocks(data, pos)
        new_pos, _, _ = skip_and_replace_biome_palette(data, new_pos)
        return new_pos > pos  # Прогресс есть
    except:
        return False

def _find_section_data(pkt: bytes, after: int):
    """
    Найти позицию VarInt data_len + section_data в пакете.
    Сначала пробуем позицию after (результат NBT-парсера),
    затем brute-force в диапазоне ±200 байт.
    Возвращает (nbt_end_pos, data_len, data_start) или raises ValueError.
    """
    def try_pos(p):
        if p < 0 or p >= len(pkt):
            return None
        try:
            dl, ds = read_varint(pkt, p)
            if dl < 200 or dl > 3_000_000:
                return None
            if ds + dl > len(pkt):
                return None
            candidate = pkt[ds:ds+dl]
            if _quick_valid_sections(candidate):
                return (p, dl, ds)
        except:
            pass
        return None

    # Сначала доверяем NBT-парсеру
    r = try_pos(after)
    if r:
        return r

    # Brute-force: ищем в диапазоне после заголовка
    header_end = after - 1  # примерная позиция, ищем вокруг
    for delta in range(-50, 300):
        p = after + delta
        if p <= 8:
            continue
        r = try_pos(p)
        if r:
            return r

    raise ValueError("Не удалось найти данные секций")


# ─── Пакет level_chunk_with_light ────────────────────────────────────────────

def fix_chunk_packet(pkt: bytes):
    """Починить один chunk-пакет. Возвращает (fixed_bytes, biome_fixes) или (None, 0)."""
    try:
        pos = 0
        pkt_id, pos = read_varint(pkt, pos)

        chunk_x = struct.unpack_from(">i", pkt, pos)[0]; pos += 4
        chunk_z = struct.unpack_from(">i", pkt, pos)[0]; pos += 4

        header_end = pos  # сохраняем позицию после X,Z

        # NBT skip (может быть неточным для некоторых чанков)
        try:
            nbt_end = skip_nbt(pkt, pos)
        except:
            nbt_end = pos  # fallback

        # Ищем реальную позицию data_len
        nbt_end_real, data_len, data_start = _find_section_data(pkt, nbt_end)

        nbt_bytes = pkt[header_end:nbt_end_real]
        section_bytes = pkt[data_start:data_start+data_len]
        rest = pkt[data_start+data_len:]

        fixed_sections, biome_fixes = fix_sections(section_bytes)

        out = bytearray()
        out.extend(write_varint(pkt_id))
        out.extend(struct.pack(">i", chunk_x))
        out.extend(struct.pack(">i", chunk_z))
        out.extend(nbt_bytes)
        out.extend(write_varint(len(fixed_sections)))
        out.extend(fixed_sections)
        out.extend(rest)
        return bytes(out), biome_fixes

    except Exception:
        return None, 0


# ─── Обработка tmcpr ─────────────────────────────────────────────────────────

def process_tmcpr(data: bytes):
    pos = 0; total = len(data)
    out = bytearray()
    stats = {"total":0, "fixed_chunks":0, "dropped_chunks":0, "biome_fixes":0, "other":0}

    while pos < total:
        if pos + 8 > total: out.extend(data[pos:]); break

        ts  = struct.unpack_from(">I", data, pos)[0]; pos += 4
        plen= struct.unpack_from(">I", data, pos)[0]; pos += 4

        if plen == 0 or pos + plen > total:
            print(f"  [!] Обрезанный пакет @ {pos}"); break

        pkt = data[pos:pos+plen]; pos += plen
        stats["total"] += 1

        try: pkt_id, _ = read_varint(pkt, 0)
        except:
            out.extend(struct.pack(">I",ts)); out.extend(struct.pack(">I",plen)); out.extend(pkt)
            stats["other"] += 1; continue

        if pkt_id == CHUNK_PACKET_ID:
            fixed, biome_fixes = fix_chunk_packet(pkt)
            if fixed is None:
                stats["dropped_chunks"] += 1
                # Выбрасываем проблемный чанк — лучше дыра в мире чем краш
                continue
            pkt = fixed
            stats["fixed_chunks"] += 1
            stats["biome_fixes"]  += biome_fixes
        else:
            stats["other"] += 1

        out.extend(struct.pack(">I", ts))
        out.extend(struct.pack(">I", len(pkt)))
        out.extend(pkt)

    return bytes(out), stats


# ─── Главная функция ─────────────────────────────────────────────────────────

def fix_mcpr(input_path: str, output_path: str):
    print(f"\nОткрываю: {input_path}")
    with zipfile.ZipFile(input_path, "r") as zin:
        names = zin.namelist()
        print(f"Файлы: {names}")
        if "recording.tmcpr" not in names:
            print("ОШИБКА: recording.tmcpr не найден!"); return

        tmcpr = zin.read("recording.tmcpr")
        print(f"Размер: {len(tmcpr)/1024/1024:.1f} МБ")
        print("Обрабатываю...")

        fixed, stats = process_tmcpr(tmcpr)

        print(f"\n  Всего пакетов:         {stats['total']}")
        print(f"  Чанков обработано:     {stats['fixed_chunks']}")
        print(f"  Биом-секций заменено:  {stats['biome_fixes']}")
        if stats['dropped_chunks']:
            print(f"  Чанков выброшено:      {stats['dropped_chunks']}  ← ПЛОХО, мир будет с дырами")
        print(f"  Прочих пакетов:        {stats['other']}")

        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zout:
            for name in names:
                zout.writestr(name, fixed if name=="recording.tmcpr" else zin.read(name))

    with open(output_path, "wb") as f: f.write(buf.getvalue())
    print(f"\nГотово → {output_path} ({len(buf.getvalue())/1024/1024:.1f} МБ)")


def main():
    if len(sys.argv) < 2:
        print("Использование: python fix_replay.py <файл.mcpr> [результат.mcpr]")
        sys.exit(1)
    inp = sys.argv[1]
    if not os.path.exists(inp): print(f"ОШИБКА: {inp} не найден"); sys.exit(1)
    if not inp.endswith(".mcpr"): print("ОШИБКА: нужен .mcpr"); sys.exit(1)
    out = sys.argv[2] if len(sys.argv)>=3 else os.path.splitext(inp)[0]+"_fixed.mcpr"
    if out==inp: print("ОШИБКА: совпадает с входным"); sys.exit(1)
    fix_mcpr(inp, out)

if __name__ == "__main__":
    main()
