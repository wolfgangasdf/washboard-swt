```
this is the native image beryx version
gradle dist...
works BUT: can't load http !!!!!!!!!!!!!!
    setting NSAllowsArbitraryLoads in Info.plist has no effect
    most likely because the runtime image jlink .app runs a script which runs java...
    nearly nothing about that in the internet :-(
    but strange, java is run with bash.exec which replaces process...
    option make wmp https:
        swtbrowser works with https://self-signed.badssl.com (after accepting cert in safari)
        quite easy to make jetty https, can also generate keys easily.
        => THINK!!!
    option wait until more have this problem
    option package with graalvm, should work in .app with proper info.plist
        see below, doesn't work yet
    option package with jpackage (javapackager)
        https://badass-runtime-plugin.beryx.org/releases/latest/#_the_jpackage_script_block
        VERY unclear if this works, most likely same launcher script.
    option test in eclipse if it works... but no solution
    option remove startscript
        how? can't add command-line arguments for java in info.plist

```