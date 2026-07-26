import json
from pathlib import Path

import requests


# noinspection SpellCheckingInspection
replacements = {
    "ALEJANDRO MARCELO PAUCAR CASTILLO": "Alejandro Castillo",
    "Betancourt Guerrero Ramón": "Betancourt Ramón",
    "Marco Antônio Gonçalves Aragão": "Marco Aragão",
    "Manuel Giménez González": "Manuel González",
    "juan marcelo casas gonzales": "Juan Gonzales",
    "Jorge Quirós Fernández": "Jorge Fernández",
    "Eddiee Ethian Quiriz Loaiza": "Eddiee Quiriz",
    "Felipe Lizarrague Antona": "Felipe Antona",
    "[PT] Mlg Magic Hoodini [MemesFTW]": "Mlg Magic Hoodini",
    "Dalek_Caan_2001 Sharks of sliver": "Sharks of sliver",
    "Maicon Alan de Aviz Santos": "Maicon Santos",
}

SUPPORTERS_DIR = (
    Path(__file__).resolve().parents[1]
    / "common/src/main/resources/assets/mca/api/supporters"
)


def fetch_names(group, endpoint):
    response = requests.get(f"https://api.conczin.net/v1/{endpoint}", timeout=30)
    response.raise_for_status()
    names = [replacements.get(name, name) for name in response.json()]

    with (SUPPORTERS_DIR / f"{group}.json").open("w", encoding="utf-8") as output:
        json.dump(names, output, indent=2)
        output.write("\n")


if __name__ == "__main__":
    fetch_names("translators", "translator_names")
    fetch_names("patrons", "patron_names")
