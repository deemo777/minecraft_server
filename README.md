# ServerCore 3.0 — 스타듀밸리 메뉴 + 수집품 도감 (상자 GUI)

Paper **26.3** / Java 25. 플레이어는 바닐라 클라이언트 + 서버 리소스팩.
상자 GUI 제목 자리에 리소스팩 배경 그림을 깔아서 꾸미는 방식이라, 다른 화면(Esc 메뉴 등)에는 영향이 없어요.

## 무엇이 들어 있나
| 기능 | 조작 |
|---|---|
| 마을 메뉴 (칠판 간판, 일러스트 큰 버튼 5개 — 가운데 워프) | **Shift + F**, `/메뉴` |
| 도감 (수집품 판, 한 페이지 45개) | 메뉴의 📖 도감, `/도감 [페이지]` |
| 도감 등록 | 항목 칸 클릭 → 가방에 `required`개가 있으면 그만큼 가져가고 등록 (체크 표시) |
| 도감 순서 | 크리에이티브 인벤토리 순서 (`dogam.yml` 의 `order: creative`, `file` 이면 파일 순서) |
| 페이지 이동 | 맨 아래 줄 양 끝 ◀ ▶ (사이에 진행도 게이지·페이지) |
| 점프 패드 | `/점프패드 [개수] [플레이어]` (OP)로 받은 아이템을 설치 → 밟으면 앞+위로 튕김. 모양은 중량 무게 압력판과 같음. 수치는 `config.yml` → `jump-pad` |
| 도약 | 달리면서 점프하면 멀리 뛰기 (쿨타임 5초). 수치는 `config.yml` → `long-jump` |
| 골드 | 처음 접속 1,000G. `/money` 보기, `/money <금액>` 골드 주머니로 꺼내기(우클릭하면 다시 넣기), `/money give/take/set` (OP). 잔액은 `money.yml` |
| NPC 상점 | `/shopnpc create <fish/crops/ores>` 로 상점 주인(주민) 세우기 → 클릭하면 테마 상점. 주민 교환처럼 [아이템]→[골드] 줄이 왼쪽 5개·오른쪽 5개. 골드 칸 클릭 = 1개 팔고 골드 주머니 받기(Shift 전부). 품목·가격은 `shops.yml` |
| 레벨링 | 농사·채광·벌목·낚시·전투. 경험치 표는 `skills.yml`, `/skill` 또는 메뉴의 내 얼굴 → 스킬 창, `/skill info` 채팅, `/skill set <플레이어> <분야> <레벨>` (OP) |
| HUD | 왼쪽 위 얼굴·이름·골드·레벨 + 경험치 얻으면 위에 경험치 바. `/hud` 켜기/끄기, `/hud left·right·pos·reset` 으로 각자 위치 맞추기 (기본값 `hud.offset-x`) |
| 이장님 | 로비에 `/mayornpc create` 로 세우기. 처음 말을 걸면 대화창(다음 ▶)을 넘기고 마지막에 **집터** 지급, 그 뒤로는 **빚 갚기 창**. 대사는 `config.yml` → `mayor` |
| 칭호 | 분야마다 10레벨마다 칭호 (`skills.yml` → `titles`), `/칭호` 로 바꾸기 → 채팅·탭 목록 이름 앞에 |
| 내 집 땅 | 이장님께 **집터**(작은 집 모형)를 받아 → 설치한 청크가 바로 내 집(집값은 빚). 문이 놓은 사람 쪽을 보고, 지도도 그 방향 기준(위 = 놓을 때 바라본 방향). 집터 우클릭(또는 `/land`) → 청크 지도 창: 붙은 청크를 골라 한 칸씩 넓히기(최대 4, 집터에서 상하좌우 2칸 안, 넓힐 때마다 빚, 빚을 다 갚아야 다음 확장, 빚은 이장님께 갚기). 집터는 못 부숨. 지도 창의 이웃 버튼 → 서버 사람 목록에서 클릭해 허가 ✔(전부 가능) 또는 `/land trust <이름>`, `/land kit·home·sethome·show·info·abandon confirm`. 관리자 `/land bypass`, `/land delete <이름>`, `/land give <이름>`. 데이터 `lands.yml`, 설정 `config.yml` → `land` |
| 안내서 | 처음 접속하면 서버 안내서(책) 1권, `/guide` 로 다시 받기. 내용은 `guide.yml` |
| 관리 | `/도감 reset <플레이어>`, `/코어 reload` (OP) |

## 설치 순서
1. **빌드**: IntelliJ → Gradle 탭 → `build` → `build/libs/ServerCore-<버전>.jar` → 서버 `plugins` 에 넣고 **예전 ServerCore jar는 꼭 삭제** (버전은 `build.gradle.kts`, 바뀐 내용은 `CHANGELOG.md`)
2. **서버 설정 파일 교체**: 서버의 `plugins/ServerCore/` 에서 `menu.yml`, `dogam.yml` 을 이 프로젝트의
   `src/main/resources/` 것으로 바꾸기 (예전 menu.yml은 형식이 달라요. 그대로 두면 기본 메뉴로 동작하고 콘솔에 경고)
3. **리소스팩 만들기** (내 컴퓨터, tools 폴더에서)
   ```
   pip install pillow pyyaml
   python make_art.py          ← 그림 다시 그릴 때만 (tools/art 에 이미 들어 있음)
   python build_pack.py --jar "$env:APPDATA\.minecraft\versions\26.3\26.3.jar" --dogam "서버\plugins\ServerCore\dogam.yml"
   ```
4. **리소스팩 올리기**: `tools/build/servercore_pack.zip` 업로드 → `server.properties` 의 `resource-pack`, `resource-pack-sha1` 수정 → 재시작

### 언제 리소스팩을 다시 만들어야 하나
- `dogam.yml` 에 **새 아이템 종류**를 추가했을 때 (체크 모델이 아이템별로 들어가요)
- `make_art.py` 로 그림을 바꿨을 때
- `required` 숫자, 이름, 설명, 순서만 바꿨다면 → `/코어 reload` 로 끝

## 화면 구성
```
도감 (6줄)                                  메뉴 (5줄)
열: 0 1 2 3 4 5 6 7 8                       열: 0~2       3~5       6~8
줄0 ■ ■ ■ ■ ■ ■ ■ ■ ■                       줄0 [머리] ♥ [어서 오세요!] ♥ [닫기]
줄1 ■ ■ ■ ■ ■ ■ ■ ■ ■                       줄1 [📖 도감 ] [         ] [🛒 상점  ]
줄2 ■ ■ ■ ■ ■ ■ ■ ■ ■   ← 한 페이지 45개     줄2 [        ] [ ✦ 워프  ] [        ]
줄3 ■ ■ ■ ■ ■ ■ ■ ■ ■                       줄3 [🏠 집으로] [         ] [🏘 마을로 ]
줄4 ■ ■ ■ ■ ■ ■ ■ ■ ■                       줄4 [        ] [         ] [        ]
줄5 ◀ [ 진행도 게이지 ][2/8] ▶
```
- 메뉴의 큰 버튼은 여러 칸을 묶은 것이고, 그림은 묶은 칸 영역 밖으로 나가지 않아요. 칸마다 보이지 않는 아이템이 깔려 있어서 클릭을 받아요.
- 어느 칸이 무슨 버튼인지는 `menu.yml`, 버튼 그림(일러스트·이름표)은 `tools/make_art.py` 의 `MENU_TILES`.

## 리소스팩 없이 테스트
`config.yml` → `resourcepack.enabled: false` → `/코어 reload`
바닐라 상자 모양으로 나오고, 등록한 항목은 반짝이로 표시돼요.

## 구조
```
src/main/java/com/htk/core
 ├─ ServerCore, CoreCommand
 ├─ common/
 │   ├─ Glyphs        리소스팩 그림 글자 + 제목에 그림을 좌표로 배치하는 Canvas (⚠ build_pack.py·make_art.py와 약속)
 │   ├─ Gui, GuiListener   상자 GUI 부모 / 클릭 막기·전달
 │   ├─ Items         아이템 만들기 (투명 버튼, 머리, 아이템 모델)
 │   ├─ Placeholders  %dogam_found% 같은 치환 문구
 │   └─ Text, Feature
 ├─ menu/   MenuFeature (Shift+F, /메뉴), MenuGui, MenuConfig (menu.yml)
 ├─ jumppad/ JumpPadFeature (점프 패드)
 ├─ longjump/ LongJumpFeature (달리기 도약)
 ├─ economy/ Economy, EconomyFeature (골드, /money, 골드 주머니)
 ├─ shop/    ShopFeature, ShopGui, ShopConfig (NPC 상점, shops.yml)
 ├─ skill/   SkillFeature, SkillStore, SkillCommand, SkillGui (레벨링·스킬 창, skills.yml)
 ├─ guide/   GuideFeature (서버 안내서 책, guide.yml)
 ├─ land/    LandFeature, LandStore, LandProtection, LandCommand, LandGui, MembersGui (내 집 땅, lands.yml)
 ├─ mayor/   MayorFeature (이장님 NPC: 첫 대화 → 집터, 빚 갚기 창, 대화창 Dialog)
 ├─ hud/     HudFeature (보스바 HUD)
 └─ dogam/  DogamFeature (등록·보상), DogamGui (책 화면), CollectionRegistry, PlayerDataStore, DogamCommand

tools/
 ├─ make_art.py    배경·체크·화살표·막대·숫자 그림 그리기 → art/
 ├─ make_creative_order.py  크리에이티브 인벤토리 순서 뽑기 → src/main/resources/creative_order.txt
 │                          (마인크래프트 업데이트로 순서가 바뀌었을 때만: --jar <버전 jar>, 그다음 플러그인 빌드)
 ├─ build_pack.py  리소스팩 만들기 (폰트, 아이템별 체크 모델, zip, SHA-1)
 └─ art/           그림
```
`common/Dialogs.java`, `dogam/DogamDialog.java`, `menu/QuickActionInstaller.java`, `tools/make_template.py`,
`tools/pack_template/` 은 2.0(대화창 방식)에서 쓰던 것이라 지워도 돼요.

## 리소스팩 약속 (Glyphs.java ↔ build_pack.py ↔ make_art.py)
| 항목 | 값 |
|---|---|
| 폰트 | `servercore:gui` |
| `\uE020`~`\uE023` / `\uE024`~`\uE027` | 도감 / 메뉴 배경 4조각 (왼위·오른위·왼아래·오른아래). 전체 176×(20+222) / 176×(20+204), 위 20px는 간판·꽃. 폰트 그림은 256px를 넘으면 안 그려져서 build_pack.py가 잘라 넣어요 |
| `\uE028`~`\uE02B` / `\uE02C`~`\uE02F` | 스킬 창 / 집 지도 창 배경 4조각 (176×(20+222)) |
| `\uE03C`~`\uE03F` | 이웃 허가 창 배경 4조각 |
| `\uE060`~`\uE064` + 폰트 `servercore:skill1~5` | 스킬 창 줄별 게이지 조각 / 줄별 기본 글자 |
| `\uE002` | 진행도 게이지 조각 4×5, 윗변 y=114 / 시작 x=31, 22칸 |
| `\uE010`~`\uE01A` | 숫자 0~9, `/` (4×7, 윗변 y=113) / 페이지 숫자 가운데 x=136 |
| `\uF801`~`\uF808` / `\uF811`~`\uF818` | 왼쪽 / 오른쪽 1,2,4…128px |
| 아이템 모델 | `servercore:checked/<아이템>`, `servercore:arrow_left(_off)`, `servercore:arrow_right(_off)`, `servercore:invisible`, `servercore:gold_coin`, `servercore:player_head_front`, `servercore:land_plus(_off)`, `servercore:checked_head`, `servercore:house_core` (집터 모형, 문 = +z) |
