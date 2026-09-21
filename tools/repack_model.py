#!/usr/bin/env python3
"""
Reempaqueta un modelo de sherpa-onnx (.tar.bz2) al .zip que espera la app.

Android no abre .tar.bz2 sin librerías extra, así que el modelo se sube a
GitHub Releases como .zip. Este script hace la conversión y deja los archivos
en la raíz del zip, que es como los espera SherpaModelInstaller.

Uso:
    python repack_model.py vits-piper-en_US-libritts_r-medium.tar.bz2 en_us_libritts_r.zip

Opciones:
    --sin-espeak   No incluye espeak-ng-data (~19 MB). Úsalo SOLO para el
                   segundo modelo en adelante: la app comparte la carpeta del
                   primero. El primer modelo que descargue el alumno SÍ la
                   necesita.

El nombre del .zip debe coincidir con downloadFileName en SherpaVoiceCatalog.kt.
"""

import argparse
import io
import sys
import tarfile
import zipfile


def repack(source: str, target: str, include_espeak: bool) -> None:
    written = 0
    skipped = 0

    with tarfile.open(source, "r:bz2") as tar:
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
            for member in tar.getmembers():
                if not member.isfile():
                    continue

                # Quita la carpeta raíz del tar: los archivos van planos.
                parts = member.name.split("/")
                relative = "/".join(parts[1:]) if len(parts) > 1 else parts[0]

                if not relative:
                    continue

                if not include_espeak and relative.startswith("espeak-ng-data/"):
                    skipped += 1
                    continue

                # MODEL_CARD y el .json no los usa la app, pero pesan nada y
                # sirven para saber después qué modelo es este zip.
                extracted = tar.extractfile(member)
                if extracted is None:
                    continue

                data = extracted.read()
                zf.writestr(relative, data)
                written += 1

                if relative.endswith(".onnx"):
                    print(f"  modelo: {relative} ({len(data) / 1_000_000:.0f} MB)")

    print(f"Listo: {target} ({written} archivos, {skipped} omitidos de espeak)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", help="archivo .tar.bz2 descargado de sherpa-onnx")
    parser.add_argument("target", help="archivo .zip de salida")
    parser.add_argument(
        "--sin-espeak",
        action="store_true",
        help="omite espeak-ng-data (solo para modelos adicionales)",
    )
    args = parser.parse_args()

    repack(args.source, args.target, include_espeak=not args.sin_espeak)
    return 0


if __name__ == "__main__":
    sys.exit(main())
