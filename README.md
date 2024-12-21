# Washboard
A apple mac dashboard replacement. I miss it. It shows local or remote websites or web apps in floating windows, remembers position and size.

If a widget doesn't load properly: try setting `enable clicks`, washboard uses a hack to prevent clicks which might interfere.


# Widget types
See the wiki for examples.


## Local widges
Local widgets contain at least an index.html file, possibly others (js, css, images,...). 


## Web widges
Shows some website, saves cookies. Ideas:
  * weather pages
  * astronomic picture of the day
  * google tasks (authenticate once)

  
## Apple Dashboard widgets
I thought about implementing this, however, since even Sol.wdgt uses some compiled 
helper tools, this doesn't make sense. It's easy to rewrite widgets using only html, js & co. 


# How to run
[Download a zip](https://github.com/wolfgangasdf/washboard-swt/releases), extract it somewhere and run the app (mac). It is not signed, google for "open unsigned mac" (right click -> open on mac).


## Notes
### Fast activation
On startup, washboard writes a lockfile (in settings folder) which contains the port number on which washboard 
listens for connection attempts. Open a connection to this port and washboard is revealed (super fast). 
If the app is running and is launched again, the running instance is revealed (not very fast).

Activate the washboard by 
  * [calling the app via a keyboard shortcut](https://www.wikihow.com/Set-a-Keyboard-Shortcut-to-Open-Mac-Apps)
  * or hot corners activation with bettertouchtool: go to `automation & named & other triggers` -> trigger `move mouse to bottom right corner` 
    , then set the action `Execute Terminal Command`, and enter this:<br>
``` nc -z localhost `cat "/Users/$USER/Library/Application Support/Washboard/lockfile.lock"` ```


### Browser
Washboard uses the Eclipse SWT Browser to show widgets, which in turn uses the installed default browser, see Eclipse SWT FAQ. 
This seems to be the most resource-friedly and secure solution.


### Security
The browser implementation has some access to the file system, use only widgets that you trust.  


### Why not a full-screen app?
Fullscreen SWT windows can't have subwindows, therefore one would need to write a little window manager, I was too lazy. Also, I find the fullscreen animation on mac too slow :-)


# How to develop, compile & package

* Get Java from https://jdk.java.net
* Clone the repository
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), just open the project to get started.
* Compile and run manually: `./gradlew run`.
* Package jar: `./gradlew clean dist`. The resulting zips are in `build/crosspackage`.


# Used libraries

* [Kotlin](https://kotlinlang.org/) and [Gradle](https://gradle.org/)
* [Runtime plugin](https://github.com/beryx/badass-runtime-plugin) to make runtimes with JRE


# How "App Transport Security (ATS)" (mac) is circumvented
Mac OS has "App Transport Security" that prevents opening http pages (which I want in washboard), and this 
happens with the swt browser widget even on the local network if server names are used (ip works). 
During development there is no Info.plist where this can be disabled in principle, but also this didn't work here, 
possibly an issue with the java launcher scripts. In any case, one might want to disable ATS dynamically, 
the code below works but it was a bit hard to figure out. Note that this issue might only appear if 
building standalong maven/gradle projects, I guess that eclipse disables ATS at another place, 
otherwise many more would have issues with this.

https://bugs.eclipse.org/bugs/show_bug.cgi?id=564094

Solution used here:
```
val ats = org.eclipse.swt.internal.cocoa.NSDictionary.dictionaryWithObject(
        org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
        org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAllowsArbitraryLoads"))
org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
        ats, org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAppTransportSecurity"))
```