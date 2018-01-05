# Audio Capture Service for Android

When installed as a system app, enables other applications to capture playing
media audio and metadata.

## Installation

This service must be installed as a privileged system app, in ``/system/priv-app``.

### If rooted

[Download](https://github.com/martoreto/audiocapture/releases) the APK and install it normally.
The service will move itself automatically there, using root capabilities.

### If not rooted, with TWRP

[Download](https://github.com/martoreto/audiocapture/releases) the APK, install it normally and move to ``/system/priv-app``.

Here's the video:

[![Video instructions](https://img.youtube.com/vi/K9l5HdOQ6LQ/0.jpg)](https://www.youtube.com/watch?v=K9l5HdOQ6LQ)

## Developing apps

To use this service in an app, you need to connect to the appropriate services,
as defined in [AndroidManifest.xml](src/main/AndroidManifest.xml). Their AIDL interfaces
are defined in [``github/audiocapture-lib``](https://github.com/martoreto/audiocapture-lib/tree/master/src/main/aidl/com/github/martoreto/audiocapture).
