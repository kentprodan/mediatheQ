# 0031. The playback service handles audio focus itself

- Status: Accepted
- Date: 2026-11-06

## Context

Issue #139 adds Settings -> Playback -> Audio focus: a short interruption (a navigation prompt, a notification) ducks, pauses or does nothing, and a switch keeps playback paused after a long one (a call). With `handleAudioFocus = true` ExoPlayer always ducks a short interruption and always resumes after a call, so neither setting can work. Without `setWillPauseWhenDucked(true)` the system also ducks by itself and never tells the app.

## Decision

- ExoPlayer runs with `handleAudioFocus = false`. `AudioFocusController` (a `Player.Listener` on the session's player) asks for focus whenever the player is meant to play, from any controller, and gives it up on pause, end or stop.
- The reaction to a focus change is a pure function (`focusReaction`): a short interruption ducks (volume 0.2, Media3's own value), pauses or does nothing; bit-perfect has no volume to lower, so Duck pauses there. A long interruption pauses and resumes unless "Do not resume after a long interruption" is on. Losing the focus for good (another player) pauses without resuming.
- Refused focus does not play; delayed focus (a call is on) plays when the focus comes.
- A pause or play by the user during an interruption cancels the pending resume.
- "Becoming noisy" (headphones unplugged) stays with ExoPlayer (`setHandleAudioBecomingNoisy(true)`).

## Consequences

- Focus is our code to keep correct across Media3 bumps; Robolectric tests (`AudioFocusControllerTest`) cover every focus change and the resume rule.
