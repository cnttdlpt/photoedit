# UX release 2026-10-21

Web deployed to http://100.113.89.21:3000/ and verified in browser and by HTTP 200 for index, app and studio stylesheet.
Server backup: /home/nguyennam/iro-backups/ux-20261021-VwLtqa/frontend-before.tgz
Release inputs: /home/nguyennam/iro-ux-20261021/release

Validation: 30 web tests pass, including A → B → A source-based rendering regression; desktop/mobile editor review disposition: ship. Desktop and mobile captures live beside this file but are not committed.
Android: assembleDebug succeeded; launched Pixel 9 emulator without AndroidRuntime crash. Native tablet and large-font visual QA remain outstanding.
iOS: source updated; Xcode build and simulator/device verification unavailable on this Windows host.

Web changes include import-to-editor navigation, cancellable pending image load, full inline searchable recipe catalog, neutral responsive studio layout, Morphicons controls, 960px preview plus four-result cache and full resolution export.
The GitHub branch contains web, iOS and Android source changes together. It has not been merged into main or the platform branches.
