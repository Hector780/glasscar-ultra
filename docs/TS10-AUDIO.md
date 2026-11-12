# TS10 native audio · 4.2 test

## Evidence and scope

This adapter is based on static inspection of the user's own extracted OEM applications. It is an independently written interoperability adapter; no OEM APKs, SDK binaries, decompiled classes or native libraries are distributed in this repository.

- `System UI.apk`: `com.android.systemui`, version 10/code 29; SHA-256 `08bc9bcdcaa1e9c724f19e1605582ef3e09cb986eac9f60447645dba9167258f`.
- `TW.apk`: `com.tw.service`, version 20210731/code 3; SHA-256 `f17c234eb13a016aee506e84fd6e2e0bc963f790eb9519c274bf3cccfd3810a2`.
- Observed device hardware `ums512_1h10`, API 29; OEM car settings code 551.
- User's wheel test changed the stereo volume while Android STREAM_MUSIC stayed 14/15. Android stream volume is therefore not used as native feedback.

The first test build gates the adapter to this hardware/API plus the observed TW version and car settings version. This is not a claim of universal TS10 compatibility. The supplied ZIP contains 23 APKs. Car Info Service, TWCore, XTService, Car Choose and Reverse are not in that archive. They are not required for this audio test; CAN and reverse integration remain separate work.

## Identified contract

| Operation | OEM evidence | Contract |
|---|---|---|
| Connect | `com.android.systemui.tw.TWSystemUI.init` | Construct framework `android.tw.john.TWUtil`, `open(short[])`, require return 0, then `start()` |
| Subscribe | `TWSystemUI.addHandler` and `StatusBarViewInit` | `addHandler(String, Handler)`; audio channel `0x203` (515) |
| Query current volume | `StatusBarViewInit.init` | `TWUtil.write(0x203, 0xff)` through the two-int overload |
| Set absolute volume | `QSPanelVolume$1.onProgressChanged` and `TWSystemUI.write(int,int,int)` | `TWUtil.write(0x203, 1, level, null)` using the four-argument overload |
| Receive | `StatusBarViewInit$1.handleMessage` → `QSPanelVolume$3.getData` | Message.what=515; arg1 low 31 bits=level, high bit=mute; arg2 low 31 bits=maximum |

The maximum is read from the device, never hardcoded to Android's 15. Invalid ranges (zero, over 255, level outside maximum) disable the native slider. No mute command is sent by this adapter.

## Implementation behavior

- Connect only while the launcher is resumed. Query the known volume channel every two seconds. Startup does not set volume, audio source, EQ, CAN, MCU configuration, or Android stream volume.
- Use framework reflection without hidden-API exemptions, root, injected events or elevated installation. Catch missing/inaccessible framework APIs and connection failures; show the existing native-panel button.
- Expose native slider only after valid feedback within five seconds. Display the native level/maximum and mute state. Wheel changes update it from feedback.
- Send one absolute-volume command on a completed tap or drag, not every drag frame. Cancelled drags do not send a native-volume command.
- A method return alone is not confirmation. Keep the last observed level and require matching feedback within three seconds. If it does not arrive, block further writes for that connection and offer the native panel. Reopening the launcher creates a fresh session.
- Closing a session clears state, stops polling and rejects queued callbacks from that session. No firmware flashing or system modifications.
- Diagnostics expose connection status, received level/maximum/mute, count of feedback messages, requested/confirmed level and write return. No serials, device identifiers or arbitrary vendor payloads.

## Validation and limits

Automated tests cover protocol decoding, bad ranges, native scale, readback confirmation, failed writes, permission/open failures, missing feedback, stale readings, non-finite input, unsupported devices, polling cleanup and late callbacks. They use a fake transport and do not validate the real MCU.

Hardware test still required: the OEM UI runs with system privileges. Static analysis cannot establish whether the device permits an ordinary app to use TWUtil. The provided apps call a framework class; they do not include its implementation or its service-side permission checks.

The test APK uses `com.glasscar.launcher.ts10test`, so it can coexist with both the existing launcher and the earlier `.review` test, without assuming their signing keys match. Configuration and permissions are separate for this test installation.

## Device test (parked, low volume)

1. Open GlassCar 4.2 Prueba. In settings, select `Control de volumen: Estéreo · integración TS10`.
2. Open Diagnóstico del estéreo. `TW nivel` should show a valid level/maximum. Move the wheel volume one step; the value should follow. If it remains unavailable, copy the report before attempting the slider.
3. Make a small change with GlassCar's bottom volume bar. Compare the original stereo volume indication and sound; `TW confirmado` should reflect the requested native level.
4. Copy the diagnostic report. Also report whether the wheel and GlassCar now adjust the same volume.

This build does not resolve Google Maps/Tlink embedding, DVR access or CAN instrumentation. Those require their own analysis and device validation.
