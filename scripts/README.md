# Scripts

A collection of Pythons scripts to automate or generate stuff.

Install requirements in `requirements.txt`.

Run `./all.sh` from this directory to update supporters, fill missing Pirate
translations, and regenerate skins.

## Name Conversion

Convert a name database to a fancy Json list.

## Skin Generator

Generates burnt and zombie variants from skin files, as well as zombie eyes.

Accepts parameter `--path` to specify the clothing/face directory.

## TTS

The old TTS script that uses Google and Amazon TTS.
It has been deprecated in favor of the [online TTS](https://github.com/Luke100000/minecraft-comes-alive/wiki/TTS).

## Fetch Contributors

Update patrons and translators from the public Conczin API.

## Pirate Translator

Fill missing entries in the `en_pt.json` locale files using [Pirate Monkeyness](https://pirate.monkeyness.com/).
Existing translations are preserved, and unchanged files are not rewritten.
