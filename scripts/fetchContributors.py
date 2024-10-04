import json
import os
import time

import requests
from crowdin_api import CrowdinClient
from crowdin_api.api_resources.reports.enums import Format, Unit
from crowdin_api.exceptions import NotFound
from dotenv import load_dotenv

load_dotenv()


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
}


class MCACrowdinClient(CrowdinClient):
    TOKEN = os.getenv("CROWDIN_KEY")
    PROJECT_ID = 456324


def fetch_translators():
    client = MCACrowdinClient()

    # Request a new report
    report_request = client.reports.generate_top_members_report(
        unit=Unit.WORDS,
        format=Format.JSON,
    )
    identifier = report_request["data"]["identifier"]

    # Download the report
    while True:
        try:
            report = client.reports.download_report(identifier)
            url = report["data"]["url"]
            raw_data = requests.get(url).content
            members = json.loads(raw_data)
            break
        except NotFound:
            print("Report not found, waiting...")
            time.sleep(1)

    min_actions = 10
    ignored = 0

    names = []
    for m in members["data"]:
        if (
            m["translated"] + m["approved"] + m["voted"] > min_actions
            and m["user"]["username"] != "Luke100000"
        ):
            name = m["user"]["fullName"]

            name = name.split("(")[0].strip()

            if name in replacements:
                name = replacements[name]

            names.append(name)

            if len(name) > 22:
                print(f"Name too long: {name}")
        else:
            ignored += 1

    with open(
        "../common/src/main/resources/assets/mca/api/supporters/translators.json", "w"
    ) as f:
        json.dump(names, f, indent=2)


def fetch_patrons():
    patrons = requests.get("https://api.conczin.net/v1/patron_names").json()

    names = []
    for name in patrons:
        if name in replacements:
            name = replacements[name]

        if len(name) > 22:
            print(f"Name too long: {name}")

        names.append(name.strip())

    with open(
        "../common/src/main/resources/assets/mca/api/supporters/patrons.json", "w"
    ) as f:
        json.dump(names, f, indent=2)


if __name__ == "__main__":
    fetch_translators()
    fetch_patrons()
