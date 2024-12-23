import mu.KLogger
import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Device
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.internal.cocoa.*
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.schedule
import kotlin.concurrent.thread
import kotlin.system.exitProcess


private lateinit var logger: KLogger

object WashboardApp {
    lateinit var display: Display
    private lateinit var mainShell: Shell
    private var focusTimer: Timer? = null
    private var appShown = true
    private var serverSocket: ServerSocket? = null
    private var revealThread: Thread? = null
    var lastActiveWidget: Widget? = null
    private var logtext: Text? = null
    private val logstring = AtomicReference("")
    private var logTimer: TimerTask? = null

    private fun beforeQuit() {
        serverSocket!!.close()
        focusTimer?.cancel()
        Settings.saveSettings()
        serverSocket?.close()
        AppSettings.releaseLock()
    }

    fun startFocusTimer() {
        stopFocusTimer()
        focusTimer = fixedRateTimer("focus timer", initialDelay = 500, period = 200) {
            display.syncExec {
                if (display.focusControl == null) hideApp()
            }
        }
    }

    fun stopFocusTimer() {
        focusTimer?.cancel()
    }

    private fun showApp() {
        NSApplication.sharedApplication().activateIgnoringOtherApps(true)
        NSApplication.sharedApplication().arrangeInFront(null)
        Timer().schedule(100) { // ugly workaround that not all windows are always on top
            display.syncExec { NSApplication.sharedApplication().activateIgnoringOtherApps(true) }
        }
        val now = System.currentTimeMillis()
        Settings.widgets.forEach { w ->
            if (now - w.lastupdatems > w.updateIntervalMins * 60 * 1000) {
                logger.info("reloading widget $w")
                w.bs!!.loadWebviewContent()
            }
        }
        startFocusTimer()
        appShown = true
    }

    private fun hideApp() {
        stopFocusTimer()
        NSApplication.sharedApplication().hide(null)
        appShown = false
    }

    private fun quitApp() {
        Global.dolog = {}
        beforeQuit()
        exitProcess(0)
    }

    fun showWidget(w: Widget, addToSettings: Boolean) {
        w.bs = BrowserShell(w)
        w.bs!!.loadWebviewContent()
        if (addToSettings) Settings.widgets.add(w)
    }

    private fun showAllWidgets() {
        Settings.widgets.forEach { showWidget(it, false) }
    }

    fun launchApp() {
        Device.DEBUG = true
        Display.setAppName("washboard-swt")
        display = Display.getDefault()
        Display.setAppName("washboard-swt") // need twice https://stackoverflow.com/a/45088431

        // hide dock icon related to https://stackoverflow.com/questions/2832961/is-it-possible-to-hide-the-dock-icon-programmatically?rq=1
        NSApplication.sharedApplication().setActivationPolicy(1) // NSApplicationActivationPolicyAccessory
        NSApplication.sharedApplication().activateIgnoringOtherApps(true)
//        System.setProperty("LSUIElement", "true") // doesn't work, also not in pinfo/launch cmd
//        System.setProperty("apple.awt.UIElement", "true")
//        System.setProperty("apple.awt.headless", "true")
//        System.setProperty("java.awt.headless", "true")

        mainShell = Shell(display).apply {
            text = "WashboardSwt"
            layout = GridLayout(1, false)
            setSize(250, 200)
        }

        logtext = Text(mainShell, SWT.MULTI or SWT.H_SCROLL or SWT.V_SCROLL).apply {
            layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
        }

        display.addFilter(SWT.KeyDown) {
            logger.debug("disp keydown: ${it.keyCode} $it")
            if (it.character == SWT.ESC) {
                hideApp()
            }
        }
        val tray = display.systemTray
        assert (tray != null) { "can't get tray!!" }
        val trayItem = TrayItem(tray, SWT.NONE)
        trayItem.toolTipText = "Washboard"
        trayItem.image = Image(display, javaClass.getResource("icon16x16.png")!!.openStream())
        val menu = Menu(mainShell, SWT.PUSH)
        wMenuItem(menu, "About washboard") { Helpers.openURL("https://quphotonics.org") }
        wMenuItem(menu, "Show") { showApp() }
        wMenuItem(menu, "Hide") { hideApp() }
        wMenuItem(menu, "Settings") { ShellSettings() }
        wMenuItem(menu, "Settings folder") { Helpers.revealFile(AppSettings.getSettingFile()) }
        wMenuItem(menu, "Quit") { quitApp() }
        MenuItem(menu, SWT.SEPARATOR)
        wMenuItem(menu, "Refresh widget") {
            Settings.widgets.find { it == lastActiveWidget }?.also { it.bs!!.loadWebviewContent() }
        }
        wMenuItem(menu, "Edit widget") {
            Settings.widgets.find { it == lastActiveWidget }?.also { ShellEditWidget(it, false) }
        }
        wMenuItem(menu, "Add web widget") { ShellEditWidget(Widget(WidgetType.WEB), true) }
        wMenuItem(menu, "Add local widget") { ShellEditWidget(Widget(WidgetType.LOCAL), true) }
        wMenuItem(menu, "Widget History") { ShellHistory() }
        MenuItem(menu, SWT.SEPARATOR)
        trayItem.addListener(SWT.MenuDetect) {
            if (!appShown) showApp()
            display.asyncExec { menu.isVisible = true }
        }

        mainShell.open()

        // socket listener to show washboard via e.g. bettertouchtool action mouse hot corner
        serverSocket = ServerSocket(0)
        logger.info("listening on port " + serverSocket?.localPort)
        AppSettings.lockFile.writeText(serverSocket!!.localPort.toString())
        revealThread = thread { // reveal thread
            while (!serverSocket!!.isClosed) {
                try {
                    serverSocket!!.accept()
                    logger.info("revealthread: Connection to socket, reveal!")
                    display.syncExec { showApp() }
                    // do again because sometimes not all windows in foreground... ugly hack
                    display.syncExec { showApp() }
                } catch (e: SocketException) {
                    logger.debug("revealthread: serversocket got exception: $e")
                }
            }
        }

        @Suppress("ObjectLiteralToLambda") // lambda doesn't work here
        display.addListener(SWT.Close, object: Listener { override fun handleEvent(event: Event) {
            logger.debug("display close!!!")
            quitApp()
        }})

        showAllWidgets()
        showApp() // call here, starts focus timer

        // logging: written by character, shown every 200ms
        fun startLogTimer() {
            logTimer?.cancel()
            logTimer = Timer("log timer", false).schedule(200) {
                display.asyncExec {
                    logtext?.append(logstring.getAndSet(""))
                }
            }
        }
        Global.dolog = {
            if (logstring.get().isEmpty()) startLogTimer()
            logstring.getAndUpdate { olds -> olds + it }
        }

        // run gui
        while (!mainShell.isDisposed) {
            if (!display.readAndDispatch()) display.sleep()
        }

        quitApp()
    }

}

object Global {
    var dolog: (String) -> Unit = { }
    val toolWindowsActive = AtomicInteger(0) // to disable focus tracking if tool windows are active
}

fun main() {

    val oldOut: PrintStream = System.out
    val oldErr: PrintStream = System.err
    var logps: FileOutputStream? = null

    // do this before logfile is created (emptied)
    if (!AppSettings.getLock()) {
        println("Lock file exists...")
        //reveal if socket listens, start normal if not.
        try {
            val revealport = AppSettings.lockFile.readText().toInt()
            val socket = Socket(InetAddress.getByName(null), revealport) // ping other washboard process
            if (socket.isConnected) { // true also if closed now
                println("Revealed running washboard on port $revealport!")
                exitProcess(0)
            } else {
                println("... but nobody listening on port $revealport, normal startup!")
            }
        } catch(e: Exception) {
            println("... but got exception $e, normal startup!")
        }
    }

    class MyConsole(val errchan: Boolean) : OutputStream() {
        override fun write(b: Int) {
            logps?.write(b)
            Global.dolog(b.toChar().toString())
            (if (errchan) oldErr else oldOut).print(b.toChar().toString())
        }
    }
    System.setOut(PrintStream(MyConsole(false), true))
    System.setErr(PrintStream(MyConsole(true), true))
    logps = FileOutputStream(File("/tmp/washboard.log"))

    System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
    System.setProperty(org.slf4j.simple.SimpleLogger.SHOW_DATE_TIME_KEY, "true")
    System.setProperty(org.slf4j.simple.SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss:SSS")
    System.setProperty(org.slf4j.simple.SimpleLogger.LOG_FILE_KEY, "System.out") // and use intellij "grep console" plugin
    // System.setProperty("javax.net.debug", "all")
    logger = KotlinLogging.logger {} // after set properties!

    logger.error("error")
    logger.warn("warn")
    logger.info("info jvm ${System.getProperty("java.version")}")
    logger.debug("debug")
    logger.trace("trace")

    logger.info("fixing bundle dictionary (info.plist)")
    // since a java launcher script is used, the info.plist from .app (build.gradle.kts) is gone if java is launched.
    val infoDict = NSBundle.mainBundle()!!.infoDictionary()!!
    // disable App transport security ATS to allow http (needed also for LAN if hostname used, ip ok). unclear if still needed.
    val ats = NSDictionary.dictionaryWithObject(NSNumber.numberWithBool(true),NSString.stringWith("NSAllowsArbitraryLoads"))
    infoDict.setValue(ats, NSString.stringWith("NSAppTransportSecurity"))
    // fix CFBundlePackageType = APPL, otherwise swt sets setActivationPolicy to NSApplicationActivationPolicyRegular=0, giving it a dock icon
    // this workaround was not needed in swt 3.115.100, this commit made it necessary:
    // https://github.com/eclipse-platform/eclipse.platform.swt/commit/e323e5d89a1d1413968d3b1160e632e77227fd72
    infoDict.setValue(NSString.stringWith("APPL"), NSString.stringWith("CFBundlePackageType"))
    // log
    val keys: NSArray = infoDict.allKeys()
    for (i in 0 until keys.count()) {
        val key = infoDict.allKeys().objectAtIndex(i)
        logger.debug("info dictionary: key: ${NSString(key).string} val: ${NSString(infoDict.objectForKey(key)).string}")
    }

    logger.info("starting Washboard!")

    Settings.loadSettings()
    WashboardApp.launchApp()
}
