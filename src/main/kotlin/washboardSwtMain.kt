import WashboardApp.display
import mu.KLogger
import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.graphics.Device
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*
import org.eclipse.swt.widgets.List
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread
import kotlin.system.exitProcess


private lateinit var logger: KLogger



class Widget(val type: WidgetType, var url: String = "", var x: Int = 100, var y: Int = 100,
             var wx: Int = 250, var wy: Int = 250, var updateIntervalMins: Int = 30,
             var enableClicks: Boolean = false) {
    var bs: BrowserShell? = null
    var lastupdatems: Long = 0
    override fun toString() = "[${url}]"
}

class BrowserShell(private val w: Widget) {
    val shell = Shell(display)
    // https://www.eclipse.org/articles/Article-SWT-browser-widget/DocumentationViewer.java
    private val browser = Browser(shell, SWT.NONE)

    fun loadWebviewContent() {
        when(w.type) {
            WidgetType.WEB -> w.bs?.browser?.url = w.url
            WidgetType.LOCAL -> {
                val f = File("${StoreSettings.getLocalWidgetPath()}/${w.url}/index.html")
                logger.debug("[$w] url: ${f.toURI()}")
                w.bs?.browser?.url = f.toURI().toString()
            }
            WidgetType.DASHBOARD -> error("not impl")
        }
        w.lastupdatems = System.currentTimeMillis()
    }

    init {
        shell.layout = FillLayout()
        shell.location = Point(w.x, w.y)
        shell.size = Point(w.wx, w.wy)
        shell.open()
        loadWebviewContent()
    }
}

class ShellEditWidget(w: Widget) {
    private var turl: Text? = null
    init {
        val shell = Shell(display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = "Edit widget"
            layout = RowLayout(SWT.VERTICAL)
            setSize(200, 250)
        }
        when(w.type) {
            WidgetType.LOCAL -> {
                Label(shell, SWT.NONE).apply { text = "Local widgets reside in a folder below ${StoreSettings.getLocalWidgetPath()}, and should at least contain index.html" }
                turl = Text(shell, SWT.NONE).apply { text = w.url }
                wButton(shell, "Choose...") {
                    FileDialog(shell, SWT.OPEN).apply {
                        text = "Select widget folder"
                        fileName = StoreSettings.getLocalWidgetPath()
                    }.open()?.let {
                        turl!!.text = it
                    }
                }
            }
            WidgetType.WEB -> {
                Label(shell, SWT.NONE).apply { text = "URL:" }
                turl = Text(shell, SWT.NONE).apply { text = w.url }
            }
            WidgetType.DASHBOARD -> {
                error("not impl")
            }
        }
        Label(shell, SWT.NONE).apply { text = "Update interval (minutes)" }
        val tupdi = Text(shell, SWT.NONE).apply { text = w.updateIntervalMins.toString() }
        Label(shell, SWT.NONE).apply { text = "Enable mouse clicks (otherwise open URL)" }
        val bclic = Button(shell, SWT.CHECK).apply { selection = w.enableClicks }
        wButton(shell, "Update") {
            WashboardApp.unshowWidget(w)
            w.url = turl!!.text
            w.updateIntervalMins = tupdi.text.toIntOrNull()?:w.updateIntervalMins
            w.enableClicks = bclic.selection
            Settings.widgethistory.add(w)
            WashboardApp.showWidget(w)
            Settings.saveSettings()
            shell.close()
        }
        wButton(shell, "Cancel") { shell.close() }
        shell.pack()
        shell.open()
    }
}


class ShellHistory {
    init {
        val shell = Shell(display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = "Washboard History"
            layout = RowLayout(SWT.VERTICAL)
            setSize(200, 250)
        }
        val lv = List(shell, SWT.H_SCROLL or SWT.V_SCROLL)
        Settings.widgethistory.forEach {
            logger.debug("hist: $it")
            lv.add(it.toString())
        }
        wButton(shell, "Add widget") {
            if (lv.selectionIndex > -1) {
                WashboardApp.showWidget(Settings.widgethistory[lv.selectionIndex])
                Settings.saveSettings()
            }
        }

        wButton(shell, "Delete") {
            if (lv.selectionIndex > -1) {
                Settings.widgethistory.removeAt(lv.selectionIndex)
                Settings.saveSettings()
            }
        }

        shell.pack()
        shell.open()
    }
}

object WashboardApp {
    private lateinit var focusTimer: Timer
    private var focusLostCount = 0 // -1: hidden
    private var serverSocket: ServerSocket? = null

    private fun beforeQuit() {
        focusTimer.cancel()
        Settings.saveSettings()
        serverSocket?.close()
        StoreSettings.releaseLock()
    }

    private fun showApp() {
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().activateIgnoringOtherApps(true)
        val now = System.currentTimeMillis()
        Settings.widgets.forEach { w ->
            w.bs!!.shell.setActive() // also bring all widgets in foreground, above command works not always! TODO unreliable possibly just add timerthread to try later again?
            if (now - w.lastupdatems > w.updateIntervalMins*60*1000) {
                logger.info("reloading widget $w")
                w.bs!!.loadWebviewContent()
            }
        }
        focusLostCount = 0
    }

    private fun hideApp() {
        focusLostCount = -1
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().hide(null)
    }

    private fun quitApp() {
        beforeQuit()
        exitProcess(0)
    }

    lateinit var display: Display
    private lateinit var mainShell: Shell

    fun unshowWidget(w: Widget) {
        w.bs?.shell?.close()
        Settings.widgets.remove(w)
    }

    fun showWidget(w: Widget, addToSettings: Boolean = true) {
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
        display = Display.getDefault() // this shows dock icon, no matter what i do!
        Display.setAppName("washboard-swt") // need twice https://stackoverflow.com/a/45088431

        // hide dock icon related to https://stackoverflow.com/questions/2832961/is-it-possible-to-hide-the-dock-icon-programmatically?rq=1
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().setActivationPolicy(1)
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().activateIgnoringOtherApps(true)

        mainShell = Shell(display)
        mainShell.text = "WashboardSwt"
        mainShell.layout = FillLayout()

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
        trayItem.image = Image(display, javaClass.getResource("icon16x16.png").openStream())
        val menu = Menu(mainShell, SWT.PUSH)
        wMenuItem(menu, "About washboard") { Helpers.openURL("https://quphotonics.org") }
        wMenuItem(menu, "Show") { showApp() }
        wMenuItem(menu, "Hide") { hideApp() }
        wMenuItem(menu, "Add web widget") {
            val w = Widget(WidgetType.WEB)
            ShellEditWidget(w)
        }
        wMenuItem(menu, "Add local widget") { ShellEditWidget(Widget(WidgetType.LOCAL)) }
        wMenuItem(menu, "Edit widget") {
            Settings.widgets.forEach {
                logger.debug("$it: ${it.bs!!.shell.isFocusControl}")
            }
        }
        wMenuItem(menu, "History") { ShellHistory() }
        wMenuItem(menu, "Settings folder") { Helpers.revealFile(StoreSettings.getSettingFile()) }
        wMenuItem(menu, "Quit") { quitApp() }
        trayItem.addListener(SWT.MenuDetect) {
            showApp()
            menu.isVisible = true
        }

        mainShell.pack()
        mainShell.open()

        focusTimer = fixedRateTimer("focus timer", period = 200) {
            if (focusLostCount > -1) {
                display.syncExec {
                    if (display.focusControl == null) {
                        focusLostCount += 1
                        if (focusLostCount > 3) {
                            hideApp()
                        }
                    } else focusLostCount = 0
                }
            }
        }

        showAllWidgets()

        serverSocket = ServerSocket(0)
        logger.info("listening on port " + serverSocket?.localPort)
        StoreSettings.lockFile.writeText(serverSocket!!.localPort.toString())
        thread { // reveal thread
            while (true) {
                serverSocket!!.accept()
                logger.info("Connection to socket, reveal!")
                display.syncExec {
                    showApp()
                }
            }
        }



        while (!mainShell.isDisposed) {
            if (!display.readAndDispatch()) display.sleep()
        }

        beforeQuit()
        display.dispose()

    }
}


fun main() {

    // disable App transport security ATS to allow http (needed also for LAN if hostname used, ip ok)
    val ats = org.eclipse.swt.internal.cocoa.NSDictionary.dictionaryWithObject(
            org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
            org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAllowsArbitraryLoads"))
    org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
            ats, org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAppTransportSecurity"))

    val oldOut: PrintStream = System.out
    val oldErr: PrintStream = System.err
    var logps: FileOutputStream? = null

    // do before logfile is created
    if (!StoreSettings.getLock()) {
        println("Lock file exists...")
        val revealport = StoreSettings.lockFile.readText().toInt()
        //reveal if socket listens, start normal if not.
        val socket = Socket(InetAddress.getByName(null), revealport) // ping other washboard process
        if (socket.isConnected) { // true also if closed now
            println("Revealed running washboard on port $revealport!")
            exitProcess(0)
        } else {
            println("... but nobody listening on port $revealport, normal startup!")
        }
    }

    class MyConsole(val errchan: Boolean) : OutputStream() {
        override fun write(b: Int) {
            logps?.write(b)
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


    logger.info("starting Washboard!")

    WashboardApp.launchApp()
}
