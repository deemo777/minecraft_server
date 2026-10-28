# 제공된 물뿌리개 디자인 (3.31.17)

| 강화 | 원본 모델 |
|---|---|
| +0~4 | bucket / water_bucket → iron_watering_can_empty/full → 3d_watering_can |
| +5~9 | gilded_phoenix_watering_can_empty/full |
| +10~14 | demons_blood_watering_can_empty/full |
| +15~19 | viridian_watering_can_empty/full |
| +20 | winterthorn_watering_can_empty/full |

`references/watering_cans/`에 제공된 JSON·PNG를 보관하며, 0단계 부모 모델은 `vanilla/`에 함께 보관합니다.
`python tools/make_watering_models.py`로 열 가지 모델·텍스처를 재생성합니다.
팩 빌드 후 `python tools/verify_watering_models.py`로 42가지 강화·물 상태 선택과 원본 일치 여부를 검증합니다.
부모 모델은 병합하고 텍스처 경로를 `servercore:block/village_watering_can_<단계>[_full]`로 옮깁니다.
형상·UV·display·원본 credit와 PNG 바이트는 유지합니다. 일반 도구 생성기도 이 생성기를 사용합니다.
강화 단계는 custom_model_data floats[0], 물 보유 여부는 flags[0]로 선택합니다.
플러그인의 `watering_can_full` PDC가 물 보유 상태를 저장하고 강화·재접속 시에도 flags[0]에 반영합니다.
이전 팔레트·조각형 물뿌리개 설명은 이전 디자인 기록입니다.

---

# 제공된 도끼 디자인 (3.31.16)

| 강화 | 원본 모델 |
|---|---|
| +0~4 | viridian_greataxe |
| +5~9 | black_iron_greataxe |
| +10~14 | gloomsteel_greataxe |
| +15~19 | berserkers_greataxe |
| +20 | gilded_phoenix_greataxe |

`references/axes/`에 사용자 제공 원본 JSON·PNG를 보관합니다.
`make_tool_models.py`는 형상·UV·색상·display를 유지하고 텍스처 경로만
`servercore:block/village_axe_<단계>`로 바꿉니다. 단계별 PNG는 원본 그대로 복사합니다.
이전 iron_greataxe용 날 확대·1인칭 위치 및 배율 보정은 새 모델에 적용하지 않습니다.
이전 원본 `references/iron_greataxe.json`은 디자인 기록으로 보관합니다.

---

# 제공된 낫 디자인 (3.31.15)

| 강화 | 원본 모델 |
|---|---|
| +0~4 | iron_hay_sickle |
| +5~9 | windreaper |
| +10~14 | crystal_frostcythe |
| +15~20 | azure_scythe (+20도 +15와 동일) |

낫은 기존 `village_hoe` 아이템을 사용합니다. `references/scythes/`에 사용자 제공 원본을 보관합니다.
`make_tool_models.py`는 형상·UV·텍스처·display를 유지하고 텍스처 경로만
`servercore:block/village_hoe_<단계>`로 바꿉니다. 단계별 PNG는 원본 그대로 복사합니다.
`azure_scythe_e.png`는 원본 보조 텍스처로 보관하며, 제공된 JSON은 기본 PNG만 참조합니다.
아래의 덩굴·꽃 세공 낫 설명은 이전 디자인 기록입니다.

---

# 제공된 곡괭이 디자인 (3.31.14)

| 강화 | 원본 모델 |
|---|---|
| +0~4 | talonpick |
| +5~9 | azure_pickaxe |
| +10~14 | amethyst_greatpick |
| +15~20 | viridian_pickaxe (+20도 +15와 동일) |

`references/pickaxes/`의 사용자 제공 JSON·PNG를 원본으로 보관합니다.
`make_tool_models.py`는 형상·UV·텍스처·display를 유지하고 텍스처 경로만
`servercore:block/village_pickaxe_<단계>`로 바꿉니다. PNG는 원본 바이트 그대로 복사합니다.
아래의 보라색 팔레트·세공 곡괭이 설명은 이전 디자인 기록입니다.

---

# 낚싯대 모델 (3.23.0)

`python tools/make_fishing_models.py --preview`로 0/5/10/15/20강과 각 cast 모델을 생성합니다.
`village_fishing_rod_<단계>.json` 및 `_cast.json`, 팔레트 텍스처는 `textures/`에 저장됩니다.
통합 팩은 강화 단계와 `minecraft:fishing_rod/cast`를 함께 판정합니다. 다른 도구 모델은 다시 생성하지 않습니다.

---

# 1인칭 자세와 도구별 개성 조정

- 일반 도구 오른손: rotation [-12, -12, -20], translation [0.6, 0.5, -2], scale 0.58.
  이전 [0, -35, -20]에서 Y를 +23도, X를 -12도 조정해 넓은 날 면과 앞으로 기운 머리를 보여줍니다.
- 왼손은 Y·Z 회전을 반대로 적용합니다.
- 물뿌리개는 upright에 가까운 별도 자세 [-5, -20, 0], scale 0.75를 사용합니다.
- 곡괭이: 위 표의 제공된 UV 모델·텍스처 및 원본 display를 사용합니다.
- 도끼: 위 표의 다섯 단계별 UV 모델·텍스처 및 원본 display를 사용합니다.
- 낫(괭이): 위 표의 제공된 UV 모델·텍스처 및 원본 display를 사용합니다.
- 물뿌리개: 남색 물통·물방울 무늬·운반 손잡이·긴 직선 주둥이·구멍 난 살수판.
  주전자형 몸통·굽은 주둥이·날개·큰 보석·궤도 장식은 제거했습니다.
- 입체 도구는 palette_for(tool)로 팔레트를 따로 만듭니다. 도끼는 원본 JSON의 요소와 UV 면을 유지하고, 단계별 텍스처를 block atlas에서 불러옵니다.
- 미리보기: tools/build/model_preview/tool_identity.png. 실제 클라이언트의 원근·손 위치는 게임 안 최종 확인이 필요합니다.

---

# 마을 도구: 세공 판타지 컬렉션

## 참고 이미지에 맞춘 도트 스타일 조정

- 크림색·노란 금색 위주의 조합을 남색 외곽선, 은청색 금속, 청록·하늘색·보라 포인트로 조정했습니다.
- 곡괭이의 절삭 날 면적을 이전보다 약 1.5배 넓히고, 진한 보라·자홍 보라·은백색 참고 팔레트로 정리했습니다. 손잡이 아래 감개는 제거했습니다. 낫의 날 길이와 굽힘은 줄이고 상위 강화의 보조 날도 짧게 조정했습니다.
- 곡면 외곽에 진한 도트 테두리와 밝은 절삭면을 나란히 배치했습니다.
- 세공·손잡이·실제 XYZ 부품 구조와 강화별 형태 변화는 유지합니다.
- 코드의 `gold` 계열 이름은 기존 부품 구분용 이름이며, 현재 팔레트에서는 은빛·연보라 금속으로 표시합니다.
- `refinement_comparison.png`에서 곡괭이·낫 +20의 수정 전후를 비교할 수 있습니다.

이 버전은 큰 직육면체 조립형을 곡선형 날·얇은 절삭면·정교한 보석·금속 세공으로 교체합니다.
그림을 한 방향으로 단순 돌출한 모델이 아닙니다. 날 경계까지의 거리에 따라 실제 Z 두께를 바꾸고,
손잡이·소켓·보석·뒷면 세공·깊이 방향 고리를 별도 XYZ 입체 부품으로 만듭니다.

## 강화 변화

| 도구 | +0 / +5 | +10 | +15 | +20 |
|---|---|---|---|---|
| 곡괭이 | talonpick / azure_pickaxe | amethyst_greatpick | viridian_pickaxe | +15와 동일 |
| 도끼 | viridian_greataxe / black_iron_greataxe | gloomsteel_greataxe | berserkers_greataxe | gilded_phoenix_greataxe |
| 괭이 | iron_hay_sickle / windreaper | crystal_frostcythe | azure_scythe | +15와 동일 |
| 물뿌리개 | 둥근 은청색 통·작은 원석 / 세공 보석 | 청색 주전자·은빛 문양·잎 | 자수정 몸통·꽃 | 은청색 몸통·큰 보석·날개·입체 궤도 |

+0은 소박한 판타지 수제 도구, +5는 보강형입니다. +10과 +20은 실루엣과 장식 구성이 크게 달라집니다.
기존 아이템 ID와 게임 내 이름, 5강 단위 전환 규칙은 유지합니다. 낚싯대는 변경하지 않습니다.

## 구조와 크기

- 모델 곡면을 약 0.14~0.19칸 단위로 세분화하고, 가장자리로 갈수록 얇게 만듭니다.
- 같은 두께·색의 이웃한 면은 사각형으로 합쳐 불필요한 조각을 줄입니다.
- 본체와 별도로 앞뒤 마름눈형 보석, 금속 세공, 가죽 감개, 잎·꽃을 모델링합니다.
- 둥근 물뿌리개는 단면을 쌓아 만들고, 휘어진 주둥이와 뒤쪽 손잡이를 붙입니다.
- 기존 버전보다 길이와 장식 범위를 늘리고 손에 드는 display 크기도 키웠습니다.
- 인벤토리는 각 모델의 회전된 경계를 계산해 중앙과 배율을 자동으로 맞춥니다.
- 모든 부품은 Java 모델 좌표 범위(-16~32)와 회전 각도(0/±22.5/±45)를 따릅니다.
- 세분화로 이전 단순 모델보다 부품 수가 늘었습니다. 실제 FPS는 클라이언트에서 확인해야 합니다.

## 재생성

`python tools/make_tool_models.py --preview`

필요: Pillow, numpy. 게임 실행에는 Python이 필요하지 않습니다.
디자인 수정: `fine_make`, `sculpt`, `jewel`, `shaft`, `fine_can`.
PNG는 64×64 색상표이며 실제 형상은 JSON의 elements에 있습니다.

미리보기(`tools/build/model_preview/`):

- `collection.png` / `all_tools.png`: 모든 강화 단계의 정면 사선·옆면.
- `detail_showcase.png`: +20 네 도구의 큰 세공 미리보기.
- `depth_comparison.png`: +0 / +10 / +20의 입체 비교.
- `held_angles.png`: 1인칭 display 회전값을 적용한 정사영 비교.
- `village_*.png`: 각 모델의 최신 미리보기.

미리보기는 실제 JSON 부품을 계산해 그린 것이며 게임 화면 캡처가 아닙니다.
클라이언트에서 조명·손 위치·원근과 실제 프레임률을 최종 확인해야 합니다.

## 팩 연결

- `village_<도구>_<단계>.json`, 단계 0/5/10/15/20.
- 입체 모델 텍스처: `servercore:block/village_<도구>_<단계>`.
- 도끼 텍스처: `servercore:block/village_axe_<단계>`.
- 모델: `servercore:item/village_<도구>_<단계>`.
- custom_model_data index 0의 range_dispatch: +0 fallback, 5/10/15/20 전환.
- 도끼와 나머지 입체 도구는 개별 `elements`와 면별 UV 텍스처를 사용합니다.
- 보석 glint의 light_emission은 주변 블록을 밝히는 동적 광원이 아닙니다.

기존 `build_pack.py`로 재빌드할 수 있습니다. 이번 팩은 다른 기존 리소스를 보존하고
20개 모델, 입체 도구용 팔레트, 도끼 UV 텍스처와 4개 단계 정의를 교체한 `tools/build/servercore_pack.zip`입니다.
서버 업로드와 resource-pack 설정 변경은 별도입니다.
