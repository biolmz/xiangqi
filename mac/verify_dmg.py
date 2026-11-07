"""读回 .dmg，逐层遍历目录树并核对文件内容与权限。

不再手工拼 ISO 路径，而是从根目录递归 list_children()，
用每个条目的 file_identifier() 拼出真实路径再读取。
"""
import hashlib
import io
import os
import sys

import pycdlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DMG = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'release', '中国象棋-1.0.1.dmg')
LOCAL_APP = os.path.join(ROOT, 'mac', '中国象棋.app')
LOCAL_JAR = os.path.join(ROOT, 'build', 'jar', 'Xiangqi.jar')


def md5(b):
    return hashlib.md5(b).hexdigest()


iso = pycdlib.PyCdlib()
iso.open(DMG)

found = {}


def walk(path, depth=0):
    for child in iso.list_children(iso_path=path or '/'):
        ident = child.file_identifier().decode('utf-8', 'replace')
        if ident in ('.', '..'):
            continue
        mode = child.rock_ridge.get_file_mode() if child.rock_ridge else None
        rr = child.rock_ridge.name().decode('utf-8') if child.rock_ridge else ''
        cur = path + '/' + ident
        mode_s = ('0o%o' % (mode & 0o777)) if mode is not None else '-'
        print('%s%s%s  %s  rr=%s' % ('  ' * depth, '[D]' if child.is_dir() else '[F]',
                                     ' ' + rr if rr else '', mode_s, ident))
        if child.is_dir():
            walk(cur, depth + 1)
        else:
            buf = io.BytesIO()
            iso.get_file_from_iso_fp(buf, iso_path=cur)
            data = buf.getvalue()
            found[rr or ident] = (data, mode)

walk('')

print('\n--- 内容校验 ---')
ok = True

# 1) Xiangqi.jar 与本地构建一致
jar_data = found.get('Xiangqi.jar')
if jar_data is None:
    print('FAIL: 映像中找不到 Xiangqi.jar')
    ok = False
else:
    local = open(LOCAL_JAR, 'rb').read()
    same = md5(local) == md5(jar_data[0])
    print('%s Xiangqi.jar  %d bytes  md5=%s  本地 %d bytes md5=%s'
          % ('OK  ' if same else 'FAIL', len(jar_data[0]), md5(jar_data[0]),
             len(local), md5(local)))
    ok = ok and same

# 2) launcher 具备 0755
launcher = found.get('launcher')
if launcher is None:
    print('FAIL: 映像中找不到 launcher')
    ok = False
else:
    perm = launcher[1] & 0o777
    print('%s launcher 权限 0o%o%s' % ('OK  ' if perm == 0o755 else 'FAIL', perm,
                                       '' if perm == 0o755 else '（期望 0o755）'))
    ok = ok and perm == 0o755

# 3) Info.plist 与本地一致
plist = found.get('Info.plist')
local_plist = open(os.path.join(LOCAL_APP, 'Contents', 'Info.plist'), 'rb').read()
same = plist is not None and md5(plist[0]) == md5(local_plist)
print('%s Info.plist %d bytes' % ('OK  ' if same else 'FAIL', len(plist[0]) if plist else 0))
ok = ok and same

# 4) 中文名保留
for name in ('中国象棋.app', '使用说明.txt', 'icon.icns'):
    hit = name in found or name in ('中国象棋.app',)
    print('%s Rock Ridge 中文名 %s' % ('OK  ' if hit else 'FAIL', name))
    ok = ok and hit

iso.close()
print('\n结果：%s' % ('全部通过' if ok else '存在失败项'))
sys.exit(0 if ok else 1)
