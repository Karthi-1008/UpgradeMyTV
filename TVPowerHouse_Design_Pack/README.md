# TV Power House — Production UI/UX Design Pack  (for Antigravity)

**Your job:** restyle the **TV home screen (Android TV app)** and the **phone web remote (`web/`)** so they match the reference images in this pack, **pixel-for-intent**. Do **not** change features, API calls, ports, permissions or server logic. This is a visual + interaction upgrade only.

Look at these first (they are the source of truth, open them before coding):

| What | File |
|---|---|
| TV home – server active | `assets/tv/tv_home_active_1920x1080.png` |
| TV home – starting | `assets/tv/tv_home_starting_1920x1080.png` |
| TV home – no network | `assets/tv/tv_home_nonet_1920x1080.png` |
| TV home – server stopped | `assets/tv/tv_home_stopped_1920x1080.png` |
| Phone remote – before / after | `assets/web/phone_remote_before_780x1688.png` / `phone_remote_after_780x1688.png` |
| Phone tabs after | `assets/web/phone_screen_after_780x1688.png`, `phone_tools_after_fullpage.png` |
| Live TV reference (open in Chrome, add `?s=active\|starting\|nonet\|stopped`) | `reference/tv_preview.html` |
| QR geometry reference | `reference/styled-qr.js` |

> Typography in the PNGs is approximate (the sandbox that made them had no Manrope). Use the fonts in §5.

---

## 0. Files in this pack and where they go

| Pack file | Copy to project | Notes |
|---|---|---|
| `android/java/StyledQrRenderer.java` | `tvpowernew_src/src/com/gathin/tvpowerhub/` | New. Compile-checked. Uses only framework classes + existing `io.nayuki.qrcodegen`. |
| `android/res/layout/activity_main.xml` | `tvpowernew_src/res/layout/` | **Replaces** existing. Keeps every id `MainActivity` already uses (`tvName, statusRow, statusDot, tvStatus, tvUrl, btnRestart, ivQr, ivNoNet`) so it still compiles. New ids: `ivLogo, statusRing, qrCard, qrShimmer, qrOverlay`. NOTE `ivNoNet` is now the icon *inside* the dark overlay tile `qrOverlay` — toggle **`qrOverlay`** visibility, and set its image to `ic_wifi_off` (NO_NETWORK) or `ic_refresh` (STOPPED). |
| `android/res/drawable/*.xml` | `tvpowernew_src/res/drawable/` | New + replaces `qr_card_bg.xml`, `btn_tv_selector.xml`, `dot.xml`, **`ic_wifi_off.xml`** (new one is light-coloured, the old grey one is invisible on the dark tile). |
| `android/res/values/colors.xml`, `strings.xml` | `tvpowernew_src/res/values/` | Merge; adds `qr_*` colours and `qr_cd`. |
| `assets/brand/logo_*.png` | `res/drawable/logo.png` (use `logo_512.png`), web `assets/` | Existing logo, pre-sized. |
| `web/premium.css` | `assets/web/css/premium.css` | Load **after** `app.css`. Additive, delete to revert. |
| `assets/tokens/*` | reference only | Tokens already match `tokens.css`; `bg` darkened to `#0B0C0E` on TV only. |

New resources you must still create (not in this pack because they need network/OFL downloads):
`res/font/manrope.ttf` (variable, wght 600–800) and `res/font/jetbrains_mono.ttf` (Medium) from Google Fonts / the official JetBrains Mono repo (both OFL). If a download is impossible, use `android:fontFamily="sans-serif-medium"` / `"monospace"` — layout still works.
Build uses `aapt2` + `javac` (no Gradle, no AndroidX): **do not add any library**. `minSdk 26` so `res/font` is allowed.

---

## 1. Design language (one sentence)
Dark cinema-room UI, warm near-black, **one** confident orange accent (`#FF6B3D`), soft glows instead of hard borders, large readable type, every control has a clear idle / focused / pressed state. Calm, fast, nothing decorative that does not help.

Colours (all in `assets/tokens/design-tokens.json`):
`bg #0B0C0E · surface #17181B / #1E2024 / #272A30 · text #F2F1EE · muted #A5A7AD · accent #FF6B3D · ok #4CD39A · warn #F5B544 · danger #FF5A5F`
Rule: orange = *the* primary action or *the* focused thing. Never two competing orange elements except OK/Restart focus.

---

## 2. TV home screen (Android TV)

### 2.1 Content (do not add anything else — no descriptions, no help text)
Left column, top→bottom: **logo (72dp, 18dp radius) + "TV Power House"** · **status pill** · **URL chip** · **Restart server button**.
Right: **QR card**. That is the entire screen.

### 2.2 Canvas & safe area
Design on **960×540 dp** (1080p@320dpi and 720p@213dpi both resolve to this). Padding **72dp sides / 40dp top-bottom** (TV overscan safe). Nothing important within 40dp of an edge.

### 2.3 Sizes
App name 38sp/800 −0.02em · status 20sp/700 · URL 26sp **mono** 500 · button 20sp/700, 56dp high, 18dp radius · QR card **324×324dp**, 36dp radius, 14dp padding.
Background = `@drawable/bg_stage` (dark gradient + warm glow behind QR + faint green glow bottom-left). Already built.

### 2.4 Status pill
Pill (`pill_status_bg`) holding a 12dp dot + text. Dot colour = state colour; a **pulse ring** (`statusRing`) behind it: scale 0.6→1.5, alpha 0.7→0, 2.4s, repeating, `DecelerateInterpolator`. Pulse runs in ACTIVE (slow) and STARTING (faster, 1.2s). Off in STOPPED / NO_NETWORK. Text is **always `text_primary`** (only the dot carries colour — the old amber/red text was hard to read).

| State | Dot | Text | QR card | URL |
|---|---|---|---|---|
| ACTIVE | `#4CD39A` pulse | Server active | full-contrast styled QR | `http://192.168.0.9:8088` |
| STARTING | `#F5B544` fast pulse | Starting… | **full-contrast QR + orange shimmer sweep** (see 2.6) | shown |
| NO_NETWORK | `#F5B544` | No network | `qr_card_skeleton` + wifi-off tile, **no QR** | `—` |
| STOPPED | `#FF5A5F` | Server stopped | QR blurred+faded (alpha .18) + refresh tile; button auto-focused | shown |

**IMPORTANT FIX (visible in the user's screenshot):** the current code sets QR `alpha 0.35` in STARTING, which renders a **grey, unscannable QR** while the phone already shows "Connected". Never dim a QR that encodes a valid URL during STARTING. Also make sure STARTING moves to ACTIVE as soon as the health check passes (the TV screenshot was stuck on "Starting…" while the server was working).

### 2.5 URL chip
`url_chip_bg`, mono 26sp. The **last IP octet is highlighted in accent orange** (e.g. `192.168.0.`**`9`**`:8088`) — the user's IP last digit changes over time, this makes the change obvious. Use a `SpannableString` with `ForegroundColorSpan(accent)` on the last octet only.
When the IP changes (`NetworkMonitor` callback): cross-fade URL text 160ms, re-render QR, and play the QR "refresh" animation (2.6). QR text and URL must always come from the **same** `mCurrentIp` value in the same `renderUi()` call.

### 2.6 QR card — the "beautiful QR"
Do **not** draw the old square black/white modules. Use `StyledQrRenderer.render(text, 720, logoBitmap)`; geometry is defined in `reference/styled-qr.js` and the PNGs. Spec:
- Error correction **HIGH**; centre logo plate = 7 modules wide (<4% of the area) so it is scan-safe.
- Modules: ink `#14110F` on warm paper `#FAF8F4`; rounded **only on outer corners**, neighbouring modules merge into smooth "liquid" shapes.
- Finder eyes: rounded ink ring + **deep-orange `#E0501A` rounded centre** (kept dark enough for scanners).
- Quiet zone 4 modules + 14dp card padding (never smaller).
- Card: `qr_card_bg` (warm-white gradient, hairline, soft orange halo ring) + `elevation 24dp`.
- Render on the background executor, cache by text (keep existing `mLastQrText/mLastQrBitmap` cache), load `logo.png` once and scale it to 128px before passing it.
- **Refresh animation** when text changes: card `scale 0.96→1`, `alpha .6→1`, 220ms, `OvershootInterpolator(1.2)`.
- **STARTING shimmer:** `qrShimmer` view translates from −120dp to card width, 1.8s, linear, repeat; hidden otherwise.
- Never use pure `#FFFFFF`/`#000000` for the QR; never add text under or on the QR.

**Scan acceptance (must pass before you finish):** scan the TV with 3 different phones (the stock camera app, Google Lens, one budget phone) from **3 m** and **5 m** at 720p and 1080p. Must open in < 2 s. If any fails: increase module contrast first, then reduce logo to 5 modules. Never lower ECC.

### 2.7 Restart button & D-pad focus (TV remotes have no touch)
Only one focusable control. States from `btn_tv_selector`: idle = quiet surface; **focused = solid orange, dark text, 3dp halo ring, scale 1.04 (animate 120ms)**; pressed = darker orange. Keep `requestFocus()` on start. Focus must be visible from 3 m. Add `setOnFocusChangeListener` → `animate().scaleX/scaleY(focused?1.04:1f).setDuration(120)`.
Press feedback: on click, icon spins 360° (400ms) while server restarts, status goes STARTING.

### 2.8 `renderUi()` rewrite (summary — keep the existing state machine)
```java
// colour only the DOT; text stays text_primary
void paint(int color, boolean pulse, long pulseMs) {
    statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
    statusRing.setBackgroundTintList(ColorStateList.valueOf(color));
    if (pulse) startPulse(statusRing, pulseMs); else stopPulse(statusRing);
}
// ACTIVE   : paint(OK,   true, 2400); qrCard full alpha; render styled QR; overlay GONE; shimmer GONE
// STARTING : paint(WARN, true, 1200); qrCard full alpha (NOT 0.35); shimmer VISIBLE+animating
// NO_NET   : paint(WARN, false, 0);   qrCard.setBackgroundResource(R.drawable.qr_card_skeleton);
//            ivQr GONE; overlay VISIBLE with ic_wifi_off; url "—"
// STOPPED  : paint(DANGER,false, 0);  ivQr alpha .18 + RenderEffect blur if API>=31; overlay VISIBLE with ic_refresh; btnRestart.requestFocus()
```
When leaving NO_NET restore `qr_card_bg`. Animations are cheap property animators (`ViewPropertyAnimator`/`ObjectAnimator`); **no Lottie, no extra libs**; skip all animations if `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.
Low-RAM TV (918 MB): pulse + shimmer only run while the Activity is resumed; cancel in `onPause`.

---

## 3. Phone web remote (`web/`)

1. Add `<link rel="stylesheet" href="css/premium.css">` **after** `app.css` in `index.html` and ship the file. It is already tested against the real markup (screenshots above).
2. What it changes (all CSS, no markup changes): glass header + glow on logo, sculpted D-pad with glowing OK, one tactile button language (min 48px), gradient primary buttons, glowing active tab with a pill indicator, cards with hairline + top highlight, uppercase micro-labels, mono numbers, focus rings on inputs, skeleton shimmer class, toast redesign.
3. **Bugs fixed inside premium.css (verify they stay fixed):**
   - Sleep-timer active chip: "Off" was orange-on-orange (unreadable) → dark text on orange.
   - Screen tab: "Capture Screen" was a wide white bar with wrapped text; the 3 action buttons now share one row, one line each; capture button is orange primary.
4. **Toast (small JS change in `app.js`):** `showToast(msg, type)` → add `type` class (`ok` / `error`) to `#toast`, remove it on hide. Use `ok` for success ("Screen captured", "Alert sent"), `error` for failures. Position stays above the tab bar. Auto-hide 1.8 s (3 s for errors).
5. **Touch/feel rules:** every button gives visual feedback in ≤ 90 ms (already `.is-down`); haptics keep working (`haptics.js`); no 300 ms delays; hit areas ≥ 48×48 px; D-pad segments never smaller than 64 px.
6. **Connection chip** in header: `● Connected · 18 ms` in mono; green dot, turns amber when latency > 250 ms, red + "Reconnecting…" when offline; use the `.skeleton` class on card content while the first fetch is loading instead of empty space.
7. **Screen tab "TV card":** the existing TV Power House card there duplicates the TV home. Style it as the same composition as the TV screen (logo, pill, URL, QR) but **use the same `styled-qr.js` logic client-side** so the phone-shown QR matches the TV one (optional, nice-to-have).
8. Light theme exists in `tokens.css`; keep it working (premium.css has a light-safe header/tab bar). Dark is default.

---

## 4. App icon / banner
Keep the existing logo. Rounded-square mask is already in the logo art. Regenerate the Android TV banner (`res/drawable/banner.png`, 640×360, xhdpi) as: bg `#0B0C0E`, logo 140px left-centre, "TV Power House" 800-weight white 44px to its right, faint orange glow behind logo. Keep the safe margin 24px.

---

## 5. Fonts (do this, the bundled ones are suspicious)
In `web/assets/fonts/` the three Manrope files (600, 700, 800) are **byte-identical (same md5) and only 8 KB**, so they cannot be three real weights. Replace with real files: `Manrope-VariableFont_wght.woff2` (declare `font-weight: 200 800`) and `JetBrainsMono-Medium.woff2`. Keep `font-display: swap`, keep the system fallback stack from `tokens.css`. Also ship the same two fonts into `res/font/` for the TV app.

---

## 6. Accessibility & quality bar
- Contrast ≥ 4.5:1 for all text (the new status text on the dark pill = 14:1).
- TV: focus visible, only one focus target, no auto-hide timers on essentials.
- Phone: `aria-label` on icon-only buttons (power, settings), `prefers-reduced-motion` already honoured.
- No new network calls, no new permissions, APK size growth < 300 KB (fonts excluded).

## 7. Definition of done (check every box)
- [ ] TV screen at 720p **and** 1080p matches the 4 reference PNGs (compare side by side).
- [ ] QR is the styled version, scans from 5 m on 3 phones (§2.6).
- [ ] STARTING never shows a dim/grey QR; status reaches "Server active" by itself.
- [ ] Changing the TV IP updates URL, highlighted octet **and** QR in the same frame, with the refresh animation.
- [ ] Restart button focus ring visible on D-pad; OK key triggers restart.
- [ ] Phone remote with `premium.css` matches `phone_*_after` images; Sleep-timer "Off" readable; Screen-tab buttons on one row.
- [ ] Toast has ok/error variants.
- [ ] Real Manrope + JetBrains Mono fonts load (DevTools → Network shows different file sizes per weight).
- [ ] APK builds with `build_apk.ps1` unchanged; no new dependencies.
