"""Build disabled collection icons from vanilla model geometry and darkened textures."""
import copy
import io
import json
from pathlib import Path
from PIL import Image, ImageEnhance

HEAD_TEXTURES = {
    "skeleton": "skeleton/skeleton", "wither_skeleton": "skeleton/wither_skeleton",
    "zombie": "zombie/zombie", "creeper": "creeper/creeper",
    "piglin": "piglin/piglin", "dragon": "enderdragon/dragon",
}

class DisabledModels:
    def __init__(self, jar, root):
        self.jar, self.root = jar, Path(root)
        self.models, self.textures = {}, {}

    def write(self, path, value):
        path = self.root / path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=True, indent=1), encoding="utf-8")

    def texture(self, reference, destination=None):
        namespace, path = reference.split(":", 1) if ":" in reference else ("minecraft", reference)
        destination = destination or f"disabled/{namespace}/{path}"
        result = f"servercore:{destination}"
        if result in self.textures:
            return result
        source = f"assets/{namespace}/textures/{path}.png"
        image = Image.open(io.BytesIO(self.jar.z.read(source))).convert("RGBA")
        alpha = image.getchannel("A")
        image = ImageEnhance.Color(image).enhance(.15)
        image = ImageEnhance.Brightness(image).enhance(.30)
        image.putalpha(alpha)
        output = self.root / f"textures/{destination}.png"
        output.parent.mkdir(parents=True, exist_ok=True)
        image.save(output)
        metadata = source + ".mcmeta"
        if metadata in self.jar.names:
            Path(str(output) + ".mcmeta").write_bytes(self.jar.z.read(metadata))
        self.textures[result] = source
        return result

    def model(self, reference):
        namespace, path = reference.split(":", 1) if ":" in reference else ("minecraft", reference)
        if path.startswith("builtin/"):
            return reference
        if reference in self.models:
            return self.models[reference]
        result = f"servercore:disabled/{namespace}/{path}"
        self.models[reference] = result
        model = self.jar.json(f"assets/{namespace}/models/{path}.json")
        if model is None:
            raise ValueError(f"Missing vanilla model: {reference}")
        model = copy.deepcopy(model)
        if "parent" in model:
            model["parent"] = self.model(model["parent"])
        for key, texture in model.get("textures", {}).items():
            if not texture.startswith("#"):
                model["textures"][key] = self.texture(texture)
        self.write(f"models/disabled/{namespace}/{path}.json", model)
        return result

    def item(self, node):
        if isinstance(node, list):
            return [self.item(value) for value in node]
        if not isinstance(node, dict):
            return node
        result = {key: self.item(value) for key, value in node.items()}
        if node.get("type") == "minecraft:model":
            result["model"] = self.model(node["model"])
        elif node.get("type") == "minecraft:special" and node["model"]["type"] == "minecraft:head":
            path = HEAD_TEXTURES[node["model"]["kind"]]
            self.texture(f"minecraft:entity/{path}", f"entity/disabled/{path}")
            result["model"]["texture"] = f"servercore:disabled/{path}"
        return result
