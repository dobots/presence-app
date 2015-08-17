<!-- 
[![Build Status](https://travis-ci.org/chaasfr/presence-app.svg)](https://travis-ci.org/chaasfr/presence-app)
-->

# Presence App aka Who's In

##What it does
Using BLE technology, this app brings to you basic indoor localization!

It is meant to work with [dobeacons](https://dobots.nl/products/crownstone/) and [ask's standby interface](https://standby.ask-cs.nl/).

Currently, StandBy lets you inform everyone of your position and availability via wifi. This app improves the localization using BLE devices, and automatically inform others of your position for you!

The app runs silently in the background of your phone. All you have to do is to choose the BLE devices you wish to use for localization, and fill in your standby credentials. Afterwards, you can simply close the app, and forget about it! If you ever wish to change the settings or stop the localization, start Presence again.

The app updates your position every 30 sec.

##APK
You can find the apk on [Google Play Store](https://play.google.com/store/apps/details?id=nl.dobots.presence)

##Installation

### Libraries used

This project uses the bluenet-lib-android project found [here](https://github.com/dobots/bluenet-lib-android). Until the library is added to the maven repository you have to install it manually following these steps:

1. Clone the repository [bluenet-lib-android](https://github.com/dobots/bluenet-lib-android) to your disk

2. cd to the root folder of this project

3. create a symlink to the bluenet-lib-android folder using:

    `ln -s /path/to/the/bluenet-lib-android bluenet-lib`

4. Refresh the presence-app in the android studio: `File > Synchronize`

