# Still Time privacy policy

_Last updated: 2026-11-01_

Still Time is a free, open-source focus timer for Android and Wear OS (package `org.comichao.pomodoro`). This
policy explains what the app does with your information. The short version: **it stays on your devices, and we
never receive it.**

## Who is responsible

Still Time is published by Mauro Bardelloni. Contact: comichao.dev@gmail.com, or
[open an issue](https://github.com/ESeNonFossiIo/app-pomodoro/issues).

## What the app stores

- **Your sessions:** start time, length, tag and whether you finished or gave up.
- **Your settings:** focus and break lengths, tags, theme, fullscreen mode, and the apps you allow to interrupt
  a focus session.
- **The current timer state,** so the phone, the alarm and the watch agree on it.

All of this is stored privately on your phone. The app has no account, no sign-in, no advertising and no
analytics, and it does not use the internet.

## Notification access

To hold notifications back during a focus session, Still Time asks for Android's notification access. While a
session runs, it snoozes each new notification until the session ends. To decide whether a notification may
come through, it looks only at which app sent it, its category (calls, alarms and navigation always get
through) and whether it is an ongoing notification. **It does not read, store or send the title or text of any
notification.** Outside a focus session it does nothing. You can withdraw the access at any time in Android's
settings.

## The list of your apps

To let you choose which apps may interrupt a focus session, Still Time lists the apps on your phone that have a
launcher icon, and finds your phone and messaging apps so they are allowed by default. This list is built on
your phone, is used only for that setting and is never sent anywhere.

## What leaves your devices

Nothing is sent to us or to any server. Two transfers exist, both on your side:

1. **Phone and watch:** if you use the Wear OS app, the timer state and tag travel between your own phone and
   watch through Wear OS's built-in data link (part of Google Play services). Messages go to the other copy of
   Still Time only.
2. **CSV export:** if you choose Export CSV, the app writes your session log to a file in the place you pick
   with Android's file picker. What happens to that file afterwards is up to you.

## Permissions

- **Notification access** (phone): to snooze notifications during a focus session, as described above.
- **Post notifications** (phone): to show the app's own notifications, such as the end-of-session alert.
- **Wake lock** (watch): to keep the watch screen on while the app is open.

The app never asks for access to your files, contacts, location, camera or microphone.

## Your control

- **Export** your sessions to CSV from Menu > Stats.
- **Delete** a single session from the session log.
- **Uninstalling** the app deletes all its data.
- **Withdraw** notification access at any time in Android's settings.

## Children

Still Time is not directed at children and does not knowingly collect any information from anyone.

## Changes

If this policy changes, the new version will be published at this address with a new date.
