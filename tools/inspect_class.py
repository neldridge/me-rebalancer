"""Print JVM members from a jar without requiring a JDK (supports Java 25)."""
import struct, sys, zipfile

def inspect(jar, name):
    with zipfile.ZipFile(jar) as z:
        data = z.read(name.replace(".", "/") + ".class")
    pos = 8
    def u(n):
        nonlocal pos
        value = int.from_bytes(data[pos:pos+n], "big"); pos += n
        return value
    cp = [None] * u(2)
    i = 1
    while i < len(cp):
        tag = u(1)
        if tag == 1:
            size = u(2); cp[i] = data[pos:pos+size].decode("utf-8", "replace"); pos += size
        elif tag in (3, 4): pos += 4
        elif tag in (5, 6): pos += 8; i += 1
        elif tag in (7, 8, 16, 19, 20): cp[i] = u(2)
        elif tag in (9, 10, 11, 12, 17, 18): pos += 4
        elif tag == 15: pos += 3
        else: raise ValueError(tag)
        i += 1
    u(2); u(2); parent = u(2)
    print(name, "extends", cp[cp[parent]] if parent else "-")
    for _ in range(u(2)): u(2)
    for kind in ("field", "method"):
        for _ in range(u(2)):
            flags, name, desc = u(2), cp[u(2)], cp[u(2)]
            print(kind, hex(flags), name, desc)
            for _ in range(u(2)):
                u(2); size = u(4); pos += size

if __name__ == "__main__":
    for name in sys.argv[2:]: inspect(sys.argv[1], name)
