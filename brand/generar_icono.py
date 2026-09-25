"""Genera las capas del icono adaptativo de Echo a partir del logo maestro.

Uso:  python3 brand/generar_icono.py        (desde la raiz del repo o desde brand/)

Entrada:  brand/echo-logo.png  -- badge con fondo degradado + marca (fuente de verdad)
Salida:   app/src/main/res/drawable-*dpi/ic_launcher_foreground.png  (marca centrada)
          app/src/main/res/drawable-*dpi/ic_launcher_monochrome.png  (silueta tematica)
          app/src/main/res/drawable-*dpi/echo_logo.png              (marca, tema oscuro)
          app/src/main/res/drawable-*dpi/echo_logo_claro.png        (marca, tema claro)

Por que separar el fondo de la marca: el logo maestro viene "compuesto", con el fondo
llegando al 98% del lienzo y la marca en x 31%..91% (centro optico al 61%, no al 50%).
En un icono adaptativo el lanzador recorta con su propia mascara y solo sobrevive el
72dp central de los 108dp, asi que usar el badge entero como foreground cortaria las
ondas azules. Aqui la marca se escala para que su lado mayor ocupe LADO_MARCA del
lienzo y se centra, y el fondo degradado queda como capa aparte.

El decoder y el encoder PNG estan implementados a mano porque el logo hay que medirlo
y recortarlo, y en esta maquina no hay Pillow ni ImageMagick. Sin dependencias.
"""
import os
import struct
import sys
import zlib


def load(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n', 'no es un png'
    pos, idat, ihdr = 8, b'', None
    while pos < len(d):
        ln, typ = struct.unpack('>I4s', d[pos:pos + 8])
        body = d[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            ihdr = struct.unpack('>IIBBBBB', body)
        elif typ == b'IDAT':
            idat += body
        elif typ == b'IEND':
            break
        pos += 12 + ln
    w, h, depth, color, comp, filt, inter = ihdr
    assert depth == 8 and inter == 0, (depth, inter)
    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    raw = zlib.decompress(idat)
    stride = w * nch
    out, prev = [], bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(nch, stride):
                line[i] = (line[i] + line[i - nch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                b = prev[i]
                c = prev[i - nch] if i >= nch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out.append(line); prev = line
    return w, h, nch, out




def write_png(ruta, w, h, px):
    """px: bytearray RGBA de w*h*4."""
    crudo = bytearray()
    paso = w * 4
    for y in range(h):
        crudo.append(0)
        crudo += px[y * paso:(y + 1) * paso]

    def bloque(tipo, datos):
        return (struct.pack('>I', len(datos)) + tipo + datos
                + struct.pack('>I', zlib.crc32(tipo + datos) & 0xffffffff))

    out = b'\x89PNG\r\n\x1a\n'
    out += bloque(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    out += bloque(b'IDAT', zlib.compress(bytes(crudo), 9))
    out += bloque(b'IEND', b'')
    with open(ruta, 'wb') as f:
        f.write(out)


AQUI = os.path.dirname(os.path.abspath(__file__))   # brand/
RAIZ = os.path.dirname(AQUI)                        # raiz del repo
RES = os.path.join(RAIZ, "app", "src", "main", "res")
MAESTRO = os.path.join(RAIZ, "brand", "echo-logo.png")

# Lienzo del icono adaptativo en dp y el factor de cada densidad.
DENSIDADES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
CANVAS_DP = 108
# Lado mayor de la marca, como fraccion del lienzo (la zona segura es 72/108 = 0.667).
LADO_MARCA = 0.66

# Marca dentro de la app: aqui si se muestra SIN el fondo del badge, sobre el lienzo del
# tema, que es lo que se pidio para la pantalla de login. Como el glifo del maestro es
# blanco, sobre el lienzo claro (#EEF1F7) seria invisible, asi que se generan dos
# variantes y `MarcaEcho` elige con `LocalTemaOscuro`. La variante clara no repinta el
# maestro: solo mapea los pixeles poco saturados (el glifo) al token de texto del tema
# claro, #1A2130, y deja intactas las ondas azules, que si se leen sobre ambos lienzos.
MARCA_DP = 84
TINTA_CLARA = (0x1A, 0x21, 0x30)
SAT_GLIFO = 60   # azul de las ondas: sat ~192; glifo blanco: sat ~0


def extrae_marca(w, h, filas):
    """Separa la marca del fondo. El fondo es muy oscuro y desaturado (#0C1321..#171F2E,
    ningun canal pasa de ~46) y la marca es blanca o azul saturada, asi que un par de
    rampas con mucho margen las separa sin ambiguedad."""
    px = bytearray(w * h * 4)
    for y in range(h):
        f = filas[y]
        for x in range(w):
            i = (y * w + x) * 4
            R, G, B, A = f[x * 4], f[x * 4 + 1], f[x * 4 + 2], f[x * 4 + 3]
            if A < 8:
                continue
            brillo = max(R, G, B)
            sat = brillo - min(R, G, B)
            r_brillo = (brillo - 60) / 90.0
            r_sat = (sat - 30) / 40.0
            a = min(1.0, max(0.0, r_brillo, r_sat))
            if a <= 0.0:
                continue
            # El color observado ya esta "manchado" por el fondo en los bordes; como el
            # fondo destino es del mismo azul oscuro, el error es invisible y no hace
            # falta deshacer la premultiplicacion.
            px[i] = R; px[i + 1] = G; px[i + 2] = B; px[i + 3] = int(a * 255)
    return px


def bbox(px, w, h, umbral=0.25):
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            if px[(y * w + x) * 4 + 3] >= umbral * 255:
                xs.append(x); ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def escala_area(px, w, h, dw, dh):
    """Promedio de caja con alpha premultiplicado, con halves sucesivos para que las
    reducciones grandes (mdpi es 4.3x) no produzcan aliasing."""
    while w > dw * 2 and h > dh * 2:
        nw, nh = max(dw, w // 2), max(dh, h // 2)
        px = _caja(px, w, h, nw, nh)
        w, h = nw, nh
    return _caja(px, w, h, dw, dh)


def _caja(px, w, h, dw, dh):
    out = bytearray(dw * dh * 4)
    for dy in range(dh):
        y0 = dy * h // dh
        y1 = max(y0 + 1, (dy + 1) * h // dh)
        for dx in range(dw):
            x0 = dx * w // dw
            x1 = max(x0 + 1, (dx + 1) * w // dw)
            acc_r = acc_g = acc_b = acc_a = 0.0
            n = 0
            for y in range(y0, y1):
                base = y * w
                for x in range(x0, x1):
                    i = (base + x) * 4
                    a = px[i + 3] / 255.0
                    acc_r += px[i] * a; acc_g += px[i + 1] * a
                    acc_b += px[i + 2] * a; acc_a += a; n += 1
            o = (dy * dw + dx) * 4
            if acc_a > 0:
                out[o] = min(255, int(acc_r / acc_a))
                out[o + 1] = min(255, int(acc_g / acc_a))
                out[o + 2] = min(255, int(acc_b / acc_a))
            out[o + 3] = min(255, int(acc_a / n * 255))
    return out


def centra(px, mw, mh, lado):
    """Pega la marca escalada en un lienzo cuadrado, centrada, con alfa."""
    lienzo = bytearray(lado * lado * 4)
    ox, oy = (lado - mw) // 2, (lado - mh) // 2
    for y in range(mh):
        dst = ((oy + y) * lado + ox) * 4
        src = y * mw * 4
        lienzo[dst:dst + mw * 4] = px[src:src + mw * 4]
    return lienzo


def tinta_para_claro(px, n):
    """Variante de tema claro: el glifo (poco saturado) pasa a tinta oscura, las ondas
    azules se quedan. El alfa del maestro se respeta, asi que el antialiasing del glifo
    se conserva y no hay que difuminar a mano."""
    salida = bytearray(px)
    for i in range(n):
        o = i * 4
        if salida[o + 3] == 0:
            continue
        R, G, B = salida[o], salida[o + 1], salida[o + 2]
        if max(R, G, B) - min(R, G, B) < SAT_GLIFO:
            salida[o], salida[o + 1], salida[o + 2] = TINTA_CLARA
    return salida


def main():
    w, h, nch, filas = load(MAESTRO)
    print(f'maestro: {w}x{h}')

    marca = extrae_marca(w, h, filas)
    x0, y0, x1, y1 = bbox(marca, w, h)
    mw, mh = x1 - x0 + 1, y1 - y0 + 1
    print(f'marca extraida: bbox x{x0}..{x1} y{y0}..{y1} -> {mw}x{mh}')

    recortada = bytearray(mw * mh * 4)
    for y in range(mh):
        s = ((y0 + y) * w + x0) * 4
        recortada[y * mw * 4:(y + 1) * mw * 4] = marca[s:s + mw * 4]

    for dens, factor in DENSIDADES.items():
        lado = int(round(CANVAS_DP * factor))
        destino = int(round(lado * LADO_MARCA))
        esc = min(destino / mw, destino / mh)
        tw, th = max(1, round(mw * esc)), max(1, round(mh * esc))
        escalada = escala_area(recortada, mw, mh, tw, th)
        # monocromo: la misma silueta en blanco; el sistema le aplica el tinte.
        mono = bytearray(tw * th * 4)
        for i in range(tw * th):
            a = escalada[i * 4 + 3]
            mono[i * 4] = mono[i * 4 + 1] = mono[i * 4 + 2] = 255
            mono[i * 4 + 3] = a

        for nombre, fuente in (('ic_launcher_foreground', escalada), ('ic_launcher_monochrome', mono)):
            lienzo = centra(fuente, tw, th, lado)
            carpeta = os.path.join(RES, f'drawable-{dens}')
            os.makedirs(carpeta, exist_ok=True)
            ruta = os.path.join(carpeta, nombre + '.png')
            write_png(ruta, lado, lado, lienzo)
            print(f'  {dens:8} {nombre:26} {lado}x{lado}  marca {tw}x{th}  {os.path.getsize(ruta):>6} B')

    # La marca suelta para dentro de la app (pantalla de login). El lienzo es cuadrado y
    # la marca va inscrita, de modo que `ContentScale.Fit` en un cuadro de MARCA_DP la
    # dibuja 1:1 sin estirar nada.
    print('\nmarca dentro de la app (sin fondo del badge):')
    for dens, factor in DENSIDADES.items():
        lado = int(round(MARCA_DP * factor))
        esc = min(lado / mw, lado / mh)
        tw, th = max(1, round(mw * esc)), max(1, round(mh * esc))
        escalada = escala_area(recortada, mw, mh, tw, th)
        carpeta = os.path.join(RES, f'drawable-{dens}')
        os.makedirs(carpeta, exist_ok=True)
        for nombre, fuente in (
            ('echo_logo', escalada),
            ('echo_logo_claro', tinta_para_claro(escalada, tw * th)),
        ):
            ruta = os.path.join(carpeta, nombre + '.png')
            write_png(ruta, lado, lado, centra(fuente, tw, th, lado))
            print(f'  {dens:8} {nombre:26} {lado}x{lado}  marca {tw}x{th}  {os.path.getsize(ruta):>6} B')


if __name__ == '__main__':
    main()
