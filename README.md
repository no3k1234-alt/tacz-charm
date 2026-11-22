# TaCZ: Charm (총기 참)

**An unofficial add-on for TACZ Refabricated (Fabric 1.21.1).**
Adds a new **Charm** attachment slot to TACZ guns. Hang a keychain charm on your rifle and watch it swing.

> 한국어 설명은 아래에 있어요.

## Features
- New **Charm** slot in the gun refit screen (next to scope, muzzle, grip…)
- 4 charms: **MF Tag, Marine Dog Tags, Cross, Shell Casing**
- Charms hang from a small rail mount and **swing with physics**: chain links, weight, collisions with the gun body, the ring sliding around the mount
- The mount sits on the scope rail, so charms fit **only guns that can take a scope**. Guns from other gun packs get a charm spot placed automatically
- Per-gun position tweak with `/charmpos` (or `/참위치`): an in-game editor with buttons and arrow keys
- Charms are cosmetic: no stat changes

## Requirements
- Fabric Loader 0.16+, Minecraft 1.21.1
- [Fabric API](https://modrinth.com/mod/fabric-api)
- TACZ Refabricated
- **Install on both server and client** (the attachment slot list must match)

## Notes
- Not affiliated with or endorsed by the TACZ team.
- Guns that cannot take a scope cannot take a charm either. These also cannot take a charm even though they take a scope: Winchester 1866, Springfield 1873, Double-barrel shotguns (long/short), Kar98k.
- License: code GPL-3.0-only, charm models/textures/icons CC BY-NC-SA 4.0 (see LICENSE-ASSETS). All charm art was made for this mod; no TACZ assets are included. Source code is attached to each version.

---

## 한국어

**TACZ Refabricated(패브릭 1.21.1) 비공식 애드온입니다.**
TACZ 총의 부착물 칸에 **참(키링)** 칸을 하나 더 넣습니다. 총에 참을 달면 움직일 때마다 흔들려요.

### 기능
- 개조 화면에 **참** 칸 추가 (조준경·총구·손잡이 칸 옆)
- 참 4종: **MF 태그, 해병대 군번줄, 십자가, 탄피**
- 레일에 다는 작은 마운트에 매달려 **물리로 흔들림**: 사슬 마디·무게, 총몸 충돌, 마운트 고리를 따라 미끄러지는 고리
- 마운트가 조준경 레일에 붙기 때문에 **조준경을 달 수 있는 총에만** 달 수 있음. 다른 총 팩의 총은 자동으로 자리를 잡아 줌
- 총마다 `/참위치`(영어 `/charmpos`) 명령어로 위치 조정 (버튼·방향키 편집 창)
- 꾸미기 전용: 총 성능은 안 바뀜

### 필요한 것
- 패브릭 로더 0.16 이상, 마인크래프트 1.21.1
- Fabric API, TACZ Refabricated
- **서버와 클라이언트 둘 다 설치** (부착물 칸 목록이 같아야 함)

### 참고
- TACZ 제작진과 관계없는 비공식 애드온입니다.
- 조준경을 못 다는 총은 참도 못 답니다. 조준경은 달 수 있지만 참은 막아 둔 총: 윈체스터 1866, 스프링필드 1873, 더블배럴 산탄총(롱·숏), Kar98k
- 라이선스: 코드 GPL-3.0-only, 참 모델·텍스처·아이콘 CC BY-NC-SA 4.0(LICENSE-ASSETS). 그림은 모두 직접 만들었고 TACZ 자료는 들어 있지 않습니다. 소스 코드는 버전마다 같이 올려 둡니다.
