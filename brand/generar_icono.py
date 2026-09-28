"""Genera las capas del icono adaptativo de Echo a partir del logo maestro.

Uso:  python3 brand/generar_icono.py        (desde la raiz del repo o desde brand/)

Entrada:  brand/echo-logo.png  -- badge con fondo y marca pegados (fuente de verdad)
Salida:   app/src/main/res/drawable-*dpi/ic_launcher_foreground.png  (marca centrada)
          app/src/main/res/drawable-*dpi/ic_launcher_monochrome.png  (silueta tematica)
          app/src/main/res/drawable-*dpi/echo_logo.png              (marca, tema oscuro)
          app/src/main/res/drawable-*dpi/echo_logo_claro.png        (marca, tema claro)

Por que separar el fondo de la marca: el logo maestro viene "compuesto", con el fondo
llegando al borde del lienzo y la marca casi al 93% del ancho. En un icono adaptativo
el lanzador recorta con su propia mascara y solo sobrevive el 72dp central de los 108dp,
asi que usar el badge entero como foreground cortaria las ondas azules. Por eso el fondo
se mide (el anillo exterior del maestro es fondo puro) y la marca se escala para que
toda su tinta quepa en la zona segura. El fondo se queda como capa aparte, el vector de
`drawable/ic_launcher_background.xml`, que este script no escribe pero cuyo color imprime
para que se mantenga al dia a mano.

El decoder y el encoder PNG estan implementados a mano porque el logo hay que medirlo
y recortarlo, y en esta maquina no hay Pillow ni ImageMagick. Sin dependencias.
"""
import math
import os
import struct
import sys
import zlib


def load(path):
    """Devuelve (ancho, alto, filas) con las filas ya normalizadas a RGBA de 4 canales.

    El maestro se exporta a veces como RGB y otras como RGBA, y `extrae_marca` lee
    cuatro canales por pixel: normalizar aqui evita que el desajuste se manifieste
    como una imagen corrida en vez de como un error obvio.
    """
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n', 'no es un png'
    pos, idat, ihdr, paleta = 8, b'', None, b''
    while pos < len(d):
        ln, typ = struct.unpack('>I4s', d[pos:pos + 8])
        body = d[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            ihdr = struct.unpack('>IIBBBBB', body)
        elif typ == b'PLTE':
            paleta = body
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

    if color == 6:
        return w, h, [bytes(f) for f in out]
    rgba = bytearray(w * h * 4)
    for y, f in enumerate(out):
        o = y * w * 4
        if color == 2:                       # RGB opaco
            for x in range(w):
                s, d = x * 3, o + x * 4
                rgba[d] = f[s]; rgba[d + 1] = f[s + 1]; rgba[d + 2] = f[s + 2]
                rgba[d + 3] = 255
        elif color == 0:                     # gris opaco
            for x in range(w):
                d = o + x * 4
                rgba[d] = rgba[d + 1] = rgba[d + 2] = f[x]; rgba[d + 3] = 255
        elif color == 4:                     # gris + alfa
            for x in range(w):
                d = o + x * 4
                rgba[d] = rgba[d + 1] = rgba[d + 2] = f[x * 2]
                rgba[d + 3] = f[x * 2 + 1]
        else:                                # paleta: se reconstruye desde PLTE
            assert paleta, 'png con paleta sin PLTE'
            for x in range(w):
                i = f[x]
                d = o + x * 4
                rgba[d] = paleta[i * 3]; rgba[d + 1] = paleta[i * 3 + 1]
                rgba[d + 2] = paleta[i * 3 + 2]; rgba[d + 3] = 255
    return w, h, [bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h)]




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
# Radio, en dp sobre el lienzo de 108, dentro del cual debe caber TODA la tinta de la
# marca. 36 es el circulo inscrito del area visible de 72dp: es el criterio que venia a
# aplicar el 0.66 del lado que usaba antes, y para una marca cuadrada daria lo mismo
# (0.66 del lado son 35.6dp de semiancho, contra 36dp de radio). 33 es la zona segura
# estricta que pide Google (66dp de diametro): deja el icono mas pequeno a cambio de que
# ningun lanzador de OEM lo recorte. Al final de la corrida se imprime cuanto recorte
# deja cada uno.
RADIO_SEGURO_DP = 36

# El fondo del maestro se toma del anillo exterior, que es fondo puro: su variacion
# medida es la zona muerta del extractor, para no comerse el borde de la marca. El
# margen y la rampa convierten "distancia al fondo" en alfa, y con eso el
# antialiasing del maestro se conserva sin difuminar a mano.
FONDO_MARGEN = 6
FONDO_RAMPA = 30
UMBRAL_ALFA = 128      # para medir la marca: mitad de la rampa
_TABLA_BINARIA = bytes(1 if i >= UMBRAL_ALFA else 0 for i in range(256))

# Marca dentro de la app: aqui si se muestra SIN el fondo del badge, sobre el lienzo del
# tema, que es lo que se pidio para la pantalla de login. Como el glifo del maestro es
# blanco, sobre el lienzo claro (#EEF1F7) seria invisible, asi que se generan dos
# variantes y `MarcaEcho` elige con `LocalTemaOscuro`. La variante clara no repinta el
# maestro entero: solo mapea a tinta el glifo (poco saturado Y claro) y deja intactas
# las ondas azules y el filete gris, que si se leen sobre ambos lienzos.
MARCA_DP = 84
TINTA_CLARA = (0x1A, 0x21, 0x30)
SAT_GLIFO = 60      # azul de las ondas: sat ~142; glifo blanco: sat ~0
BRILLO_TINTA = 190  # el glifo (~255) se tinta; el filete gris (~128) se queda gris


def fondo_del_anillo(filas, w, h, grosor=0.02):
    """Mide el fondo en el anillo exterior: color dominante y su variacion por canal.

    El anillo exterior es fondo puro: no toca la marca, y da el color de fondo con un
    margen de error igual a su propia variacion. En un fondo plano esa variacion es
    cero y el extractor puede ser agresivo; en un degradado es el rango del degradado
    y el extractor lo respeta sin comerse el borde de la marca.
    """
    m = max(2, int(min(w, h) * grosor))
    pts = []
    for y in range(h):
        for x in range(w):
            if y < m or y >= h - m or x < m or x >= w - m:
                f = filas[y]
                if f[x * 4 + 3] == 255:      # transparente: no dice nada del fondo
                    pts.append((f[x * 4], f[x * 4 + 1], f[x * 4 + 2]))
    assert pts, 'el anillo exterior del maestro es transparente'
    dom = tuple(sum(c[i] for c in pts) // len(pts) for i in range(3))
    varia = max(max(abs(c[i] - dom[i]) for i in range(3)) for c in pts)
    return dom, varia


def _distancia(canal, valor):
    """Por cada pixel, |canal - valor| como un plano de bytes (translate es de C)."""
    return canal.translate(bytes(min(255, abs(i - valor)) for i in range(256)))


def extrae_marca(w, h, filas, fondo, variacion):
    """Separa la marca del fondo: alfa = cuanto se aparta del color de fondo.

    Fondo y marca son dos colores planos y muy distintos (#112439 contra azul
    saturado y blanco), asi que la distancia al fondo los separa sin ambiguedad. La
    zona muerta cubre la variacion del anillo, para que un fondo con degradado no
    se convierta en tinta.
    """
    plano = b''.join(filas)
    dR = _distancia(plano[0::4], fondo[0])
    dG = _distancia(plano[1::4], fondo[1])
    dB = _distancia(plano[2::4], fondo[2])
    muerta = variacion + FONDO_MARGEN
    tope = muerta + FONDO_RAMPA
    alfa = bytes(0 if d <= muerta else (255 if d >= tope else (d - muerta) * 255 // FONDO_RAMPA)
                 for d in map(max, zip(dR, dG, dB)))
    if min(plano[3::4]) < 255:      # maestro con transparencia: el alfa manda
        alfa = bytes(a * b // 255 for a, b in zip(alfa, plano[3::4]))

    px = bytearray(w * h * 4)
    px[0::4] = plano[0::4]
    px[1::4] = plano[1::4]
    px[2::4] = plano[2::4]
    px[3::4] = alfa
    # El color observado ya esta "manchado" por el fondo en los bordes; como el
    # fondo destino es el mismo, el error es invisible y no hace falta deshacer la
    # premultiplicacion.
    return px


def _binaria(px, w, h):
    return px[3::4].translate(_TABLA_BINARIA)


def bbox(px, w, h):
    xs, ys = [], []
    binaria = _binaria(px, w, h)
    for y in range(h):
        fila = binaria[y * w:(y + 1) * w]
        a = fila.find(1)
        if a < 0:
            continue
        b = fila.rfind(1)
        if xs:
            xs[0] = min(xs[0], a); xs[1] = max(xs[1], b)
            ys[0] = min(ys[0], y); ys[1] = max(ys[1], y)
        else:
            xs = [a, b]; ys = [y, y]
    assert xs, 'no se encontro ninguna marca: el maestro se cambio?'
    return xs[0], ys[0], xs[1], ys[1]


def radio_maximo(px, w, h, cx, cy):
    """Distancia maxima, en pixeles, del centro a la tinta.

    No se usa la diagonal del bbox: el esquinas del bbox de una marca ancha estan
    vacias, y medirlas como si tuvieran tinta encogeria el icono sin necesidad. Por
    cada fila basta con mirar su pixel de tinta mas a la izquierda y el mas a la
    derecha, que son los dos puntos mas alejados del centro en esa fila.
    """
    binaria = _binaria(px, w, h)
    r = 0.0
    for y in range(h):
        fila = binaria[y * w:(y + 1) * w]
        a = fila.find(1)
        if a < 0:
            continue
        b = fila.rfind(1)
        for x in (a, b):
            d = math.hypot(x - cx, y - cy)
            if d > r:
                r = d
    return r


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
    """Pega la marca escalada en un lienzo cuadrado, centrado, con alfa."""
    lienzo = bytearray(lado * lado * 4)
    ox, oy = (lado - mw) // 2, (lado - mh) // 2
    for y in range(mh):
        dst = ((oy + y) * lado + ox) * 4
        src = y * mw * 4
        lienzo[dst:dst + mw * 4] = px[src:src + mw * 4]
    return lienzo


def tinta_para_claro(px, n):
    """Variante de tema claro: el glifo pasa a tinta oscura, las ondas azules se quedan.

    Solo se repinta lo que es a la vez poco saturado y claro, para que el glifo blanco
    se vuelva tinta y el filete gris que lo rodea conserve su propio tono. El alfa del
    maestro se respeta, asi que el antialiasing del glifo se conserva y no hay que
    difuminar a mano.
    """
    salida = bytearray(px)
    for i in range(n):
        o = i * 4
        if salida[o + 3] == 0:
            continue
        R, G, B = salida[o], salida[o + 1], salida[o + 2]
        if max(R, G, B) - min(R, G, B) < SAT_GLIFO and max(R, G, B) >= BRILLO_TINTA:
            salida[o], salida[o + 1], salida[o + 2] = TINTA_CLARA
    return salida


def simula_recorte(px, lado):
    """Cuanta la tinta que se sale de las zonas seguras, en el lienzo ya escalado.

    Se hace sobre el lienzo generado y no sobre el maestro porque es lo que ve el
    lanzador. 72dp es el area visible de un icono adaptativo (el circulo inscrito, la
    referencia) y 66dp la zona segura estricta; el squircle es la mascara cuadrada con
    esquinas redondeadas que usan la mayoria de los lanzadores.
    """
    binaria = _binaria(px, lado, lado)
    w = lado
    centro = (lado - 1) / 2.0
    k = CANVAS_DP / lado
    dentro = [0, 0, 0, 0]
    for y in range(lado):
        fila = binaria[y * lado:(y + 1) * lado]
        a = fila.find(1)
        if a < 0:
            continue
        b = fila.rfind(1)
        dy = (y - centro) * k
        for x in range(a, b + 1):
            if not fila[x]:
                continue
            dx = (x - centro) * k
            r = math.hypot(dx, dy)
            if r <= 36.0:
                dentro[0] += 1
            if r <= 33.0:
                dentro[1] += 1
            if abs(dx / 36.0) ** 4 + abs(dy / 36.0) ** 4 <= 1:
                dentro[2] += 1
            if abs(dx / 33.0) ** 4 + abs(dy / 33.0) ** 4 <= 1:
                dentro[3] += 1
    total = sum(1 for v in binaria if v)
    if not total:
        return
    print('\nrecorte de la tinta por el lanzador (lienzo de 108dp):')
    for nombre, i in (('circulo 72dp (referencia)', 0), ('circulo 66dp (estricto)', 1),
                      ('squircle 72dp', 2), ('squircle 66dp', 3)):
        pct = (1 - dentro[i] / total) * 100
        print(f'  {nombre:26} {pct:6.2f}% fuera')


def main():
    w, h, filas = load(MAESTRO)
    print(f'maestro: {w}x{h}')

    fondo, variacion = fondo_del_anillo(filas, w, h)
    print(f'fondo medido (anillo exterior): #%02X%02X%02X, variacion {variacion} por canal'
          % fondo)
    if variacion == 0:
        print(f'  -> el fondo es plano: `drawable/ic_launcher_background.xml` debe ser un '
              f'relleno #FF{fondo[0]:02X}{fondo[1]:02X}{fondo[2]:02X}, sin degradado')
    else:
        print('  -> el fondo tiene degradado: el vector de fondo debe medirlo aparte')

    marca = extrae_marca(w, h, filas, fondo, variacion)
    x0, y0, x1, y1 = bbox(marca, w, h)
    mw, mh = x1 - x0 + 1, y1 - y0 + 1
    print(f'marca extraida: bbox x{x0}..{x1} y{y0}..{y1} -> {mw}x{mh}  '
          f'(x {x0 / w:.1%}..{x1 / w:.1%}, y {y0 / h:.1%}..{y1 / h:.1%} del lienzo)')

    recortada = bytearray(mw * mh * 4)
    for y in range(mh):
        s = ((y0 + y) * w + x0) * 4
        recortada[y * mw * 4:(y + 1) * mw * 4] = marca[s:s + mw * 4]

    # La escala del icono sale de la zona segura y no de un porcentaje del lado: la
    # marca es 1.26 mas ancha que alta, y un porcentaje del lado dejaria las ondas
    # azules tocandose con el recorte del lanzador.
    rmax = radio_maximo(recortada, mw, mh, (mw - 1) / 2.0, (mh - 1) / 2.0)
    print(f'  radio maximo de la tinta: {rmax:.0f}px = {rmax / w * CANVAS_DP:.1f}dp de 108 '
          f'(el bbox entero daria {math.hypot(mw / 2.0, mh / 2.0) / w * CANVAS_DP:.1f}dp)')

    mayor = None
    for dens, factor in DENSIDADES.items():
        lado = int(round(CANVAS_DP * factor))
        objetivo = lado * RADIO_SEGURO_DP / CANVAS_DP
        esc = min(objetivo / rmax, lado / mw, lado / mh)
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
            if nombre == 'ic_launcher_foreground' and (mayor is None or lado > mayor[0]):
                mayor = (lado, lienzo)

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

    if mayor:
        simula_recorte(mayor[1], mayor[0])


if __name__ == '__main__':
    main()
