# TaCZ: Charm (총기 참)

**An unofficial add-on for TACZ. Builds for Fabric 1.21.1, Forge 1.20.1 and NeoForge 1.21.1.**
Adds a new **Charm** attachment slot to TACZ guns. Hang a keychain charm on your rifle and watch it swing.

> 한국어 설명은 아래에 있어요.

## Features
- New **Charm** slot in the gun refit screen (next to scope, muzzle, grip…)
- 10 charms: **MF Tag, Marine Dog Tags, Cross, Shell Casing, Compass, Dice, Four-Leaf Clover, Skull, Star Medal, Creeper Head**
- Three chain lengths per charm (short / normal / long), switched in the `/charmpos` editor or with the `[` `]` keys
- Charms hang from a small rail mount and **swing with physics**: chain links, weight, collisions with the gun body, the ring sliding around the mount
- The mount sits on the scope rail, so charms fit **only guns that can take a scope**. Guns from other gun packs get a charm spot placed automatically
- Per-gun position tweak with `/charmpos` (or `/참위치`): an in-game editor with buttons and arrow keys
- Charms are cosmetic: no stat changes

## Requirements
| Loader | Minecraft | Also needs |
|---|---|---|
| Fabric 0.16+ | 1.21.1 | [Fabric API](https://modrinth.com/mod/fabric-api), TACZ Refabricated |
| Forge 47+ | 1.20.1 | Timeless and Classics Zero (TACZ) 1.2.0+ |
| NeoForge 21.1+ | 1.21.1 | Timeless and Classics Zero (TACZ) 1.2.0+ |

- Use the jar that matches your loader (`+forge-1.20.1`, `+neoforge-1.21.1`, or the plain Fabric jar).
- **Install on both server and client** (the attachment slot list must match)

## Notes
- Not affiliated with or endorsed by the TACZ team.
- Guns that cannot take a scope cannot take a charm either. These also cannot take a charm even though they take a scope: Winchester 1866, Springfield 1873, Double-barrel shotguns (long/short), Kar98k.
- License: see [License](#license) below. All charm art was made for this mod; no TACZ assets are included. Source code is attached to each version.

---

## 한국어

**TACZ 비공식 애드온입니다. 패브릭 1.21.1 · 포지 1.20.1 · 네오포지 1.21.1 버전이 있습니다.**
TACZ 총의 부착물 칸에 **참(키링)** 칸을 하나 더 넣습니다. 총에 참을 달면 움직일 때마다 흔들립니다.

### 기능
- 개조 화면에 **참** 칸이 추가됩니다 (조준경·총구·손잡이 칸 옆)
- 참 10종: **MF 태그, 해병대 군번줄, 십자가, 탄피, 나침반, 주사위, 네잎클로버, 해골, 별 메달, 크리퍼 머리**
- 참마다 줄 길이 3가지(짧은 줄·보통·긴 줄), `/참위치` 편집 창 버튼이나 `[` `]` 키로 바꿈
- 레일에 다는 작은 마운트에 매달려 **물리적으로 흔들립니다**: 사슬 마디·무게, 총몸 충돌, 마운트 고리를 따라 미끄러지는 고리
- 마운트가 조준경 레일에 붙기 때문에 **조준경을 달 수 있는 총에만** 달 수 있으며. 다른 총 팩의 총은 자동으로 자리를 잡아 줍니다
- 총마다 `/참위치`(영어 `/charmpos`) 명령어로 위치 조정 (버튼·방향키 편집 창)
- 꾸미기 전용: 총 성능은 안 바뀝니다

### 필요한 것
- 패브릭 1.21.1: 패브릭 로더 0.16 이상, Fabric API, TACZ Refabricated
- 포지 1.20.1: 포지 47 이상, TACZ(Timeless and Classics Zero) 1.2.0 이상
- 네오포지 1.21.1: 네오포지 21.1 이상, TACZ(Timeless and Classics Zero) 1.2.0 이상
- 로더에 맞는 jar를 쓰세요 (`+forge-1.20.1`, `+neoforge-1.21.1`, 표시 없는 것 = 패브릭)
- **서버와 클라이언트 둘 다 설치** (부착물 칸 목록이 같아야 함)

### 참고
- TACZ 제작진과 관계없는 비공식 애드온입니다.
- 조준경을 못 다는 총들은 참도 못 답니다. (조준경은 달 수 있지만 참은 막아 둔 총: 윈체스터 1866, 스프링필드 1873, Kar98k)
- 라이선스: 코드 GPL-3.0-only, 참 모델·텍스처·아이콘 CC BY-NC-SA 4.0(LICENSE-ASSETS). 그림은 모두 직접 만들었고 TACZ 자료는 들어 있지 않습니다. 소스 코드는 버전마다 같이 올려 둡니다.


---

## Building from source

All builds need Java 21 and the matching TACZ jar in `libs/` (download it yourself from its mod page).

| Build | Folder | TACZ jar in `libs/` | Command |
|---|---|---|---|
| Fabric 1.21.1 | repo root | `tacz-refabricated-1.0.1-neo1.2.0-hotfix+mc1.21.1.jar` | `gradlew build` |
| Forge 1.20.1 | `forge-1.20.1/` | `tacz-forge-1.20.1-1.2.0-hotfix.jar` | `cd forge-1.20.1` then `gradlew build` |
| NeoForge 1.21.1 | `neoforge-1.21.1/` | `tacz-neoforge-1.21.1-1.2.0-hotfix.jar` | `cd neoforge-1.21.1` then `gradlew build` |

Jars are written to each folder's `build/libs/`. All three share the charm pack in `pack/`.

## Models

`blockbench/` holds the Blockbench source files of the charms and the rail mount (CC BY-NC-SA 4.0).
`charm_dogtag.bbmodel` is the full 3D original; `charm_dogtag_bake.bbmodel` is the low-block version used in game, with the relief baked into the texture (see `blockbench/images/dogtag_bake_comparison.png`).

## License

| Part | License |
|---|---|
| Source code | [GPL-3.0-only](LICENSE) |
| Charm models, textures, icons and Blockbench files (`pack/`, `blockbench/`, images under `src/**/textures/`) | [CC BY-NC-SA 4.0](LICENSE-ASSETS) |

See [COPYRIGHT.txt](COPYRIGHT.txt) for details.
코드는 GPL-3.0-only, 참 모델·텍스처·아이콘·블록벤치 파일은 CC BY-NC-SA 4.0입니다(출처 표시, 비영리, 같은 조건으로만 배포).

Not affiliated with or endorsed by the TACZ team, Mojang or Microsoft.
