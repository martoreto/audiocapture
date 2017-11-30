# Audio Capture Service for Android

When installed as a system app, enables other applications to capture playing
media audio and metadata.

## Installation

This service must be installed as a privileged system app, in ``/system/priv-app``.
This requires root or a custom recovery like TWRP.

[Download](https://github.com/martoreto/audiocapture/releases) the APK, install it normally and move to ``/system/priv-app``
([this app](https://play.google.com/store/apps/details?id=de.j4velin.systemappmover) 
may help).

## Developing apps

To use this service in an app, you need to connect to the appropriate services,
as defined in [AndroidManifest.xml](src/main/AndroidManifest.xml). Their AIDL interfaces
are defined in [``github/audiocapture-lib``](https://github.com/martoreto/audiocapture-lib/tree/master/src/main/aidl/com/github/martoreto/audiocapture).
