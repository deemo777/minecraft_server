# ServerCore 3 — 스타듀밸리풍 마을 서버 플러그인

Paper **26.3** / Java 25. 플레이어는 바닐라 클라이언트 + 서버 리소스팩.
상자 GUI 제목 자리에 리소스팩 배경 그림을 깔아서 꾸미는 방식이라, 다른 화면(Esc 메뉴 등)에는 영향이 없어요.
지금 버전은 `build.gradle.kts` 의 `version`, 바뀐 내용은 `CHANGELOG.md`.

## 서버 구성 (월드 3개)
| 월드 | 하는 일 | 난이도 |
|---|---|---|
| `lobby` 로비 | 처음 오는 곳. **이장님**(집터 받기·빚 갚기), (나중에) 게시판·도감 전시관·점프맵 | 평화로움 |
| `village` 마을 | 상점 NPC, 편의시설, 플레이어가 **집을 짓는 곳** (집터는 마을에서만 설치, 고정) | 평화로움 |
| `wild` 야생 | 자원 구하는 야생 | - |

## 플레이 흐름
1. 처음 접속 → 1,000G + 서버 안내서(책). "로비의 이장님께 말을 걸어 보세요" 안내
2. **이장님** 대화창을 **다음 ▶** 으로 넘기면 마지막에 **집터** 받기
3. 마을에 집터 설치 → 그 청크가 내 집 (집값 50,000G 는 **빚**)
4. 야생에서 농사·채광·벌목·낚시·사냥 → 레벨업, 칭호 → 마을 상점에 팔아 골드
5. 이장님께 빚 갚기 → 다 갚으면 땅 넓히기 (빚 150,000 / 500,000 / 1,000,000G) → 다시 갚기
6. 틈틈이 도감 채우기

## 기능
### 마을 메뉴 — `Shift + F`, `/메뉴`
칠판 간판 + 일러스트 큰 버튼. 버튼마다 `menu.yml` 에서 명령어나 이동 지점을 정해요.

| 버튼 | 하는 일 |
|---|---|
| 내 얼굴 (왼쪽 위) | 스킬 창 |
| 📖 도감 | 도감 열기 |
| ✦ 워프 (가운데) | 준비 중 |
| 🏘 로비로 / 🏠 마을로 / 🏘 야생으로 | 지점 `lobby` / `village` / `wild` 로 이동 (숫자를 센 뒤, `config.yml` → `teleport.countdown-seconds`) |

이동 지점은 그 자리에 서서 `/servercore setpoint <이름>`. `/mv tp` 와 같은 곳으로 보내려면 같은 자리에서 `/mv setspawn` 도 같이.

### 이장님 (로비)
- `/mayornpc create` 로 세워요. 나를 바라보게 서요
- **처음**: 대화창 3장을 **다음 ▶** 으로 넘기고, 마지막 장의 **🏠 집터 받기**. 중간에 닫으면 다음에 그 장부터 이어서
- **그 뒤**: 빚 갚기 창. 남은 빚·가진 골드 표시, 1,000 / 10,000 / 50,000 / 전부(가진 만큼) 갚기, 집터를 잃어버렸으면 다시 받기
- 예전에 이미 집터를 받은 사람·집이 있는 사람은 첫 대화를 건너뛰어요
- 대사·버튼 글자: `config.yml` → `mayor`

### 내 집 땅 (마을)
- 이장님께 받은 **집터**(작은 집 모형)를 설치하면 그 청크(16×16)가 바로 내 집. 문이 놓은 사람 쪽을 봐요
- 집터 우클릭 또는 `/land` → **집 지도 창** (위 = 집터를 놓을 때 바라본 방향)
  - 붙은 청크를 골라 한 칸씩 넓히기: 최대 4청크, 집터에서 상하좌우 2칸 안, **집 구역** 안만 (밖은 회색 공용지)
  - + 를 누르면 창이 닫히고 빛나는 경계선(초록 내 땅 · 노랑 넓힐 곳) → 채팅의 **[✔ 넓히기] [✖ 취소] [↻ 다시 보기]** (30초)
  - 넓힐 때마다 빚이 생기고, **빚을 다 갚아야 다음 확장**. 빚은 **이장님께만** 갚아요 (지도의 금화는 보기만)
  - 이웃 버튼 → 서버 사람 목록에서 클릭해 허가 ✔ (내 땅에서 전부 할 수 있음), 다시 누르면 취소
  - 망원경 = 내 땅 테두리를 빛나는 선으로 보기 (나한테만 보여요)
- **집 구역**: 관리자가 정한 청크 안에서만 집터 설치·넓히기 (`land-zones.yml`, 구역이 없으면 마을 어디든)
- 집터는 아무도 못 부수고, 내 땅은 나와 이웃만 만질 수 있어요. 크리퍼·TNT 폭발에서도 보호
- 땅에 들어가면 화면 아래에 "○○님의 집"
- 데이터 `lands.yml`, 설정 `config.yml` → `land`

### 골드
- 처음 접속하면 1,000G (`economy.starting-balance`). 잔액은 `money.yml`
- `/money <금액>` → **골드 주머니** 아이템으로 꺼내기 (던져서 주기, 우클릭하면 다시 잔액으로, Shift+우클릭 묶음 전부)

### NPC 상점 (마을)
- `/shopnpc create <fish|crops|ores>` 로 상점 주인(주민) 세우기 → 클릭하면 생선 / 농작물 / 광물 가게
- 주민 교환처럼 [아이템] → [골드] 줄이 왼쪽 5개·오른쪽 5개 (10개 넘으면 페이지)
- 골드 칸 클릭 = 1개 팔고 골드 주머니를 커서에, Shift = 전부 팔기
- `buy` 가격을 적은 품목은 아이템 칸을 눌러 살 수도 있어요 (지금은 사들이기만)
- 이름 붙은 아이템·인챈트된 아이템은 안 팔려요
- 품목·가격: `shops.yml` (3.11.0 에서 판매가 2배). 상점마다 `skill:` 로 그 분야 레벨 보너스 연결

### 레벨링 — 농사·채광·벌목·낚시·전투
- 다 자란 작물, 광석, 원목, 낚은 것, 잡은 몹으로 경험치. 직접 놓은 블록을 다시 캐면 경험치 없음
- 최고 **Lv.50**. 다음 레벨까지 = `50 + 40×레벨 + 3×레벨²` (1→50 누적 약 172,700)
- `/skill` 또는 메뉴의 내 얼굴 → **스킬 창** (아이콘에 마우스를 올리면 칭호·효과)
- **레벨 효과** (일단 아주 작게, `skills.yml` → `perks`)

  | 효과 | 레벨당 | Lv.50 |
  |---|---|---|
  | 농사·채광(광석)·벌목: 2배로 얻을 확률 / 낚시: 한 마리 더 | 0.05% | 2.5% |
  | 전투: 몹에게 주는 피해 | +0.05% | +2.5% |
  | 그 분야 상점 판매가 (농사→농작물, 채광→광물, 낚시→생선) | +0.02% | +1% |

- **칭호**: 분야마다 Lv.10·20·30·40·50 (예: 새싹 농부 → 전설의 농부, `skills.yml` → `titles`)
  - 받으면 자동으로 달리고 다른 사람에게 알림. `/칭호` 로 바꾸기 → 채팅·탭 목록 이름 앞에 `[칭호]`
- 경험치 표·공식: `skills.yml`, 데이터 `skill-data/<UUID>.yml`

### HUD
왼쪽 위에 얼굴·이름·골드·레벨, 경험치를 얻으면 위에 경험치 바. 각자 켜고 끄거나 위치를 맞출 수 있어요 (`/hud`).

### 도감
- 메뉴의 📖 도감 또는 `/도감 [페이지]`. 한 페이지 45칸, 맨 아래 ◀ 진행도 게이지 · 페이지 ▶
- 항목 칸 클릭 → 가방에 `required` 개가 있으면 그만큼 가져가고 등록 (체크 표시)
- 순서는 크리에이티브 인벤토리 순서 (`dogam.yml` 의 `order: creative`, `file` 이면 파일 순서)

### 이동 도우미
- **점프 패드**: `/점프패드` 로 받은 아이템(중량 무게 압력판 모양)을 설치 → 밟으면 앞+위로 튕김 (`config.yml` → `jump-pad`)
- **도약**: 달리면서 점프하면 멀리 뛰기, 쿨타임 5초. 로비·마을에서만 (`config.yml` → `long-jump`)

### 서버 안내서
처음 접속하면 책 1권, `/guide` 로 다시 받기. 내용은 `guide.yml`.

## 명령어
### 플레이어
| 명령어 | 하는 일 |
|---|---|
| `Shift + F`, `/메뉴` (`/menu`) | 마을 메뉴 |
| `/도감 [페이지]` (`/dogam`) | 도감 |
| `/skill` (`/스킬`, `/레벨`) | 스킬 창 |
| `/skill info` | 내 레벨·칭호를 채팅으로 |
| `/칭호` (`/honor`) | 받은 칭호 목록 (눌러서 달기) |
| `/칭호 <분야:레벨>` · `/칭호 off` | 칭호 달기 (예: `/칭호 farming:30`) · 떼기 |
| `/money` (`/돈`, `/gold`) | 내 골드 |
| `/money <금액>` | 골드 주머니로 꺼내기 |
| `/land` (`/home`, `/house`) | 집 지도 창 |
| `/land home` · `/land sethome` | 집으로 이동 · 지금 자리를 집 스폰으로 (내 땅 안에서) |
| `/land trust <이름>` · `/land untrust <이름>` | 이웃 허가 · 취소 |
| `/land show` | 내 땅 테두리 보기 |
| `/land info` | 내 땅 정보 (청크 수, 빚, 이웃) |
| `/land kit` | 집터 다시 받기 (집이 없고, 이장님과 첫 대화를 마친 뒤) |
| `/land confirm` · `/land cancel` · `/land preview` | 넓히기 확인 · 취소 · 경계 다시 보기 (보통은 채팅 버튼으로) |
| `/land abandon confirm` | 집 포기 (집터를 돌려받고 남은 빚도 없어짐) |
| `/hud` · `/hud on` · `/hud off` | HUD 켜기/끄기 |
| `/hud left [칸]` · `/hud right [칸]` · `/hud pos <숫자>` · `/hud reset` | HUD 위치 맞추기 (각자 기억) |
| `/guide` (`/가이드`, `/안내서`) | 서버 안내서 다시 받기 |

### 관리자 (OP)
| 명령어 | 하는 일 |
|---|---|
| `/코어 reload` (`/servercore reload`) | 설정 다시 불러오기 (config, menu, dogam, shops, skills, guide, points) |
| `/servercore setpoint <이름>` | 지금 자리를 이동 지점으로 (메뉴의 `teleport: <이름>`) |
| `/servercore delpoint <이름>` · `/servercore points` | 지점 지우기 · 목록 |
| `/mayornpc create` · `/mayornpc remove` | 이장님 세우기 · 5칸 안의 이장님 없애기 |
| `/mayornpc reset <플레이어>` | 그 사람의 이장님 첫 대화를 처음부터 (집이 있으면 건너뜀) |
| `/shopnpc create <fish\|crops\|ores>` · `/shopnpc remove` | 상점 주인 세우기 · 5칸 안의 상점 주인 없애기 |
| `/shop <상점> [플레이어]` | 상점 바로 열기 (콘솔·다른 NPC 플러그인 연결용) |
| `/money give\|take\|set <플레이어> <금액>` | 골드 주기 · 빼기 · 정하기 |
| `/skill set <플레이어> <분야> <레벨>` | 레벨 정하기 (분야: farming mining woodcutting fishing combat 또는 한글) |
| `/skill reset <플레이어>` | 레벨 전부 1로 |
| `/land give <플레이어>` | 집터 주기 |
| `/land delete <플레이어>` | 그 사람의 땅 지우기 (집터 블록도) |
| `/land bypass` | 남의 땅에서도 건드릴 수 있게 (켜기/끄기) |
| `/land zone pos1` · `/land zone pos2` | 집 구역 네모의 두 모서리 (지금 서 있는 청크) |
| `/land zone add` · `/land zone remove` | 네모 안의 청크를 집 구역에 넣기 · 빼기 (모서리를 안 정하면 지금 청크 하나) |
| `/land zone show` · `/land zone info` | 근처 집 구역을 하늘색 선으로 · 구역 크기와 지금 청크가 구역인지 |
| `/land admin addchunk <이름>` | 지금 서 있는 청크를 그 사람 땅에 붙이기 (빚 그대로) |
| `/land admin removechunk <이름>` | 지금 서 있는 청크를 그 사람 땅에서 떼기 (집터 청크는 안 됨) |
| `/land admin setdebt <이름> <금액>` | 빚 정하기 |
| `/도감 reset <플레이어>` | 도감 기록 지우기 |
| `/점프패드 [개수] [플레이어]` (`/jumppad`) | 점프 패드 아이템 받기 |

## 처음 서버 세팅 체크리스트
1. jar 넣기, `plugins/ServerCore/` 의 설정 파일 확인 (아래 설치 순서)
2. 로비: 도착 자리에서 `/mv setspawn` + `/servercore setpoint lobby`, 이장님 자리에서 `/mayornpc create`
3. 마을: `/mv setspawn` + `/servercore setpoint village`, 상점 자리마다 `/shopnpc create fish|crops|ores`
   집 구역: 주택가 한쪽 모서리에서 `/land zone pos1`, 반대쪽에서 `/land zone pos2` → `/land zone add` (`/land zone show` 로 확인)
4. 야생: `/mv setspawn` + `/servercore setpoint wild`
5. 로비·마을 난이도 평화로움: `/mv modify <월드> set difficulty peaceful`

## 설치 순서
1. **빌드**: IntelliJ → Gradle 탭 → `build` → `build/libs/ServerCore-<버전>.jar` → 서버 `plugins` 에 넣고 **예전 ServerCore jar는 꼭 삭제** (버전은 `build.gradle.kts`, 바뀐 내용은 `CHANGELOG.md`)
2. **서버 설정 파일 교체**: 서버의 `plugins/ServerCore/` 에서 바뀐 설정 파일을 이 프로젝트의 `src/main/resources/` 것으로 바꾸기
   (`menu.yml`, `dogam.yml`, `shops.yml`, `skills.yml` 등 — 어떤 파일이 바뀌었는지는 `CHANGELOG.md` 의 ⚠ 표시)
   `config.yml` 은 새 항목이 없어도 jar 안의 기본값을 써요. 처음 설치면 그냥 두면 자동으로 만들어져요
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
줄1 ■ ■ ■ ■ ■ ■ ■ ■ ■                       줄1 [📖 도감 ] [         ] [🏘 로비로 ]
줄2 ■ ■ ■ ■ ■ ■ ■ ■ ■   ← 한 페이지 45개     줄2 [        ] [ ✦ 워프  ] [        ]
줄3 ■ ■ ■ ■ ■ ■ ■ ■ ■                       줄3 [🏘 야생으로] [        ] [🏠 마을로 ]
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
 │   ├─ Dialogs       대화창(Dialog) 버튼 도우미 (이장님)
 │   ├─ Points        이동 지점 (points.yml, /servercore setpoint)
 │   ├─ Placeholders  %dogam_found% 같은 치환 문구
 │   └─ Text, Feature
 ├─ menu/   MenuFeature (Shift+F, /메뉴), MenuGui, MenuConfig (menu.yml)
 ├─ jumppad/ JumpPadFeature (점프 패드)
 ├─ longjump/ LongJumpFeature (달리기 도약)
 ├─ economy/ Economy, EconomyFeature (골드, /money, 골드 주머니)
 ├─ shop/    ShopFeature, ShopGui, ShopConfig (NPC 상점, shops.yml)
 ├─ skill/   SkillFeature, SkillStore, SkillCommand, SkillGui, HonorCommand (레벨링·스킬 창·칭호·레벨 효과, skills.yml)
 ├─ guide/   GuideFeature (서버 안내서 책, guide.yml)
 ├─ land/    LandFeature, LandStore, LandProtection, LandCommand, LandGui, MembersGui (내 집 땅, lands.yml)
 │           LandZones (집 구역, land-zones.yml), BorderDisplay (빛나는 경계선)
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
`dogam/DogamDialog.java`, `menu/QuickActionInstaller.java`, `tools/make_template.py`, `tools/pack_template/` 은
2.0(대화창 방식)에서 쓰던 것이라 지워도 돼요. (`common/Dialogs.java` 는 3.11 부터 이장님 대화창에서 다시 써요)

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
