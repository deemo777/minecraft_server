"""
크리에이티브 인벤토리 아이템 순서 뽑기  →  src/main/resources/creative_order.txt

  python make_creative_order.py --jar "$env:APPDATA\\.minecraft\\versions\\26.3\\26.3.jar"

마인크래프트 업데이트로 크리에이티브 순서가 바뀌었을 때만 다시 실행하면 돼요.
(결과 파일은 플러그인 jar 안에 들어가서, 도감이 이 순서대로 보여요. 실행 후 플러그인 다시 빌드)

크리에이티브 탭 내용은 게임 데이터 파일이 아니라 코드(CreativeModeTabs)에 적혀 있어서,
그 코드를 javap 로 풀어서 아이템이 나오는 순서를 읽어요.
필요: JDK의 javap (IntelliJ/Gradle용으로 설치한 JDK 25면 됨), Python 3.10+
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "creative_order.txt")

TABS = ["itemGroup.buildingBlocks", "itemGroup.coloredBlocks", "itemGroup.natural", "itemGroup.functional",
        "itemGroup.redstone", "itemGroup.tools", "itemGroup.combat", "itemGroup.foodAndDrink",
        "itemGroup.ingredients", "itemGroup.spawnEggs", "itemGroup.op"]
COLORS = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
          "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"]
# 코드 속 필드 이름과 실제 아이템 id가 다른 것
FIELD_FIX = {"dry_short_grass": "short_dry_grass", "dry_tall_grass": "tall_dry_grass",
             "cut_standstone_slab": "cut_sandstone_slab"}
# 따로 만들어 넣는 함수 → 아이템
CALLS = {"generateOminousBottles": ["ominous_bottle"], "generateSuspiciousStews": ["suspicious_stew"]}


def javap_path():
    p = shutil.which("javap")
    if p:
        return p
    home = os.environ.get("JAVA_HOME")
    if home:
        for name in ("javap.exe", "javap"):
            c = os.path.join(home, "bin", name)
            if os.path.exists(c):
                return c
    sys.exit("javap 를 못 찾았어요. JDK를 설치하거나 JAVA_HOME 을 설정해 주세요.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", required=True, help="마인크래프트 클라이언트 jar (.minecraft/versions/<버전>/<버전>.jar)")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()

    tmp = tempfile.mkdtemp()
    try:
        with zipfile.ZipFile(args.jar) as z:
            z.extract("net/minecraft/world/item/CreativeModeTabs.class", tmp)
            version = z.read("version.json").decode("utf-8") if "version.json" in z.namelist() else ""
        txt = subprocess.run([javap_path(), "-v", "-p", "-cp", tmp, "net.minecraft.world.item.CreativeModeTabs"],
                             capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if "CreativeModeTabs" not in txt:
        sys.exit("javap 결과가 비었어요. JDK 버전이 마인크래프트(Java 25)보다 낮은지 확인하세요.")

    # 람다 번호 → 메서드 이름
    bsm = {}
    for m in re.finditer(r"^\s+(\d+): #\d+ REF_invokeStatic java/lang/invoke/LambdaMetafactory.*?\n\s+Method arguments:\n.*?\n"
                         r"\s+#\d+ REF_\w+ net/minecraft/world/item/CreativeModeTabs\.([\w$]+):", txt, re.M):
        bsm[int(m.group(1))] = m.group(2)
    # 메서드 본문
    bodies = {}
    for m in re.finditer(r"^  (?:private |public |protected )?(?:static )?[\w.$<>\[\], ?]+ ([\w$]+)\(([^)]*)\);\n((?:(?!^  \S).*\n)*)",
                         txt, re.M):
        bodies.setdefault(m.group(1), []).append(m.group(3))

    def tokens(name, depth=0):
        out = []
        for body in bodies.get(name, []):
            for line in body.split("\n"):
                g = re.search(r"getstatic\s+#\d+\s+// Field net/minecraft/world/item/Items\.(\w+):L([\w/$]+);", line)
                if g:
                    kind = "item" if g.group(2) == "net/minecraft/world/item/Item" else g.group(2).split("/")[-1]
                    out.append((kind, g.group(1)))
                    continue
                g = re.search(r"invokedynamic\s+#\d+,\s+0\s+// InvokeDynamic #(\d+):", line)
                if g and depth < 5:
                    ln = bsm.get(int(g.group(1)))
                    if ln:
                        out += tokens(ln, depth + 1)
                    continue
                g = re.search(r"invokestatic\s+#\d+\s+// Method (?:net/minecraft/world/item/CreativeModeTabs\.)?(\w+):", line)
                if g:
                    out.append(("call", g.group(1)))
        return out

    # bootstrap 에서 탭 이름 다음에 나오는 displayItems 람다 찾기
    boot = "\n".join(bodies.get("bootstrap", []))
    tab_lambda = {}
    cur = None
    for line in boot.split("\n"):
        g = re.search(r"// String (itemGroup\.\w+)", line)
        if g:
            cur = g.group(1)
            continue
        g = re.search(r"invokedynamic\s+#\d+,\s+0\s+// InvokeDynamic #(\d+):accept:", line)
        if g and cur and cur not in tab_lambda:
            tab_lambda[cur] = bsm.get(int(g.group(1)))

    order, seen = [], set()

    def add(n):
        n = FIELD_FIX.get(n, n)
        if n not in seen:
            seen.add(n)
            order.append(n)

    for tab in TABS:
        if tab not in tab_lambda:
            print(f"  ! {tab} 탭을 못 찾았어요 (건너뜀)")
            continue
        for kind, v in tokens(tab_lambda[tab]):
            if kind == "item":
                add(v.lower())
            elif kind == "ColorCollection":
                base = v.lower().replace("dyed_", "")
                for col in COLORS:
                    add(f"{col}_{base}")
            elif kind == "call":
                for n in CALLS.get(v, []):
                    add(n)

    ver = re.search(r'"id"\s*:\s*"([^"]+)"', version)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# 크리에이티브 인벤토리 순서 (마인크래프트 {ver.group(1) if ver else '?'}) — tools/make_creative_order.py 로 생성\n")
        f.write("\n".join(order) + "\n")
    print(f"완료: {os.path.abspath(args.out)}  ({len(order)}개)")
    print("플러그인을 다시 빌드하면 도감이 이 순서로 보여요.")


if __name__ == "__main__":
    main()
