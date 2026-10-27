# 설치 플러그인과 역할

[전체 문서](../README.md)

| 플러그인 | 담당 | 명령어 안내 |
|---|---|---|
| ServerCore | 메뉴·골드·집터·NPC·상점·스킬·도감·HUD·마을 도구 | [보기](ServerCore.md) |
| Multiverse-Core | 여러 월드 등록·생성·이동·월드별 설정 | [보기](Multiverse-Core.md) |
| FastAsyncWorldEdit (FAWE) | 선택 영역의 블록 편집·복사·스키매틱 | [보기](FastAsyncWorldEdit.md) |
| WorldGuard | 공공 구역 보호·PVP·폭발 등의 규칙 | [보기](WorldGuard.md) |

확인한 로컬 JAR: ServerCore 3.12.0, Multiverse-Core 5.8.2-pre.2, FAWE 2.16.1+9e5e49e,
WorldGuard 7.0.20+2410-0b6c3f1. 개발 소스의 새 버전은 ServerCore 3.15.0입니다.
이는 파일 확인 결과이며, OCI에서 현재 실행 중인 버전은 `/version ServerCore` 등으로 따로 확인합니다.
ServerCore 3.13.0부터 마을 도구, 3.15.0부터 고정 부지 해방 기능이 들어갑니다.

FAWE가 WorldEdit 역할을 제공합니다. WorldGuard는 이 WorldEdit 기능에 의존합니다.
개인 집은 ServerCore, 공공 시설 보호는 WorldGuard로 구분하면 관리하기 쉽습니다.
지역 보호가 겹치면 양쪽에서 작업을 막을 수 있으므로 WorldGuard 허가만으로 집터 보호가 해제되지는 않습니다.
