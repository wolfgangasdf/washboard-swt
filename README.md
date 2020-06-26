# Washboard
A mac dashboard replacement. I miss it.

Washboard uses the Eclipse SWT Browser to show widgets. This uses the installed default browser, see Eclipse SWT FAQ,
which seems to be the most resource-friedly solution at least on mac. 
It avoids packaging and loading a full browser for each widget (javafx, ...)  

# Widget types

## Local widges

## Web widges

## Apple Dashboard widgets
not yet...

# How to run TODO
[Download a zip](https://github.com/wolfgangasdf/WebRemoteControl/releases), extract it somewhere and run 
    `bin/webremotecontrol.bat` (Windows) or `bin/webremotecontrol` (Mac/Linux). It is not signed, google for "open unsigned mac/win".

## Notes
  * On startup, it writes a lockfile which contains a port on which is listened, open it and washboard is revealed.
If this lockfile is present while the app is launched, it reveals itself.
  * To implement hot corners activation:
    * mac: bettertouchtool has "Other" action "Move Mouse to Bottom Right Corner", then use "Execute Terminal Command", enter path to washboard executable.


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

# issue App Transport Security ATS
```
    https://bugs.eclipse.org/bugs/show_bug.cgi?id=564094

    setting NSAllowsArbitraryLoads in Info.plist has no effect
    most likely because the runtime image jlink .app runs a script which runs java...

    solution:
        val ats = org.eclipse.swt.internal.cocoa.NSDictionary.dictionaryWithObject(
                org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
                org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAllowsArbitraryLoads"))
        org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
                ats, org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAppTransportSecurity"))

```