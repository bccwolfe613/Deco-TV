# Deco TV

An Art Deco home screen for Google TV / Android TV (Android 8+).

- Greets you by time of day: **Good Morning** (navy, 5 AM–12 PM), **Good Afternoon** (emerald, 12–5 PM), **Good Evening** (black, 5 PM–5 AM).
- "Liquid glass" top bar and app tiles, drawn from a pre-blurred copy of the artwork so it stays smooth on TV hardware.
- Top bar: Cast, Mirror, Inputs (HDMI), Network and Settings, plus the clock.
- The app row shows every installed TV app, using each app's own banner.

## Getting it onto the TV (no computer needed)

Every push to `main` builds `DecoTV.apk` and attaches it to the newest release under **Releases**.

1. On the TV: Settings → System → About → press *Android TV OS build* 7 times. Then Settings → System → Developer options → turn on **Wireless debugging**.
2. On an Android phone on the same Wi-Fi: install **Bugjaeger** from the Play Store, then pair it with the TV using the pairing code shown under Wireless debugging.
3. On the phone, download `DecoTV.apk` from the latest release and install it to the TV with Bugjaeger's APK install option.
4. Make it the home screen. In Bugjaeger's shell, run:

   ```
   pm disable-user --user 0 com.google.android.apps.tv.launcherx
   ```

   Then press Home on the remote. If the TV asks which home app to use, pick **Deco TV → Always**.

To go back to the stock Google TV home screen at any time:

```
pm enable com.google.android.apps.tv.launcherx
```

Turn Wireless debugging off when you're done.

## Notes

- `keystore/deco-tv.jks` is a personal signing key so updates install over the old version. It's fine for your own TV; don't use it to publish anywhere.
- Artwork lives in `app/src/main/res/drawable-nodpi/`. For a 4K TV, replace the `bg_*.jpg` files with 3840×2160 versions.
