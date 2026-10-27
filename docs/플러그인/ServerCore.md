# ServerCore 명령어

[전체 문서](../README.md) · **3.20.2 / 2026-10-28**

관리자는 OP 또는 `servercore.admin` 권한이 필요합니다. `<값>` 필수, `[값]` 생략 가능.

## 기본 명령과 별칭

| 명령어 | 용도 | 권한 | 별칭 |
|---|---|---|---|
| `/rankboard` | 네 분야 순위 게시판 관리 | 관리자 | `/순위게시판` |
| `/ranking` | 네 분야 누적 순위 조회 | 일반 | `/순위` |
| `/cookienpc` | 광쿠키 요리사 생성·제거·방향 | 관리자 | `/쿠키npc` |
| `/blacksmithnpc` | 대장장이 NPC 생성 및 제거 | 관리자 | `/대장장이` |
| `/enhance` | 강화 화면 열기 및 전용 재료 지급 (관리자) | 관리자 | `/강화` |
| `/menu` | 마을 메뉴를 엽니다 | 일반 | `/메뉴` |
| `/dogam` | 도감을 엽니다 | 일반/관리 | `/도감` · `/collection` |
| `/money` | 골드 보기 / 골드 주머니 꺼내기 | 일반/관리 | `/돈` · `/gold` |
| `/shop` | 상점 열기 (관리자) | 관리자 | — |
| `/shopnpc` | 상점 주인 NPC 세우기/없애기 (관리자) | 관리자 | — |
| `/skill` | 스킬 창 열기 / 레벨 설정 (관리자) | 일반/관리 | `/skills` · `/스킬` · `/레벨` |
| `/honor` | 칭호 목록 / 칭호 달기 | 일반 | `/칭호` · `/titles` |
| `/hud` | HUD 켜기/끄기, 위치 맞추기 | 일반 | — |
| `/guide` | 서버 안내서(책) 받기 | 일반 | `/가이드` · `/안내서` |
| `/land` | 내 집 땅 (집터, 지도 창, 넓히기·이웃 허가) | 일반/관리 | `/home` · `/house` |
| `/jumppad` | 점프 패드 아이템 받기 | 관리자 | `/점프패드` |
| `/mayornpc` | 이장님 NPC 세우기/없애기, 첫 대화 되돌리기 (관리자) | 관리자 | — |
| `/villagetool` | 마을 도구 주기 / 강화 단계 바꾸기 (관리자, 모양 확인용) | 관리자 | `/마을도구` |
| `/servercore` | ServerCore 관리 명령어 | 일반 | `/코어` |

## 자주 쓰는 관리자 명령

| 명령어 | 용도 |
|---|---|
| `/servercore reload` | 켜진 기능의 설정 다시 읽기 |
| `/servercore setpoint <이름>` | 현재 자리를 메뉴 이동 지점으로 저장 |
| `/servercore delpoint <이름>` · `/servercore points` | 이동 지점 삭제·목록 |
| `/mayornpc create` · `/mayornpc remove` | 이장 생성·제거 |
| `/mayornpc reset <플레이어>` | 이장 첫 대화 초기화 |
| `/shopnpc create <상점>` · `/shopnpc remove` | 상점 NPC 생성·제거 (기본: fish, crops, ores) |
| `/shop <상점> [플레이어]` | 상점 창 열기 |
| `/blacksmithnpc create` · `/blacksmithnpc remove` | 대장장이 생성·제거 |
| `/enhance [플레이어]` | 강화 창 열기 |
| `/enhance material <종류> <1~64> [플레이어]` | 전용 강화 재료 지급 |
| `/villagetool give <종류> [단계] [플레이어]` | 마을 도구 지급 |
| `/villagetool level <0~20>` | 손에 든 도구 강화 단계 지정 |
| `/cookienpc create` · `/cookienpc remove` · `/cookienpc face` | 요리사 생성·제거·방향 변경 |
| `/rankboard create <이름> <분야>` | 누적 순위 홀로그램 생성 |
| `/rankboard remove <이름>` · `/rankboard list` | 게시판 삭제·목록 |
| `/money give <플레이어> <금액>` | 골드 지급 (`take` 회수, `set` 지정) |
| `/skill set <플레이어> <분야> <레벨>` | 스킬 레벨 지정 |
| `/skill reset <플레이어>` · `/dogam reset <플레이어>` | 스킬·도감 초기화 |
| `/jumppad [개수] [플레이어]` | 점프 패드 지급 |

도구 종류: `pickaxe`, `axe`, `hoe`, `fishing_rod`, `watering_can` (한글 이름도 가능).
순위 분야: `farming`, `mining`, `woodcutting`, `fishing`. 스킬에는 `combat`도 있습니다.
NPC `remove`·요리사 `face`는 5블록 안의 가장 가까운 대상을 처리합니다. 요리사 방향은 **실행자 시선의 반대**입니다.

## 집터 명령과 조작

| 명령·조작 | 용도 |
|---|---|
| 집터 들고 바닥 가리키기 | 6블록 이내 설치 전 부지 미리보기 |
| 집터 들고 웅크리기+우클릭 | 집터 모서리 순환 선택 |
| `/land` · 설치한 집터 우클릭 | 집 지도 |
| `/land kit` | 집터 재수령 (집 없음·이장 첫 대화 완료 조건) |
| `/land home` · `/land sethome` | 집 이동·내 사용 청크 안에서 귀환 지점 지정 |
| `/land trust <이름>` · `/land untrust <이름>` | 이웃 권한 부여·회수 |
| `/land show` · `/land info` | 경계·정보 |
| `/land preview` · `/land confirm` · `/land cancel` | 청크 해방 미리보기·확인·취소 |
| `/land abandon confirm` | 집 포기·예약 해제·집터 반환 |
| `/land give [플레이어]` · `/land delete <플레이어>` | 관리자 집터 지급·부지 삭제 |
| `/land bypass` | 관리자 보호 우회 토글 |
| `/land zone pos1` · `/land zone pos2` | 관리자 집 구역의 두 모서리 청크 선택 |
| `/land zone add` · `/land zone remove` | 선택한 집 구역 추가·제거 |
| `/land zone show` · `/land zone info` | 집 구역 표시·정보 |
| `/land admin addchunk <이름>` · `/land admin removechunk <이름>` | 관리자 예약 청크 해방·재잠금 |
| `/land admin setdebt <이름> <금액>` | 관리자 빚 지정 |

빚은 이장에게 갚습니다. `/land pay`는 결제 기능이 아닙니다. 집 포기·삭제는 건축물을 철거하거나 복원하지 않습니다.

## 플레이어 사용법

| 명령·조작 | 용도 |
|---|---|
| 웅크리기+F · `/menu` | 마을 메뉴 |
| `/dogam [페이지]` · `/guide` | 도감·안내서 |
| `/skill` · `/skill info` | 스킬 창·정보 |
| `/honor` · `/honor <분야:레벨>` · `/honor off` | 칭호 목록·착용·해제 |
| `/money` · `/money <금액>` | 잔액·골드 주머니 인출 |
| `/hud on` · `/hud off` | HUD 표시 |
| `/hud left [칸]` · `/hud right [칸]` · `/hud pos <숫자>` · `/hud reset` | HUD 위치 |
| `/ranking <분야>` | 누적 순위 조회 (한글 분야도 가능) |
| 요리사 우클릭 | 광쿠키 16개 무료 수령 |
| 대장장이 우클릭 | 장비와 전용 재료를 넣어 강화 |

새 JAR 또는 기능 켜기/끄기는 **재시작**이 필요합니다. 기존 아이템의 이름·설명은 새 JAR만으로 자동 변경되지 않습니다.
