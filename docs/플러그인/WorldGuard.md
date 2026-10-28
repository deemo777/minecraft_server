# WorldGuard — 공공 구역 보호

공공 구역 보호 명령어 정리입니다.

로비·상점·공공시설 보호용입니다. 설치 버전은 7.0.20입니다.
영역을 지정할 때 FAWE 선택을 먼저 만듭니다. `/region`은 `/rg`로 줄여 쓸 수 있습니다.

| 명령어 | 용도 |
|---|---|
| `/rg define <구역>` | 선택 영역 등록 |
| `/rg redefine <구역>` | 경계 재지정 |
| `/rg list` | 목록 |
| `/rg info <구역>` | 정보 |
| `/rg select <구역>` | 영역 선택 |
| `/rg addmember <구역> <name>` | 멤버 추가 |
| `/rg removemember <구역> <name>` | 멤버 제거 |
| `/rg addowner <구역> <name>` | 소유자 추가 |
| `/rg removeowner <구역> <name>` | 소유자 제거 |
| `/rg flag <구역> pvp deny` | PVP 차단 |
| `/rg flag <구역> tnt deny` | TNT 차단 |
| `/rg flag <구역> creeper-explosion deny` | 크리퍼 폭발 차단 |
| `/rg setpriority <구역> <number>` | 우선순위 |
| `/rg setparent <자식> <부모>` | 상속 |
| `/rg remove <구역>` | 보호 영역 삭제 |

영역 삭제는 보호 설정 제거이며 건축물 삭제가 아닙니다.
`build` 플래그를 일괄 설정하면 멤버 권한 구성이 달라질 수 있으므로 먼저 현재 플래그를 확인합니다.
ServerCore 집의 잠긴 청크는 WorldGuard 멤버 권한만으로 해방되지 않습니다.

출처: [영역 명령어](https://worldguard.enginehub.org/en/latest/regions/commands/),
[우선순위·상속](https://worldguard.enginehub.org/en/latest/regions/priorities/).
