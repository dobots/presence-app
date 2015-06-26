# Presence App

##What it does
Using BLE technology, this app brings to you basic indoor localization!

It is meant to work with dobeacons/crownstones ( https://dobots.nl/products/crownstone/ ) and ask's standby interface( https://standby.ask-cs.nl/ ).

Currently, StandBy let you inform everyone of your position and availability via wifi. Presence improves the localization via BLE, and automatically inform others of your position for you!

Presence runs silently in the background of your phone. All you have to do is to choose the BLE devices you wish to use for localization, and fill your standby credentials. Afterwards, you can simply close the app, and forget about it! If you ever wish to change the settings or stop the localization, start Presence again.

The app updates your position every 10 sec. The battery consumption has been optimized. On a OnePlus one, it consummed less than 4% of battery over 24 hours.

##APK
You can find the apk here: http://christian.haas-frangi.perso.centrale-marseille.fr/visible/presence.apk

##Installation
###Third Party libraries used
Altbeacon: http://altbeacon.org/
Okhttp: http://square.github.io/okhttp/
retrofit: http://square.github.io/retrofit/
fasterxml jackson datatype: https://github.com/FasterXML/jackson-datatype-joda

###Using Android Studio and Gradle
download https://altbeacon.github.io/android-beacon-library/download.html

add depedency to your project: https://altbeacon.github.io/android-beacon-library/configure.html

/!\ use okhttp 2.3.0 because 2.4.0 won't work ! /!\

###Using Eclipse
remove gradle related files
follow https://altbeacon.github.io/android-beacon-library/configure.html
