# Multiverse-Core — 월드 관리

월드 관리 명령어 정리입니다.

여러 월드를 관리합니다. 설치 파일은 5.8.2-pre.2입니다. 아래는 운영에 자주 쓰는 명령어입니다.
관리 권한이 필요합니다. 모든 하위 명령은 [공식 명령어 문서](https://mvplugins.org/core/fundamentals/commands-usage/)에서 확인합니다.

| 명령어 | 용도 |
|---|---|
| `/mv help` | 도움말 |
| `/mv version` | 버전 |
| `/mv list` | 등록 월드 |
| `/mv info [월드]` | 월드 정보 |
| `/mv tp <월드>` | 이동 |
| `/mv create <name> normal` | 새 월드 |
| `/mv import <name> normal` | 기존 월드 등록 |
| `/mv load <월드>` | 로드 |
| `/mv unload <월드>` | 언로드 |
| `/mv setspawn` | 현재 위치를 스폰으로 |
| `/mv spawn` | 현재 월드 스폰으로 |
| `/mv modify village set difficulty peaceful` | 마을 난이도 |
| `/mv modify lobby set difficulty peaceful` | 로비 난이도 |
| `/mv reload` | 설정 리로드 |
| `/mv remove <월드>` | 등록 제거, 폴더 보존 |
| `/mv delete <월드>` | 월드 삭제 |
| `/mv confirm` | 요청한 작업 확정 |
| `/mv modify village set respawn-world lobby` |
| `/mv modify wild set respawn-world lobby` |
| `/mv modify lobby set respawn-world lobby` |

환경은 `normal`, `nether`, `the_end` 중 선택합니다. 폴더가 있는 월드는 `create` 대신 `import`를 검토합니다.
`delete`·`regen`은 월드 데이터를 없애거나 재생성하므로 백업 후에만 사용합니다.

ServerCore 메뉴 이동 지점은 별도입니다. 같은 자리를 쓰려면 `/mv setspawn`과
`/servercore setpoint village`를 각각 설정합니다.
