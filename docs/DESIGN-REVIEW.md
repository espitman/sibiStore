# Visual implementation review

The approved references remain `mac/design/reference.png`, `mobile/design/reference.png` and `tv/design/reference.png`. Review uses rendered application screenshots, not build success alone. This is a component/layout comparison, not a pixel-diff claim against generated mockups.

## Mac

Verified sidebar, English wordmark with checked-bag mark, black/charcoal/gold palette, top search/rescan/open-folder controls, library folder strip, APK table, selected gold row, version-history inspector, compatibility/file details and bottom transfer/status area. The inspector width was corrected to the reference proportions. A reproduced hover regression is now covered by the Electron test: hover cannot remove the selected row's gold gradient. The rebuilt packaged app also launches.

## Phone

Verified library header and connection pill, search, three filters, update notice, icon/name/version/action rows and bottom navigation. Details show the large app icon, update badge, primary action, version/size/Android panel, notes state and installed/available versions. The Updates view has app cards, real progress and percentage, Pause and Cancel controls, and the installation-confirmation hint. Cancel was tested during a real signed-client download and across process restart. The shared checked-bag mark matches the Mac and TV mark.

## TV

The earlier component-level review missed substantial reference differences: orange-biased accents, oversized/letter-spaced typography, heavy Material glyphs, boxed inactive navigation, a gray focus overlay, misplaced Search and widely separated remote hints. That review was not sufficient evidence of visual fidelity.

The correction uses TV-local neutral gray (`#B8B8B8`), yellow (`#FFD600`) and near-black gradient surfaces. It removes inherited letter spacing and focus ripple, uses thin vector UI glyphs and filled Library squares, and keeps real APK icons. At the 960 × 540 logical canvas, the sidebar is 164 dp including its divider, the inspector is 260 dp, and the grid/inspector gap is 39 dp. Search is placed at their junction; footer hints occupy three equal left-aligned columns. Heading size is 28 sp, navigation 13 sp, card names 15 sp and inspector title 19 sp. The focused card expands slightly without changing its black surface. Notes and the Mac footer remain visible with normal-length metadata.

`bash tv/scripts/visual-check.sh <QA-device-serial>` captures the real signed client, checks normalized reference landmarks, status-text clipping, inspector footer visibility and D-pad OK focus transfer. It never installs a catalog APK. Review `tv/test-results/reference-aligned-library.png` and `reference-aligned-action.png` visually too: accessibility bounds alone cannot verify color or glyph appearance. The live QA library has three apps, not the mockup's eight; no fake apps or release notes are injected to manufacture a pixel comparison.

## Intentional data differences

Production screens never invent app counts, transfer progress, release notes or installed versions to imitate the reference's example data. APK icons and names are extracted from the actual package; unsupported icons have a fallback. SDK/API and architecture details are factual metadata. Missing notes have an explicit empty state. The phone additionally exposes the version code used for comparisons. OS status bars, native installation dialogs and font rendering follow the device OS.

Screenshots used for local review are ignored artifacts under each platform's `test-results/`: `mac-library.png`, phone `final-library.png`, `final-details.png`, `updates-release-labels.png`, and TV `final-library.png`. These are local QA evidence, not production demo content.
