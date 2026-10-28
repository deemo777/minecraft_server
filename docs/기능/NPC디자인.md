# NPC 디자인

7종 모두 표준 64×64 스킨 UV로 재제작했습니다. 머리·몸·팔·다리별로 직접 그린 도트와 투명 외피를 사용하며 반복 타일 재질은 쓰지 않습니다.

| NPC | 특징 | 생성 |
|---|---|---|
| 무기 상인 | 고글·청록 작업복·가죽 앞치마·검집 | `/npc create weaponmerchant` |
| 이장 | 흰 머리·올리브 코트·버건디 조끼·열쇠·장부 | `/npc create mayor` |
| 대장장이 | 검은 머리·작업 앞치마·장갑·망치 | `/npc create blacksmith` |
| 생선 상인 | 낚시 모자·푸른 스카프·생선 바구니 | `/npc create fish` |
| 농작물 상인 | 밀짚모자·초록 작업복·당근·씨앗 주머니 | `/npc create crops` |
| 광물 상인 | 광부 모자·램프·보석 가방·곡괭이 | `/npc create ores` |
| 광태 요리사 | 실제 얼굴 유지·제빵모자·자수 앞치마·밀대 | `/npc create cookie` |

기존 주민 NPC는 청크 로드 시 같은 위치에서 전환됩니다. 전환 실패 시 기존 주민을 지우지 않습니다.
등록된 상점 ID·이장 대화·강화 기능은 유지합니다. 임의로 추가한 상점 ID는 무기 상인 외형을 기본으로 사용합니다.

## 방향·제거

각 NPC 명령 뒤에 `face` 또는 `remove`를 사용합니다. 5블록 안의 가장 가까운 해당 NPC에 적용됩니다.

```text
/mayornpc face
/blacksmithnpc face
/shopnpc face
/cookienpc face
/weaponmerchantnpc face
```

원하는 NPC 방향의 반대를 보고 실행하세요. `shopnpc face`는 가장 가까운 상점 상인의 방향을 바꿉니다.
새 플러그인과 **최신 통합 리소스팩을 함께 적용**하세요. 주민 소리를 사용하지 않습니다.
ItemDisplay·Interaction은 월드와 함께 백업하고 엔티티 청소 플러그인이 지우지 않게 설정합니다.

원본 스킨은 `tools/make_npc_skins.py`에서 생성합니다. 참고 스킨은 `tools/art/npc_references/`, 요리사 얼굴 원본은 `tools/art/deemo777_skin.png`입니다. 최신 팩만 교체하며 3.22.0 플러그인은 유지할 수 있습니다.
