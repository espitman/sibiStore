# Visual implementation review

The approved references remain `mac/design/reference.png`, `mobile/design/reference.png` and `tv/design/reference.png`. Review uses rendered application screenshots, not build success alone. This is a component/layout comparison, not a pixel-diff claim against generated mockups.

## Mac

Verified sidebar, English wordmark with checked-bag mark, black/charcoal/gold palette, top search/rescan/open-folder controls, library folder strip, APK table, selected gold row, version-history inspector, compatibility/file details and bottom transfer/status area. The inspector width was corrected to the reference proportions. A reproduced hover regression is now covered by the Electron test: hover cannot remove the selected row's gold gradient. The rebuilt packaged app also launches.

## Phone

Verified library header and connection pill, search, three filters, update notice, icon/name/version/action rows and bottom navigation. Details show the large app icon, update badge, primary action, version/size/Android panel, notes state and installed/available versions. The Updates view has app cards, real progress and percentage, Pause and Cancel controls, and the installation-confirmation hint. Cancel was tested during a real signed-client download and across process restart. The shared checked-bag mark matches the Mac and TV mark.

## TV

Verified landscape sidebar, three-column app grid, gold remote-focus outline, update notice, right-hand inspector, primary action and remote legend. Inspector alignment/spacing was corrected so the default notes and From your Mac footer are fully visible. D-pad OK from a focused card moves focus to its action, as confirmed by the accessibility hierarchy. Native lists and inspector scrolling retain content when libraries or metadata are longer.

## Intentional data differences

Production screens never invent app counts, transfer progress, release notes or installed versions to imitate the reference's example data. APK icons and names are extracted from the actual package; unsupported icons have a fallback. SDK/API and architecture details are factual metadata. Missing notes have an explicit empty state. The phone additionally exposes the version code used for comparisons. OS status bars, native installation dialogs and font rendering follow the device OS.

Screenshots used for local review are ignored artifacts under each platform's `test-results/`: `mac-library.png`, phone `final-library.png`, `final-details.png`, `updates-release-labels.png`, and TV `final-library.png`. These are local QA evidence, not production demo content.
