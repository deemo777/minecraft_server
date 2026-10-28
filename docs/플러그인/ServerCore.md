# ServerCore 명령어

[문서 홈](../../README.md) · **3.31.7 / 2026-10-28**

명령어·도구 ID·분야 ID는 **영어로만 입력**합니다.

관리자는 OP 또는 `servercore.admin` 권한이 필요합니다. `<value>` 필수, `[value]` 생략 가능.

## 기본 명령과 별칭

| 명령어 | 용도 | 권한 | 별칭 |
|---|---|---|---|
| `/npc` | NPC 생성·제거·방향 통합 명령 | 관리자 | — |
| `/weaponmerchantnpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/weaponshop` | 0강 마을 도구 구매 창 열기 (관리자) | 관리자 | — |
| `/rankboard` | 네 분야 순위 게시판 관리 | 관리자 | — |
| `/ranking` | 네 분야 누적 순위 조회 | 일반 | — |
| `/cookienpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/blacksmithnpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/enhance` | 강화 화면 열기 및 전용 재료 지급 (관리자) | 관리자 | — |
| `/menu` | 마을 메뉴를 엽니다 | 일반 | — |
| `/dogam` | 도감을 엽니다 | 일반/관리 | `/collection` |
| `/money` | 골드 보기 / 골드 주머니 꺼내기 | 일반/관리 | `/gold` |
| `/shop` | 상점 열기 (관리자) | 관리자 | — |
| `/shopnpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/skill` | 스킬 창 열기 / 레벨 설정 (관리자) | 일반/관리 | `/skills` |
| `/honor` | 칭호 목록 / 칭호 달기 | 일반 | `/titles` |
| `/hud` | HUD 켜기/끄기, 위치 맞추기 | 일반 | — |
| `/guide` | 서버 안내서(책) 받기 | 일반 | — |
| `/land` | 내 집 땅 (집터, 지도 창, 넓히기·이웃 허가) | 일반/관리 | `/home` · `/house` |
| `/jumppad` | 점프 패드 아이템 받기 | 관리자 | — |
| `/mayornpc` | 구 NPC 전용 명령 (호환용) | 관리자 | — |
| `/villagetool` | 마을 도구 주기 / 강화 단계 바꾸기 (관리자, 모양 확인용) | 관리자 | — |
| `/servercore` | ServerCore 관리 명령어 | 관리자 | — |

## 자주 쓰는 관리자 명령

| 명령어 | 용도 |
|---|---|
| `/npc create <type>` · `/npc remove <type>` · `/npc face <type>` | NPC 생성·제거·방향 변경 (`mayor`, `blacksmith`, `fish`, `crops`, `ores`, `cookie`, `weaponmerchant`) |
| `/servercore reload` | 켜진 기능의 설정 다시 읽기 |
| `/servercore setpoint <name>` | 현재 자리를 메뉴 이동 지점으로 저장 |
| `/servercore delpoint <name>` · `/servercore points` | 이동 지점 삭제·목록 |
| `/npc create mayor` | 이장 생성 |
| `/npc reset mayor <player>` | 이장 첫 대화 초기화 |
| `/npc create <shop>` · `/npc remove <shop>` · `/npc face <shop>` | 상점 NPC 생성·제거·방향 변경 (기본: fish, crops, farmplots, ores) |
| `/shop <shop> [player]` | 상점 창 열기 |
| `/npc create blacksmith` · `/npc remove blacksmith` · `/npc face blacksmith` | 대장장이 생성·제거·방향 변경 |
| `/enhance [player]` | 강화 창 열기 |
| `/enhance material <type> <1~64> [player]` | 전용 강화 재료 지급 |
| `/villagetool give <type> [level] [player]` | 마을 도구 지급 |
| `/villagetool level <0~20>` | 손에 든 도구 강화 단계 지정 |
| `/npc create weaponmerchant` · `/npc remove weaponmerchant` · `/npc face weaponmerchant` | 무기 상인 생성·제거·방향 변경 |
| `/weaponshop [player]` | 0강 마을 도구 구매 창 열기 |
| `/npc create cookie` · `/npc remove cookie` · `/npc face cookie` | 요리사 생성·제거·방향 변경 |
| `/rankboard create <name> <category>` | 누적 순위 홀로그램 생성 |
| `/rankboard remove <name>` · `/rankboard list` | 게시판 삭제·목록 |
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
| `/land bypass` | 관리자 보호 우회 토글 |
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
