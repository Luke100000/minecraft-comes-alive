import json
import urllib.parse
import urllib.request
from pathlib import Path

from tqdm.contrib.concurrent import thread_map


def translate(text):
    request = (
        "https://pirate.monkeyness.com/api/translate?english="
        + urllib.parse.quote(text)
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return urllib.parse.unquote(response.read().decode("utf-8"))


def load_json(path):
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def translate_missing(asset_dir):
    lang_dir = asset_dir / "lang"
    phrases = load_json(lang_dir / "en_us.json")
    pirate_path = lang_dir / "en_pt.json"
    pirate_phrases = load_json(pirate_path)
    missing = [(key, text) for key, text in phrases.items() if key not in pirate_phrases]

    if not missing:
        return

    keys, texts = zip(*missing)
    pirate_phrases.update(zip(keys, thread_map(translate, texts)))

    with pirate_path.open("w", encoding="utf-8") as output:
        json.dump(pirate_phrases, output, indent=2, ensure_ascii=False)
        output.write("\n")


if __name__ == "__main__":
    assets_dir = Path(__file__).resolve().parents[1] / "common/src/main/resources/assets"
    for asset in ("mca_books", "mca_dialogue", "mca"):
        translate_missing(assets_dir / asset)
