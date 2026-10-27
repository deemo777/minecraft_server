# NPC 디자인

7종 모두 직물·가죽·금속의 도트 명암과 직업 소품이 있는 전용 3D 외형입니다.

| NPC | 특징 | 생성 |
|---|---|---|
| 무기 상인 | 고글·청록 작업복·가죽 앞치마·검집 | `/무기상인 create` |
| 이장 | 흰 수염·올리브 코트·버건디 조끼·열쇠·장부 | `/mayornpc create` |
| 대장장이 | 그을음·작업 앞치마·장갑·망치 | `/blacksmithnpc create` |
| 생선 상인 | 낚시 모자·푸른 스카프·생선 바구니 | `/shopnpc create fish` |
| 농작물 상인 | 밀짚모자·초록 작업복·당근·씨앗 주머니 | `/shopnpc create crops` |
| 광물 상인 | 광부 모자·램프·보석 가방·곡괭이 | `/shopnpc create ores` |
| 광태 요리사 | 실제 얼굴 유지·제빵모자·자수 앞치마·밀대 | `/cookienpc create` |

기존 주민 NPC는 청크 로드 시 같은 위치에서 전환됩니다. 전환 실패 시 기존 주민을 지우지 않습니다.
등록된 상점 ID·이장 대화·강화 기능은 유지합니다. 임의로 추가한 상점 ID는 무기 상인 외형을 기본으로 사용합니다.

## 방향·제거

각 NPC 명령 뒤에 `face` 또는 `remove`를 사용합니다. 5블록 안의 가장 가까운 해당 NPC에 적용됩니다.

```text
/mayornpc face
/blacksmithnpc face
/shopnpc face
/cookienpc face
/무기상인 face
```

원하는 NPC 방향의 반대를 보고 실행하세요. `shopnpc face`는 가장 가까운 상점 상인의 방향을 바꿉니다.
새 플러그인과 **최신 통합 리소스팩을 함께 적용**하세요. 주민 소리를 사용하지 않습니다.
ItemDisplay·Interaction은 월드와 함께 백업하고 엔티티 청소 플러그인이 지우지 않게 설정합니다.
