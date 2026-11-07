# 0032. Audio settings per output device

- Status: Accepted
- Date: 2026-11-06

## Context

Issue #140: one global equalizer, normalization and bit-perfect setting does not fit an app that plays on the phone speaker, Bluetooth headphones, a car and a USB DAC. Each output needs its own settings, applied when it becomes the one media plays on.

## Decision

- **Profiles:** every output has an `OutputProfile` (normalization, Bypass processing, equalizer on / preset / custom gains, bit-perfect USB). The phone is always there and cannot be forgotten. An output seen for the first time is remembered with a copy of the phone's profile.
- **Identity (`OutputId` in `:core:model`):** `phone`, `bluetooth:<hash>`, `usb:<hash>`. Bluetooth hashes the address the platform gives (it can be anonymized without `BLUETOOTH_CONNECT`) together with the product name; the raw address is never stored. USB hashes the product name only: `AudioDeviceInfo` has no vendor / product id and its address (`card=1;device=0`) changes with the port, so two identical DACs share one profile. Everything else (speaker, wired) is the phone.
- **Storage:** the audio DataStore (`AudioOutputStore`), a set of ids plus `out.<id>.<field>` keys and the last current output, like the other audio settings (no Room table, survives sign-out). A one-time DataStore migration moves the global `normalization`, `equalizer_on`, `eq_preset`, `eq_bands` and `bit_perfect_usb` keys into the phone's profile; new DACs then copy bit-perfect from the phone. The phone's page does not show the bit-perfect switch.
- **Detection:** `AudioOutputPort` (was `UsbAudioPort`) also reports the device media plays on (`getAudioDevicesForAttributes(MEDIA)`, Android 14+; the phone below it). `AudioOutputController` asks on every decision (track change, state change, device change), so a Bluetooth route that reports late is caught at the next one. `OutputProfileFollower` remembers the device and hands its profile's settings to the controller.
- **Bypass processing:** no equalizer and no normalization (`AudioPolicy.normalization`), so the output may offload; bit-perfect rules are unchanged.
- The Audio page is gone: Settings -> Playback -> Output settings lists the outputs; each output's page has Settings and Equalizer tabs; Now Playing's Equalizer button opens the current output on its Equalizer tab.

## Consequences

- Settings take effect per device without the user switching anything; a device not seen before plays like the phone.
- Two identical USB DACs, or two Bluetooth devices with the same anonymized address tail and name, share a profile.
- Below Android 14 every output is the phone (a `shortcut:` in `AudioOutputPort`).
