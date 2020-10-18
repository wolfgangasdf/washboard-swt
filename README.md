# Washboard
A apple mac dashboard replacement. I miss it. 

Activate it by a global hotkey, see below for other options.

If a widget doesn't load properly: try setting `enable clicks`, washboard uses a hack to prevent clicks which might interfere.


# Widget types

## Local widges
Local widgets contain at least an index.html file, possibly others (js, images,...). 

## Web widges
Shows some website. Cookies are stored.

## Apple Dashboard widgets
I thought about implementing this, however, since even Sol.wdgt uses some compiled 
helper tools, this doesn't make sense. It's easier to rewrite nice widgets using only html etc. 

# How to run TODO
[Download a zip](https://github.com/wolfgangasdf/WebRemoteControl/releases), extract it somewhere and run 
    `bin/webremotecontrol.bat` (Windows) or `bin/webremotecontrol` (Mac/Linux). It is not signed, google for "open unsigned mac/win".

## Notes
### Lockfile and revealing 
On startup, it writes a lockfile which contains the port number on which washboard listens for connection attempts. Open a connection and washboard is revealed.
If this lockfile is present while the app is launched, it reveals the running instance.

Hot corners activation:
  * mac: bettertouchtool has "Other" action "Move Mouse to Bottom Right Corner", then use "Execute Terminal Command", enter this:<br>
``` nc -z localhost `cat "/Users/<USER>/Library/Application Support/Washboard/lockfile.lock"` ```

### Browser
Washboard uses the Eclipse SWT Browser to show widgets. This uses the installed default browser, see Eclipse SWT FAQ,
which seems to be the most resource-friedly solution at least on mac. 
It avoids packaging and loading a full browser for each widget (javafx, ...)  

### Security
The browser implementation has some access to the file system, use only widgets that you trust. 
I think this is still much safer than the old dashboard which could execute arbitrary binaries. 



# How to develop, compile & package

* Get Java 14 from https://jdk.java.net
* Clone the repository
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), just open the project to get started.
* Compile and run manually: `./gradlew run`.
* Package jar: `./gradlew clean dist`. The resulting zips are in `build/crosspackage`.

# Used technologies

* [Kotlin](https://kotlinlang.org/) and [Gradle](https://gradle.org/)
* [jkeymaster](https://github.com/tulskiy/jkeymaster) for global keyboard shortcut
* [Runtime plugin](https://github.com/beryx/badass-runtime-plugin) to make runtimes with JRE

# Issue about App Transport Security ATS
Mac OS has "App Transport Security" that prevents opening http pages, and this happens with the swt browser even on the local network if server names are used (ip works). During development there is no Info.plist where this can be disabled in principle, but also this didn't work here, possibly an issue with java launcher scripts. In any case, one might want to disable ATS dynamically, this works but it was a bit hard to figure out, possibly it's useful for SWT FAQ or wiki? Note that this issue might only appear if building standalong maven/gradle projects, I guess that eclipse disables ATS at another place, otherwise many more would report this.

https://bugs.eclipse.org/bugs/show_bug.cgi?id=564094

Solution:
```
val ats = org.eclipse.swt.internal.cocoa.NSDictionary.dictionaryWithObject(
        org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
        org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAllowsArbitraryLoads"))
org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
        ats, org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAppTransportSecurity"))
```