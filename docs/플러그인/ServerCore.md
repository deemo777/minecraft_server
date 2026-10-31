# ServerCore 명령어

[문서 홈](../../README.md) · **4.6.0 / 2026-10-30**

명령어·도구 ID·분야 ID는 **영어로만 입력**합니다.

관리자는 OP 또는 `servercore.admin` 권한이 필요합니다. `<value>` 필수, `[value]` 생략 가능.

## 기본 명령과 별칭

| 명령어 | 용도 | 권한 | 별칭 |
|---|---|---|---|
| `/npc` | NPC 생성·제거·방향 통합 명령 | 관리자 | — |
| `/weaponmerchantnpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/weaponshop` | 0강 마을 도구 구매 창 열기 (관리자) | 관리자 | — |
| `/rankboard` | 네 분야 순위 게시판 관리 | 관리자 | — |
| `/slotmachine [1~64] [player]` | 설치형 코인 슬롯 머신 지급 | 관리자 | — |
| `/ranking` | 네 분야 누적 순위 조회 | 일반 | — |
| `/cookienpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/blacksmithnpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/enhance` | 강화 화면 열기 및 전용 재료 지급 (관리자) | 관리자 | — |
| `/repair [player]` | 수리 공방 열기 | 관리자 | — |
| `/repairnpc` | 수리 기사 전용 관리 명령 | 관리자 | — |
| `/menu` | 마을 메뉴를 엽니다 | 일반 | — |
| `/dogam` | 도감을 엽니다 | 일반/관리 | `/collection` |
| `/money` | 골드 보기 / 골드 주머니 꺼내기 | 일반/관리 | `/gold` |
| `/shop` | 상점 열기 (관리자) | 관리자 | — |
| `/shopnpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/skill` | 스킬 창 열기 / 레벨 설정 (관리자) | 일반/관리 | `/skills` |
| `/honor` | 칭호 목록 / 칭호 달기 | 일반 | `/titles` |
| `/achievements` | 최초 달성으로 공개된 히든 업적·내 진행 확인 | 일반 | — |
| `/atitle [equip <id>\|clear]` | 보유 업적 칭호 장착·해제 | 일반 | — |
| `/hud` | HUD 켜기/끄기, 위치 맞추기 | 일반 | — |
| `/guide` | 서버 안내서(책) 받기 | 일반 | — |
| `/land` | 내 집 땅 (집터, 지도 창, 넓히기·이웃 허가) | 일반/관리 | `/home` · `/house` |
| `/jumppad` | 점프 패드 아이템 받기 | 관리자 | — |
| `/mayornpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/villagetool` | 마을 도구 주기 / 강화 단계 바꾸기 (관리자, 모양 확인용) | 관리자 | — |
| `/servercore` | ServerCore 관리 명령어 | 관리자 | — |

스킨 상점: `/skinmerchantnpc create|remove|face`로 전용 NPC를 관리하고, `/skinshop [player]`로 상점 창을 엽니다. 일반 플레이어는 NPC 우클릭으로 이용합니다. 종류 선택 후 100,000G를 결제하며 미보유 스킨을 계정 도감에 영구 등록합니다.

## 자주 쓰는 관리자 명령

| 명령어 | 용도 |
|---|---|
| `/npc create <type>` · `/npc remove <type>` · `/npc face <type>` | NPC 생성·제거·방향 변경 (`mayor`, `blacksmith`, `repairer`, `fish`, `crops`, `ores`, `cookie`, `weaponmerchant`) |
| `/servercore reload` | 켜진 기능의 설정 다시 읽기 |
| `/servercore setpoint <name>` | 현재 자리를 메뉴 이동 지점으로 저장 |
| `/servercore delpoint <name>` · `/servercore points` | 이동 지점 삭제·목록 |
| `/npc create mayor` | 이장 생성 |
| `/npc create skinmerchant` · `/npc remove skinmerchant` · `/npc face skinmerchant` | 스킨 상인 관리 |
| `/skinshop [player]` | 스킨 종류 선택·뽑기·영구 도감 열기 |
| `/npc reset mayor <player>` | 이장 첫 대화 초기화 |
| `/npc create <shop>` · `/npc remove <shop>` · `/npc face <shop>` | 상점 NPC 생성·제거·방향 변경 (기본: fish, crops, farmplots, ores) |
| `/shop <shop> [player]` | 상점 창 열기 |
| `/npc create blacksmith` · `/npc remove blacksmith` · `/npc face blacksmith` | 대장장이 생성·제거·방향 변경 |
| `/enhance [player]` | 강화 창 열기 |
| `/npc create repairer` · `/npc remove repairer` · `/npc face repairer` | 수리 기사 생성·제거·방향 변경 |
| `/repair [player]` | 가운데 장비 한 칸·하단 수리 버튼의 수리 공방 열기 |
| `/enhance material <type> <1~64> [player]` | 전용 강화 재료 지급 |
| `/villagetool give <type> [level] [player]` | 마을 도구 지급 |
| `/villagetool level <0~25>` | 손에 든 도구 강화 단계 지정 |
| `/npc create weaponmerchant` · `/npc remove weaponmerchant` · `/npc face weaponmerchant` | 무기 상인 생성·제거·방향 변경 |
| `/weaponshop [player]` | 0강 마을 도구 구매 창 열기 |
| `/npc create cookie` · `/npc remove cookie` · `/npc face cookie` | 요리사 생성·제거·방향 변경 |
| `/rankboard create <name> <category>` | 누적 순위 홀로그램 생성 |
| `/rankboard remove <name>` · `/rankboard list` | 게시판 삭제·목록 |
| `/slotmachine [1~64] [player]` | 슬롯 머신 블록 지급. 설치 후 코인을 주손에 들고 블록 우클릭 |
| `/money give <player> <amount>` | 골드 지급 (`take` 회수, `set` 지정) |
| `/skill set <player> <category> <level>` | 스킬 레벨 지정 |
| `/skill reset <player>` · `/dogam reset <player>` | 스킬·도감 초기화 |
| `/jumppad [개수] [player]` | 점프 패드 지급 |

도구 종류: `pickaxe`, `axe`, `hoe`, `fishing_rod`, `watering_can`.
순위 분야: `farming`, `mining`, `woodcutting`, `fishing`. 스킬에는 `combat`도 있습니다.
NPC `remove`·`face`는 지정한 종류의 5블록 안에서 가장 가까운 대상을 처리합니다. 방향은 **실행자 시선의 반대**입니다.
기존 NPC별 명령(`/mayornpc`, `/shopnpc`, `/blacksmithnpc`, `/cookienpc`, `/weaponmerchantnpc`)도 호환됩니다.

## 집터 명령과 조작

| 명령·조작 | 용도 |
|---|---|
| 집터 들고 바닥 가리키기 | 6블록 이내 설치 전 부지 미리보기 |
| 집터 들고 웅크리기+우클릭 | 집터 모서리 순환 선택 |
| `/land` · 설치한 집터 우클릭 | 집 지도 |
| `/land kit` | 집터 재수령 (집 없음·이장 첫 대화 완료 조건) |
| `/land home` · `/land sethome` | 집 이동·내 사용 청크 안에서 귀환 지점 지정 |
| `/land trust <name>` · `/land untrust <name>` | 이웃 권한 부여·회수 |
| `/land show` · `/land info` | 경계·정보 |
| `/land preview` · `/land confirm` · `/land cancel` | 청크 해방 미리보기·확인·취소 |
| `/land abandon confirm` | 집 포기·예약 해제·집터 반환 |
| `/land give [player]` · `/land delete <player>` | 관리자 집터 지급·부지 삭제 |
| `/land bypass` | 비OP 관리자 보호 우회 토글; OP는 항상 우회 |
| `/land zone pos1` · `/land zone pos2` | 관리자 집 구역의 두 모서리 청크 선택 |
| `/land zone add` · `/land zone remove` | 선택한 집 구역 추가·제거 |
| `/land zone show` · `/land zone info` | 집 구역 표시·정보 |
| `/land admin addchunk <name>` · `/land admin removechunk <name>` | 관리자 예약 청크 해방·재잠금 |
| `/land admin setdebt <name> <amount>` | 관리자 빚 지정 |

빚은 이장에게 갚습니다. `/land pay`는 결제 기능이 아닙니다. 집 포기·삭제는 건축물을 철거하거나 복원하지 않습니다.

## 플레이어 사용법

| 명령·조작 | 용도 |
|---|---|
| 웅크리기+F · `/menu` | 마을 메뉴 |
| `/dogam [page]` · `/guide` | 도감·안내서 |
| `/skill` · `/skill info` | 스킬 창·정보 |
| `/honor` · `/honor <category:level>` · `/honor off` | 칭호 목록·착용·해제 |
| `/money` · `/money <amount>` | 잔액·골드 주머니 인출 |
| `/hud on` · `/hud off` | HUD 표시 |
| `/hud left [steps]` · `/hud right [steps]` · `/hud pos <number>` · `/hud reset` | HUD 위치 |
| `/ranking <category>` | 누적 순위 조회  |
| 무기 상인 우클릭 | 골드로 마을 도구 0강 구매 |
| 요리사 우클릭 | 광쿠키 16개 무료 수령 |
| 대장장이 우클릭 | 장비와 전용 재료를 넣어 강화 |

새 JAR 또는 기능 켜기/끄기는 **재시작**이 필요합니다. 기존 아이템의 이름·설명은 새 JAR만으로 자동 변경되지 않습니다.

[NPC 종류·이름표·상호작용 안내](../기능/NPC.md)


### 3.39 메뉴·칭호·기존 아이템 갱신

- 메뉴의 얼굴 → **칭호 변환**: 보유한 직업·히든 업적 칭호를 무료로 장착하고 해제합니다. 나무 명패 보관함 위쪽에 현재 칭호·보유 수를 표시하며 한 페이지에 28개(7열×4줄)를 보여줍니다. 직업별 도구·업적 별 아이콘을 쓰고 장착한 칭호는 반짝이는 명패로 표시합니다. 하단 메뉴·이전/다음·해제·직업 경험치 버튼을 지원하며 첫/마지막 페이지는 화살표가 비활성화됩니다. 리소스팩 비활성화 시 기본 아이템 화면으로 표시합니다. `/honor`도 같은 창을 열며 `/honor <분야:레벨>`, `/honor off`, `/atitle equip <id>`는 유지합니다. 장착 시 서버에서 소유권을 재확인합니다.
- 중앙의 위쪽 6칸은 워프, 아래쪽 6칸은 **직업 경험치**입니다. 예전 기본 `menu.yml`의 12칸 워프와 얼굴→skill 설정은 파일을 덮어쓰지 않고 로드할 때 호환합니다. 직접 바꾼 슬롯 배치는 유지하므로 사용자 지정 메뉴는 `src/main/resources/menu.yml`을 참고해 맞춰 주세요.
- 이전 도구·강화 결정은 접속, 리로드, 습득, 인벤토리 클릭·드래그, 상자 열기·닫기 때 갱신합니다. 상자·엔더 상자는 열었을 때 갱신하며 오프라인 플레이어와 닫힌 저장소를 일괄 수정하지 않습니다. 수량·강화 단계·장인의 기운·물 잔량·추가 PDC는 유지합니다.
- 경험치 막대는 4px 조각을 1px씩 겹쳐 전체 폭 136px 안에서 그립니다. Java 25 `gradlew build`에 `verifyGlyphBars`와 메뉴 호환 검증을 포함합니다. 새 메뉴 그림을 사용하려면 3.39 전체 리소스팩을 적용해야 합니다.
- 실서버 배포 후 얼굴 클릭, 직업/업적 전환·해제·재접속, 상자에서 예전 도구·결정 꺼내기, 138/142 및 최고 레벨 바의 화면 표시를 확인합니다.


### 3.39.1 메뉴 장식

마을 메뉴의 얼굴 위에 칭호 변경 안내를 표시하고 얼굴 액자·환영 리본·버튼 명패·하단 가방을 꾸몄습니다. 직업 경험치는 수첩과 세 가지 경험치 트랙으로, 워프는 빛나는 포털과 돌 계단으로 구분합니다. 6개 메뉴 버튼은 하단 여백을 늘리고 이름표의 글자 외곽선·그림자까지 실제 표시 영역으로 중앙 정렬해 테두리 안쪽에 1px 이상 여백을 확보합니다. 슬롯 배치와 메뉴 설정은 3.39.0과 같으며 새 그림은 전체 리소스팩을 다시 받아 적용합니다.

4.10.0부터 기존 야생 카드는 이동 지점이 비어 있는 던전 카드로 바뀝니다. 워프는 2×3 선택 창을 열며 기존 야생 중앙과 동·서·남·북 1,000블록 지점을 제공합니다. 여섯 번째 카드는 기능을 비워 둡니다. 기준점·기존 설정 호환·안전 착지는 [메뉴 이동 설정](../기능/기타기능.md#최초-월드-설정)을 참고하세요.


## 무기 상인 (4.7.0)

`/npc create armsmerchant`로 도구 상인과 별도의 무기 상인을 만듭니다. `/armsmerchantnpc remove|face`로 제거·방향을 조절하고 `/armsshop [player]`로 관리자 구매 창을 엽니다. 단검·태도·대검 0강을 각 1,000G에 판매하며 집터 설치가 필요합니다. 강화·재료·수리는 [무기와 스킨](../기능/무기와스킨.md)을 참고하세요.

4.9.2부터 대사 없이 구매 창을 바로 엽니다. 종류별 이름을 각 슬롯 위에 배치하고 상품 툴팁은 가격만 표시합니다. 외형은 명장과 구분되는 금발·남색 제복/모자·붉은 스카프·견갑·옆 검집을 가진 전용 모델입니다.
