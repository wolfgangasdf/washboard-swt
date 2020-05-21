```aidl


palantir-graal doesnt work yet with graal 20
do adapted hack: https://github.com/palantir/gradle-graal/issues/239#issuecomment-578493879		
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java11-20.1.0/Contents/Home
    export PATH="$JAVA_HOME/bin:${PATH}"
    gu install native-image
    mkdir -p ~/.gradle/caches/com.palantir.graal/20.1.0/graalvm-ce-20.1.0/bin
    touch ~/.gradle/caches/com.palantir.graal/20.1.0/graalvm-ce-20.1.0-amd64.tar.gz
    ln -s /Library/Java/JavaVirtualMachines/graalvm-ce-java11-20.1.0/Contents ~/.gradle/caches/com.palantir.graal/20.1.0/graalvm-ce-20.1.0/Contents
    gradle clean nativeImage
    # resulting bin in build/graal

    # -> Warning: System method java.lang.System.loadLibrary invoked at org.eclipse.swt.internal.Library.load(Library.java:232)
    #need "--no-fallback" in options...
    gradle clean nativeImage
        Exception in thread "main" java.lang.NoSuchFieldError: org.eclipse.swt.internal.cocoa.NSOperatingSystemVersion.patchVersion
            at com.oracle.svm.jni.functions.JNIFunctions$Support.getFieldID(JNIFunctions.java:1107)
=> graalvm & swt: need JNI config files, see 
    https://github.com/oracle/graal/issues/2232
    https://github.com/mbarbeaux/graalvm-swt-native-image
    => wait until bug on mac resolved!
    see graalvm-swt-native-image-master which is modified for graal-java11

Test download source and compiled jar from https://repo1.maven.org/maven2/org/eclipse/platform/org.eclipse.swt.cocoa.macosx.x86_64/3.114.0/
    (the only two big files)
    unpack with unarchiver in firefox
    see build.gradle.kts!
    => doesn't work. can't find shared libs?



```