"""打包 macOS 交付 zip，显式写入 Unix 权限（保证 .app 启动器可执行）。"""
import os
import sys
import zipfile

root, out = sys.argv[1], sys.argv[2]


def add(z, path, arc, executable=False):
    zi = zipfile.ZipInfo(arc)
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.create_system = 3  # Unix
    mode = 0o755 if executable else 0o644
    zi.external_attr = (0o100000 | mode) << 16
    with open(path, 'rb') as f:
        z.writestr(zi, f.read())


count = 0
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for base, _dirs, files in os.walk(root):
        for name in files:
            if name.endswith('.py') and name.split('_')[0] in ('make', 'verify'):
                continue
            p = os.path.join(base, name)
            arc = os.path.relpath(p, root).replace('\\', '/')
            exe = name == 'launcher' or name.endswith('.command')
            add(z, p, arc, exe)
            count += 1
print('packed %d files -> %s' % (count, out))
