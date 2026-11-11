# 0040. TV onboarding is its own feature, Quick Connect first

- Status: Proposed
- Date: 2026-11-11

## Context

`:feature:onboarding` holds the phone's server, login and Quick Connect screens with three small ViewModels over
`SessionRepository` (`startQuickConnect`, polling, `AuthenticateWithQuickConnect`; Jellyfin 12 routes verified in the
OpenAPI 12.3.0 copy: `POST /QuickConnect/Initiate`, `GET /QuickConnect/Connect?secret=`,
`POST /Users/AuthenticateWithQuickConnect`). Its screens are Material 3 Expressive, which the TV app cannot use
(ADR 0037), and `:feature-tv:*` may not depend on `:feature:*` (architecture skill, ADR 0038). Typing a server address
and a password with a remote is slow; Quick Connect needs only a 6-digit code entered on the phone or in the web UI.

Options: (a) a TV feature with its own screens and ViewModels against the same repository; (b) move the three
ViewModels into a shared `:feature:onboarding-logic` module both apps depend on; (c) push the Quick Connect state
machine into `:core:data`.

## Decision

- **(a):** `:feature-tv:onboarding` with `TvServerScreen`, `TvQuickConnectScreen`, `TvLoginScreen` and their own
  ViewModels and UiStates, written against `SessionRepository` and `AccountRepository` exactly as the phone's are.
  The ViewModels are small (the Quick Connect one is about 60 lines); sharing them would cost a module edge and a
  shared UiState shape for two different screens.
- **Flow on TV:** server address (with the last used address and `GET /System/Info/Public` validation as on the phone)
  -> Quick Connect screen showing the code in large type and polling until approved -> the library sync starts.
  Username + password is a secondary button on the Quick Connect screen for servers with Quick Connect disabled
  (`GET /QuickConnect/Enabled`).
- The same `DeviceId` rules apply (one per install, R9); the TV install is its own Jellyfin device. The `Client` name
  stays `mediatheQ`; the device model (`Build.MODEL`) tells the box apart.
- Sign-out and the 401 route-to-login behave as on the phone (ADR 0036): the TV app signs in to one account in TV 1;
  the account switcher UI is not part of this milestone.

## Consequences

- Three ViewModels exist twice (phone, TV) over one repository; a change in the Quick Connect protocol is made once in
  `:core:data` / `:provider:jellyfin` and both apps follow.
- Contract tests do not change (the provider is untouched); the TV ViewModels get their own unit tests and the screens
  their own screenshot tests.
