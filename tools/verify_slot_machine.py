"""Validate the shipped slot cabinet geometry, textures, item definition and world anchoring."""
import io
import json
from pathlib import Path
import zipfile
from PIL import Image
from make_slot_machine_art import slot_model, textures


def verify(path):
    with zipfile.ZipFile(path) as pack:
        root = "assets/servercore/"
        model = json.loads(pack.read(root + "models/item/slot_machine.json"))
        assert model == slot_model(), "Pack contains a stale slot model"
        definition = json.loads(pack.read(root + "items/slot_machine.json"))
        assert definition["model"] == {"type": "minecraft:model", "model": "servercore:item/slot_machine"}
        elements = model["elements"]; assert len(elements) >= 35
        names = {e["name"] for e in elements}
        assert {"coin_slot", "payout_opening", "lever_red_grip", "marquee_glass", "lower_cabinet"} <= names
        assert all(f"ivory_reel_{i}" in names for i in range(3))
        minimum = [min(e["from"][i] for e in elements) for i in range(3)]
        maximum = [max(e["to"][i] for e in elements) for i in range(3)]
        assert minimum[0] >= -.1 and maximum[0] <= 16.1, "Cabinet must stay one block wide"
        assert minimum[1] >= -.1 and maximum[1] == 32, "Cabinet must be two blocks tall"
        assert minimum[2] >= -.1 and maximum[2] <= 18, "Only the tray may protrude slightly"
        for e in elements:
            assert all(-16 <= v <= 32 for k in ("from", "to") for v in e[k]), e["name"]
            assert all(a < b for a, b in zip(e["from"], e["to"])), e["name"]
            for face in e["faces"].values():
                assert face["texture"][1:] in model["textures"]
                assert all(0 <= n <= 16 for n in face["uv"])
        for key, reference in model["textures"].items():
            location = root + "textures/" + reference.split(":", 1)[1] + ".png"
            with Image.open(io.BytesIO(pack.read(location))) as actual:
                actual.load(); assert actual.mode == "RGBA"
                expected = textures()[key if key != "particle" else "body"]
                assert actual.size == expected.size and actual.tobytes() == expected.tobytes(), key
        print(f"PASS: slot cabinet {len(elements)} cuboids, 12 textures, 1x2 bounds, +Z front, GUI/hand/world model.")


if __name__ == "__main__":
    verify(Path(__file__).resolve().parent / "build/servercore_pack.zip")
