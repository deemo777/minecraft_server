# FastAsyncWorldEdit — 건축 편집

건축 편집 명령어 정리입니다.

FAWE 2.16.1은 WorldEdit 호환 명령어를 제공합니다. `//` 두 개를 구분해 입력합니다.
작은 영역에서 확인한 뒤 편집하며, 실행 취소 기록을 월드 백업 대신 사용하지 않습니다.

| 명령어 | 용도 |
|---|---|
| `//wand` | 선택 도끼 |
| `//pos1`, `//pos2` | 두 모서리 |
| `//hpos1`, `//hpos2` | 바라보는 블록 선택 |
| `//expand vert` | 선택 높이 확장 |
| `//size` | 선택 크기 |
| `//set <블록>` | 채우기 |
| `//replace <기존> <새블록>` | 교체 |
| `//walls <블록>` | 벽 |
| `//copy` | 복사 |
| `//cut` | 잘라내기 |
| `//paste -a` | 공기 제외 붙여넣기 |
| `//rotate 90` | 클립보드 회전 |
| `//undo`, `//redo` | 취소·재실행 |
| `//schem save <name>` | 구조 저장 |
| `//schem load <name>` | 구조 불러오기 |
| `//schem list` | 구조 목록 |
| `//help` | 전체 도움말 |
| `/we version` | 버전 |
| `/we reload` | 설정 리로드 |
| `//cancel` | 진행 작업 취소 |

복사는 `//copy` 당시 플레이어 위치를 기준으로 붙습니다.
WorldGuard 선택은 이 영역을 공유하지만, ServerCore `/land zone` 선택과는 독립입니다.

출처: [WorldEdit 명령어](https://worldedit.enginehub.org/en/latest/commands/),
[FAWE 명령어](https://intellectualsites.gitbook.io/fastasyncworldedit/features/main-commands-and-permissions).
