# ServerCore — 기능과 명령어

[플러그인 목록](README.md) · [집터 규칙](../기능/집터와부지.md) · [기능 설명](../기능/ServerCore_기능.md)

기준: 개발 소스 `src/main/resources/plugin.yml`과 각 명령어 처리 코드, ServerCore 3.15.0.
로컬 설치 JAR는 3.12.0이므로 아래 마을 도구 명령어는 3.13.0 이상을 설치해야 합니다.
기능이 `config.yml`에서 꺼져 있으면 해당 기능의 명령어를 사용할 수 없습니다.

## 플레이어
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
| `/land` (`/home`, `/house`) | 집 지도 창 (2×2 예약 부지·청크 해방) |
| `/land home` · `/land sethome` | 집으로 이동 · 지금 자리를 집 스폰으로 (내 땅 안에서) |
| `/land trust <이름>` · `/land untrust <이름>` | 이웃 허가 · 취소 |
| `/land show` | 내 땅 테두리 보기 |
| `/land info` | 내 땅 정보 (청크 수, 빚, 이웃) |
| `/land kit` | 집터 다시 받기 (집이 없고, 이장님과 첫 대화를 마친 뒤) |
| `/land confirm` · `/land cancel` · `/land preview` | 다음 청크 해방 확인 · 취소 · 경계 다시 보기 (보통은 채팅 버튼으로) |
| `/land abandon confirm` | 집 포기 (집터를 돌려받고 남은 빚도 없어짐) |
| `/hud` · `/hud on` · `/hud off` | HUD 켜기/끄기 |
| `/hud left [칸]` · `/hud right [칸]` · `/hud pos <숫자>` · `/hud reset` | HUD 위치 맞추기 (각자 기억) |
| `/guide` (`/가이드`, `/안내서`) | 서버 안내서 다시 받기 |

## 관리자 (OP 또는 servercore.admin 권한)
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
| `/land admin addchunk <이름>` | 그 사람 2×2 부지의 잠긴 청크 해방 (빚 그대로, 관리자용) |
| `/land admin removechunk <이름>` | 사용 중인 청크를 다시 잠그기 (예약 부지는 유지, 집터 청크 제외) |
| `/land admin setdebt <이름> <금액>` | 빚 정하기 |
| `/도감 reset <플레이어>` | 도감 기록 지우기 |
| `/점프패드 [개수] [플레이어]` (`/jumppad`) | 점프 패드 아이템 받기 |
| `/마을도구 give <종류> [단계] [플레이어]` (`/villagetool`) | 마을 도구 주기 (종류: pickaxe axe hoe fishing_rod watering_can 또는 곡괭이 도끼 괭이 낚싯대 물뿌리개) |
| `/마을도구 level <단계>` | 손에 든 마을 도구의 강화 단계 바꾸기 (모양 확인용) |


## 별칭 전체 목록

| 기본 명령어 | 별칭 |
|---|---|
| `/menu` | `/메뉴` |
| `/dogam` | `/도감`, `/collection` |
| `/money` | `/돈`, `/gold` |
| `/skill` | `/skills`, `/스킬`, `/레벨` |
| `/honor` | `/칭호`, `/titles` |
| `/guide` | `/가이드`, `/안내서` |
| `/land` | `/home`, `/house` |
| `/jumppad` | `/점프패드` |
| `/villagetool` | `/마을도구` |
| `/servercore` | `/코어` |

`/shop`, `/shopnpc`, `/mayornpc`, `/hud`에는 별칭이 등록되어 있지 않습니다.
`/land pay`는 결제 명령어가 아니라 이장님께 가라는 안내만 표시합니다.
`/land give`는 게임 안에서 대상 생략 시 자기 자신에게 집터를 지급할 수 있습니다.
`/land zone`은 블록 선택이 아닌 청크 선택입니다. FAWE의 `//pos1`과 서로 독립입니다.

## 자주 쓰는 예시

```text
/servercore points
/servercore setpoint village
/mayornpc create
/shopnpc create fish
/land zone pos1
/land zone pos2
/land zone add
/마을도구 give 곡괭이 10
/마을도구 level 20
```

`/land delete`, `/land abandon confirm`, `/도감 reset`, `/skill reset`은 데이터를 변경·삭제합니다.
대상 이름을 확인하고 사용합니다. 집 포기·삭제는 실제 건축물 전체를 되돌려 주는 기능이 아닙니다.

`/servercore reload`는 설정·지점과 이미 켜져 있는 기능의 설정을 다시 읽습니다.
새 JAR 적용이나 기능 구성 자체 변경은 서버 재시작으로 적용합니다.

## 대장간 명령어 (3.15.0)

| 명령어 | 내용 |
|---|---|
| `/blacksmithnpc create` | 현재 위치에 대장장이 생성 |
| `/blacksmithnpc remove` | 5블록 안에서 가장 가까운 대장장이 제거 |
| `/enhance [플레이어]` | 강화 창 열기 (관리자) |
| `/enhance material <종류> <1~64> [플레이어]` | 장비 전용 강화 결정 지급 (관리자) |

별칭: `/대장장이`, `/강화`. 일반 플레이어는 대장장이를 우클릭해 이용합니다.
[재료 획득·성공률·실패·화면 사용법](../기능/대장간과강화.md)을 확인하세요.
수리·장비 구매·특수 능력은 아직 없습니다.

## 무료 얼굴 쿠키

관리자는 `/cookienpc create` 또는 `/쿠키npc create`로 현재 블록 중앙에 쿠키 나눔이 NPC를 만듭니다. `/cookienpc remove`는 5블록 안에서 가장 가까운 쿠키 NPC 하나를 제거합니다.

누구나 우클릭해서 Deemo777 얼굴 쿠키를 16개씩 무료로 반복 수령합니다. 중복 클릭 방지를 위해 0.5초 간격이 있으며 16개가 모두 들어갈 공간이 없으면 지급하지 않습니다. NPC는 월드에 저장됩니다. 허기 10(아이콘 5개), 포화도 12를 회복하며 일반 쿠키 성능은 유지됩니다. 통합 리소스팩을 함께 교체해야 얼굴이 보입니다.

`config.yml`의 `features.cookie: false` 후 재시작하면 기능을 끕니다. 스킨은 제작 시점의 공개 스킨으로 고정돼 서버 시작 시 외부 요청을 하지 않습니다.
