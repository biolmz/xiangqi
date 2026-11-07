"""在 Windows 上构造 macOS 可挂载的磁盘映像（.dmg）。

jpackage 只能为当前系统生成原生 dmg，因此 Windows 侧采用
ISO9660（level 3）+ Rock Ridge（SUSP）容器承载 .app：
  · Rock Ridge 的 NM 记录保存中文长文件名；
  · PX 记录保存 Unix 权限，保证 Contents/MacOS/launcher 可执行；
  · 同时为每个条目写一份可读的 ASCII 名，即使系统不解析 RR 也能定位文件。
macOS 的 DiskImageMounter 通过内容嗅探挂载，双击 .dmg 即可打开。
"""
import os
import sys

import pycdlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_DIR = os.path.join(ROOT, 'mac', '中国象棋.app')
NOTE = os.path.join(ROOT, 'mac', '使用说明.txt')
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'release', '中国象棋-1.0.1.dmg')


def ascii_of(name, is_dir):
    out = []
    for ch in name.upper():
        out.append(ch if (ch.isascii() and (ch.isalnum() or ch in '._-')) else '_')
    s = ''.join(out) or 'X'
    if is_dir:
        s = s.replace('.', '_')
        # ISO9660 要求目录标识符为偶数长度，缺一个就补齐，
        # 避免严格读取器自行补 0x00 造成名字尾部出现怪字符。
        if len(s) % 2:
            s += '_'
    return s


iso = pycdlib.PyCdlib()
iso.new(interchange_level=3, vol_ident='Xiangqi', rock_ridge='1.09')


def add_tree(local_dir, iso_dir):
    used = set()
    for name in sorted(os.listdir(local_dir)):
        p = os.path.join(local_dir, name)
        base = ascii_of(name, os.path.isdir(p))
        nm = base
        k = 1
        while nm in used:
            k += 1
            nm = base[:28] + str(k)
        used.add(nm)
        if os.path.isdir(p):
            iso.add_directory(iso_dir + '/' + nm, rr_name=name, file_mode=0o755)
            add_tree(p, iso_dir + '/' + nm)
        else:
            executable = name == 'launcher' or name.endswith('.command')
            iso.add_file(p, iso_dir + '/' + nm + ';1', rr_name=name,
                         file_mode=0o755 if executable else 0o644)


# 顶层：中国象棋.app（ASCII 名 XIANGQI，RR 名保留中文）
iso.add_directory('/XIANGQI', rr_name='中国象棋.app', file_mode=0o755)
add_tree(APP_DIR, '/XIANGQI')

# 顶层说明（ASCII 名便于兜底定位）
iso.add_file(NOTE, '/README.TXT;1', rr_name='使用说明.txt', file_mode=0o644)

iso.write(OUT)
iso.close()

print('DMG written: %s (%.1f MB)' % (OUT, os.path.getsize(OUT) / 1024.0 / 1024.0))
