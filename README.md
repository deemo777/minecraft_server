# ServerCore 2.0 — 대화창(dialog) 메뉴 + 도감

Paper **26.3** / Java 25. 플레이어는 바닐라 클라이언트 + 서버 리소스팩.

## 무엇이 들어 있나
| 기능 | 조작 |
|---|---|
| 마을 메뉴 (대화창) | **G 키**, `/메뉴`, Shift + F |
| 도감 (대화창, 15칸 × 7줄) | 메뉴의 📖 도감, `/도감 [페이지]` |
| 도감 등록 | 도감 칸 클릭 → 내 가방에 `required`개가 있으면 그만큼 가져가고 등록 |
| 페이지 이동 | 가운데 줄 양 끝 ◀ ▶ |
| 관리 | `/도감 reset <플레이어>`, `/코어 reload` (OP) |

미등록 칸은 **빨간 숫자**(필요 개수), 등록한 칸은 **초록 체크 + 흰 숫자**.

---

## 설치 순서

### 1. 플러그인 빌드
IntelliJ에서 이 폴더 열기 → Gradle 탭 → `build` → `build/libs/ServerCore-2.0.0.jar`
→ 서버 `plugins` 폴더에 넣기

> 예전 ServerCore(1.x)를 쓰고 있었다면 jar를 교체하고 `plugins/ServerCore/` 의
> `menu.yml`, `dogam.yml` 을 지운 뒤 서버를 켜서 새 양식으로 다시 만드세요. (도감 기록 `dogam-data/` 는 그대로 써요)

### 2. 서버 켜기 → 한 번 재시작
처음 켜면 콘솔에 *"G 키 메뉴 데이터팩을 새로 만들었어요. 재시작…"* 이 떠요.
G 키 메뉴는 서버가 켜질 때만 등록돼서 **한 번 재시작**해야 G 키가 작동해요.
(`menu.yml` 을 고쳤을 때도 G 키 쪽은 재시작 필요. `/메뉴`, Shift + F 는 `/코어 reload` 로 바로 반영)

### 3. 리소스팩 만들기 (내 컴퓨터에서)
```
cd tools
pip install pillow pyyaml
python build_pack.py --jar "%APPDATA%\.minecraft\versions\26.3\26.3.jar" --dogam "서버\plugins\ServerCore\dogam.yml"
```
- `--dogam` 은 **서버에서 실제로 쓰는 dogam.yml** 을 넣으세요 (항목 순서 = 아이콘 번호)
- 끝나면 `tools/build/servercore_pack.zip` 과 **SHA-1 값**이 나와요
- 아이콘을 못 찾은 항목은 `?` 로 들어가고 이름이 출력돼요

### 4. 리소스팩 올리기
zip 을 바로 다운로드되는 주소로 업로드 (mc-packs.net, GitHub Releases 등)
**서버를 끈 상태에서** `server.properties`:
```
resource-pack=<다운로드 주소>
resource-pack-sha1=<build_pack.py가 알려준 값>
require-resource-pack=true
```

### dogam.yml 항목을 바꿨을 때
항목 추가·삭제·**순서 변경** → 3, 4번 다시 (zip·SHA-1 새로) → `/코어 reload`
순서가 어긋나면 아이콘이 다른 항목에 붙어 보여요.

---

## 리소스팩 없이 쓰기
`config.yml` → `resourcepack.enabled: false`
칸에 아이콘 대신 글자(이름 첫 글자 / ✔)가 나와요. 테스트할 때 편해요.

## 알아둘 점
- **버튼 모양 변경은 전체 적용**: 리소스팩이 마인크래프트 기본 버튼 그림을 도감 칸 모양으로 바꿔서,
  서버 접속 중에는 설정 화면 등 다른 버튼도 같은 모양이 돼요.
- 대화창 칸 높이는 마인크래프트가 20으로 고정. 너비는 `dogam.yml` 의 `grid.button-width`.
- 칸을 누르면 잠깐 "응답 기다리는 중" 화면이 보였다가 갱신된 도감이 다시 떠요 (정상).
- 등록 판정은 **아이템 종류만** 봐요 (이름 바꾼 밀도 밀). 가방 36칸만 세고 갑옷·왼손은 제외.
- 가로 15칸이 한 화면에 안 들어가면(GUI 크기 큼) 마인크래프트 설정의 GUI 크기를 줄이거나 `grid.columns` 를 줄이세요.

---

## 구조
```
src/main/java/com/htk/core
 ├─ ServerCore, CoreCommand
 ├─ common/
 │   ├─ Glyphs        리소스팩 그림 글자 번호 (⚠ tools/build_pack.py 와 약속)
 │   ├─ Dialogs       대화창 버튼 (누르면 서버에서 실행)
 │   ├─ Text, Feature
 ├─ menu/
 │   ├─ MenuFeature           /메뉴, Shift+F, 접속 안내
 │   ├─ MenuConfig            menu.yml
 │   └─ QuickActionInstaller  G 키용 데이터팩 생성 (world/datapacks/servercore_quick_action)
 └─ dogam/
     ├─ DogamFeature       클릭 → 개수 확인 → 가져가기 → 등록 → 보상
     ├─ DogamDialog        15×7 격자, ◀ ▶, 제목·진행도 막대
     ├─ CollectionRegistry dogam.yml (항목 순서 = 아이콘 번호)
     ├─ PlayerDataStore    plugins/ServerCore/dogam-data/<UUID>.yml
     └─ DogamCommand

tools/
 ├─ build_pack.py     리소스팩 만들기 (아이콘 자동 생성 + 고정 그림 + 폰트 + zip + SHA-1)
 ├─ make_template.py  고정 그림(체크·숫자·화살표·막대·버튼) 다시 그리기
 └─ pack_template/    고정 그림
```

## 리소스팩 글자 약속 (Glyphs.java ↔ build_pack.py)
| 글자 | 그림 | 크기 / ascent |
|---|---|---|
| `` | 체크 | 16 / 11 |
| `` `` | 진행도 막대 틀 / 채움 | 9 / 8, 7 / 7 |
| ``~`` | 화살표 ◀ ▶ (켜짐/꺼짐) | 16 / 11 |
| ``~`` | 숫자 0~9 | 7 / 2 |
| ` + n` | 도감 n번째 항목 아이콘 | 16 / 11 |
| ``~`` / ``~`` | 왼쪽 / 오른쪽 1,2,4…128px | — |
