# Releasing Still Time

Two apps ship together from this repo: the phone app (`:app`) and the Wear OS app (`:wear`). They share the
application id `org.comichao.pomodoro` and the same release key. Releases go to GitHub (APKs) and Google Play
(app bundles). Read this before touching a version number: Play never forgets a version code.

The Kotlin package is still `dev.pomo` (source folders, `namespace`, `ACTIVITY` in the Makefile). Only the
installed application id changed, in 0.3.0, for Google Play. GitHub releases up to v0.2.1 used `dev.pomo`, so
those installs are a different app on the device and are not updated by 0.3.0 or later.

## Version name

`versionName` is `MAJOR.MINOR.PATCH` and is the same for the phone and the watch. It lives in
`app/build.gradle.kts`; `wear/build.gradle.kts` reads it from there. Change it with:

```bash
make bump PART=patch          # or minor, major, or an exact X.Y.Z
```

## Version code

A **plain counter**. `make bump` adds 1 to the phone's `versionCode` in `app/build.gradle.kts`, and that is all you
do. Do not encode dates in it.

| Build | Version code | Example |
|---|---|---|
| Phone (and the watch APK on GitHub) | the counter itself | `4` |
| Watch bundle uploaded to Play | the counter plus 1000 | `1004` |

The watch offset is automatic: `wear/build.gradle.kts` adds 1000 when the bundle is built with `-PplayBundle`.
Play counts version codes across the whole app, so the phone and the watch bundle cannot share one.

Rules:

1. **A code can be used once, ever.** Play keeps every uploaded code, even if you discard the release or the bundle.
   A rebuilt bundle needs a new code.
2. **Codes only go up.** A device is only offered an update whose code is higher than the installed one.
3. **Keep the jump small.** Play flags a code far above the previous one. Counting up by 1 never trips it.
4. Codes `1`-`3` were used by the GitHub releases (`dev.pomo`). Play starts at `4` (watch `1004`).

### Codes already used in Play (never reuse)

`4` and `1004` (0.3.0, uploaded before the rename to Still Time, then replaced), `5` and `1005` (0.3.1).
Next free phone code: `6` (watch `1006`). Add each code after it is uploaded.

## Releasing

Start with a clean, committed and pushed tree: `make publish` refuses to run otherwise.

1. `make bump PART=patch` (or minor, major).
2. Commit and push.
3. GitHub: run `make publish`. It builds both signed APKs, checks name, code and signing key, and creates the
   `v<versionName>` release with `SHA256SUMS`. The signing variables come from `env.mk`.
4. Google Play: build the bundles through `make`, so the signing variables from `env.mk` are loaded:

   ```bash
   make --eval='bundle: ; $(GRADLE) :app:bundleRelease :wear:bundleRelease -PplayBundle -q' bundle
   ```

   The files are `app/build/outputs/bundle/release/pomodoro-<version>-release.aab` and
   `wear/build/outputs/bundle/release/pomodoro-wear-<version>-release.aab`. Copy them to
   `releases/play-upload-<version>/` (hidden from git) before uploading.
5. In Play Console, upload the phone bundle to the phone track and the watch bundle to the Wear OS track
   (switch the form factor at the top of Test and release). Add release notes, start the rollout, and submit from
   Publishing overview.

Play Console notes (learned on netResult):

- **Wear OS must be opted in**, once per app: Test and release > Advanced settings > Form factors > Wear OS must
  show every step done, including "Opt-in to Wear OS and agree to the review policy". Until then watch releases
  stay "Superseded by another release · Not published" and the Wear OS track stays Inactive.
- A bundle Play has already seen cannot be uploaded again; attach it with **Add from library** instead.
- A release can only be discarded while it is the latest on its track.
- Any old build left active on any track counts for Play's target API check. Targets must stay at the current
  minimum (API 36 in 2026).
- New personal accounts need a closed test with 12 opted-in testers for 14 days before production access.

## Don'ts

- Don't reuse a code or guess one: check the list above and Play's bundle explorer.
- Don't pick a version name, code scheme or upload target without agreeing it first.
- Don't put dates in the code or add big offsets.
