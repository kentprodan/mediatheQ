# Google TV design guidelines: what mediatheQ follows

Notes from developer.android.com/design/ui/tv (read 2026-11-17, at the owner's request). The rules every TV screen
follows; numbers are dp at the 960 x 540 dp design canvas (1080p assets).

## Layout
- Canvas 960 x 540 dp, 16:9. Overscan-safe margins: 48 dp left and right (58 dp in the grid spec), 27-28 dp top and
  bottom. Backgrounds go edge to edge; important elements stay inside the margins.
- Grid: 12 columns of 52 dp with 20 dp gutters.
- Card widths by how many show at once, 20 dp apart, with a peek of the next one: 5 cards 124 dp, 4 cards 196 dp,
  3 cards 268 dp, 2 cards 412 dp, 1 card 844 dp. Leave room for the focused card's scale so neighbors never overlap.
- Browse template: rows stacked vertically, items browsed horizontally; each D-pad axis has one purpose.
- Few panels; the focus path follows the reading path.

## Navigation
- Back always goes to the previous destination and finally to the launcher; with a left menu, Back from the content
  opens the menu with focus on the active entry. No exit gating, no on-screen back button.
- Fixed start destination; a deep link behaves like manual navigation (Back reaches Home).
- Every focusable element reachable on a straight path.

## Navigation drawer
- Both states visible: collapsed = a rail of icons, expanded = icons and labels.
- Standard drawer pushes the content; modal drawer overlays it with a scrim (gradient or solid) and changes the page
  only on Select.
- Anatomy: top section (logo, profile switch or search), 3 to 7 destinations (5-6 recommended), bottom section with
  1-3 actions (settings, help, profile). Icons on every entry, short labels (ellipsis if needed), a clear active
  indicator, dividers and badges sparingly.

## Focus
- One focused element at a time; focus groups for logical navigation.
- Indicators: scale (1.025, 1.05 or 1.1 by element size), border / outline, glow (2-32 dp elevation), color change.
  Buttons and focused cards in an immersive list scale 1.1.

## Cards
- Variants: standard, classic, compact (text over the image with a scrim), wide standard, wide classic.
- Ratios: 16:9 (videos, most common), 2:3 (posters, emphasis), 1:1 (cast, logos).
- The text block is as wide as the image; long text only on wide cards.

## Immersive list / featured carousel
- Immersive list: a row of cards with a large preview of the focused item behind (16:9 background aligned top right,
  cinematic scrim), revealed progressively. For featured content.
- Featured carousel: overline, title, description, button over an image with a scrim; pagination; at the top of Home.

## Tabs, buttons, lists
- Pill tabs organize whole pages; bar tabs separate content inside a page. Content slides with the tab.
- Buttons: filled for the primary action, outlined for secondary; text and icon buttons fully rounded, wide and image
  buttons 12 dp corners; label large, sentence case, one line; the first button of a group is the primary action.
- Lists: one, two or three lines; items have no container by default; selection with a checkbox, radio or switch,
  never by color alone; no buttons inside a list item.

## Type and color
- Roboto by default; display only for the main heading of a screen, headline for featured / immersive headings, title
  for cards and lists, body for longer text, label for components.
- Dark theme for a cinematic feel; avoid white backgrounds; high contrast; never color alone for meaning; design for
  the Standard picture mode and sRGB.
- Gradients band on TVs: subtle, gradual transitions (or solid colors).

## App icon and banner
- `android:icon` and `android:banner` are both required; adaptive icons recommended. Banner 16:9 (320 x 180 px at
  xhdpi) with the logo and name inside the safe area; launcher icon 1:1 (160 px at xhdpi), 72 dp safe zone; no themed
  icons on TV.
