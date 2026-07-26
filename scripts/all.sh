source .venv/bin/activate

python fetch_contributors.py
python pirate_translator.py

cd skins
python clothing_generator.py
python face_generator.py
