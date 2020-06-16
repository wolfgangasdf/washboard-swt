import WashboardApp.display
import WashboardApp.startFocusTimer
import WashboardApp.stopFocusTimer
import mu.KLogger
import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.events.MouseListener
import org.eclipse.swt.graphics.Device
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*
import org.eclipse.swt.widgets.List
import org.kottpd.HttpRequest
import org.kottpd.HttpResponse
import org.kottpd.Server
import java.io.*
import java.net.*
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.schedule
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
    val browser = Browser(shell, SWT.NONE)

    fun loadWebviewContent() {
        when(w.type) {
            WidgetType.WEB -> {
                // test connection
                try {
                    val urlConnect = URL(w.url).openConnection() as HttpURLConnection
                    urlConnect.connect()
                    urlConnect.disconnect()
                    w.bs?.browser?.url = w.url
                    w.lastupdatems = System.currentTimeMillis()
                } catch (e: Exception) {
                    logger.debug("loadwvc $w test connection: exception $e")
                }
            }
            WidgetType.LOCAL -> {
                val f = File("${AppSettings.getLocalWidgetPath()}/${w.url}/index.html")
                logger.debug("[$w] url: ${f.toURI()}")
                w.bs?.browser?.url = f.toURI().toString()
                w.lastupdatems = System.currentTimeMillis()
            }
            WidgetType.DASHBOARD -> error("not impl")
        }
    }

    fun update() {
        browser.url = w.url
    }

    init {
        shell.layout = FillLayout()
        shell.location = Point(w.x, w.y)
        shell.size = Point(w.wx, w.wy)
        @Suppress("ObjectLiteralToLambda") // lambda doesn't work here
        shell.addListener(SWT.Close, object: Listener { override fun handleEvent(event: Event) {
            logger.info("Removing widget $w from widgets!")
            Settings.widgets.remove(w)
            Settings.saveSettings()
        }})
        browser.addMouseListener(object: MouseListener {
            override fun mouseDoubleClick(e: MouseEvent?) {}
            override fun mouseDown(e: MouseEvent?) {
                if (!w.enableClicks) Helpers.openURL(w.url)
            }
            override fun mouseUp(e: MouseEvent?) {}
        })
        shell.open()
        loadWebviewContent()
    }
}

class ShellEditWidget(w: Widget) {
    private var turl: Text? = null
    init {
        val shell = Shell(display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = "Edit widget"
            layout = RowLayout(SWT.VERTICAL).apply { this.fill = true }
            setSize(200, 250)
        }
        when(w.type) {
            WidgetType.LOCAL -> {
                Label(shell, SWT.NONE).apply { text = "Local widgets reside in a folder below\n${AppSettings.getLocalWidgetPath()},\nand should at least contain index.html" }
                turl = Text(shell, SWT.NONE).apply { text = w.url }
                wButton(shell, "Choose...") {
                    stopFocusTimer()
                    DirectoryDialog(shell, SWT.OPEN).apply {
                        text = "Select widget folder"
                        filterPath = AppSettings.getLocalWidgetPath()
                    }.open()?.let {
                        turl!!.text = AppSettings.removePrefixPath(it, AppSettings.getLocalWidgetPath())
                    }
                    startFocusTimer()
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
            w.url = turl!!.text
            w.updateIntervalMins = tupdi.text.toIntOrNull()?:w.updateIntervalMins
            w.enableClicks = bclic.selection
            w.bs?.update()
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
            layout = RowLayout(SWT.VERTICAL).apply { fill = true }
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
                lv.remove(lv.selectionIndex)
                Settings.saveSettings()
            }
        }

        shell.pack()
        shell.open()
    }
}

object WashboardApp {
    private var focusTimer: Timer? = null
    private var appShown = true
    private var serverSocket: ServerSocket? = null
    private var revealThread: Thread? = null

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

    private fun showApp(secondcall: Boolean = false) {
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().activateIgnoringOtherApps(true)
        val now = System.currentTimeMillis()
        Settings.widgets.forEach { w ->
            w.bs!!.shell.setActive()
            if (now - w.lastupdatems > w.updateIntervalMins * 60 * 1000) {
                logger.info("reloading widget $w")
                w.bs!!.loadWebviewContent()
            }
        }
        if (!secondcall) {
            startFocusTimer()
            // ugly workaround that not all windows are always on top
            Timer().schedule(100) {
                display.syncExec { showApp(true) }
            }
        }
        appShown = true
    }

    private fun hideApp() {
        stopFocusTimer()
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().hide(null)
        appShown = false
    }

    private fun quitApp() {
        beforeQuit()
        exitProcess(0)
    }

    lateinit var display: Display
    private lateinit var mainShell: Shell

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
            Settings.widgets.find { it.bs!!.browser.isFocusControl }?.also { ShellEditWidget(it) }
        }
        wMenuItem(menu, "History") { ShellHistory() }
        wMenuItem(menu, "Settings folder") { Helpers.revealFile(AppSettings.getSettingFile()) }
        wMenuItem(menu, "Quit") { quitApp() }
        trayItem.addListener(SWT.MenuDetect) {
            if (!appShown) showApp()
            menu.isVisible = true
        }

        mainShell.pack()
        mainShell.open()

        serverSocket = ServerSocket(0)
        logger.info("listening on port " + serverSocket?.localPort)
        AppSettings.lockFile.writeText(serverSocket!!.localPort.toString())
        revealThread = thread { // reveal thread
            while (!serverSocket!!.isClosed) {
                try {
                    serverSocket!!.accept()
                    logger.info("Connection to socket, reveal!")
                    display.syncExec {
                        showApp()
                    }
                } catch (e: SocketException) {
                    logger.debug("serversocket got exception: $e")
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

        // run gui
        while (!mainShell.isDisposed) {
            if (!display.readAndDispatch()) display.sleep()
        }

        quitApp()
    }


    private fun myStaticFiles(server: Server, abspath: String) {
        fun mywalk(abspath: String, prefix: String, func: (File, HttpRequest, HttpResponse) -> Unit) {
            File(abspath).walkTopDown().forEach { file ->
                if (!file.isDirectory) {
                    val rp = file.path.substring(abspath.length)
                    var doit = true
                    var url = prefix + rp
                    // only use en locale, must be served from /
                    if (rp.contains("en.lproj/")) {
                        url = prefix + rp.substring("en.lproj/".length)
                    } else if (rp.contains(".lproj/")) {
                        doit = false
                    }
                    if (doit) {
                        server.get(url) { req, res -> func(file, req, res) }
                        logger.debug("started get for $url")
                    }
                }
            }
        }
        // add for widget files
        val x = "SystemLibraryWidgetResources"
        mywalk(abspath, "") { file, req, res ->
            val s = file.readText().replace("file:///System/Library/WidgetResources/", "/$x/")
            res.send(s)
        }

        // add apple support files
        mywalk(this.javaClass.getResource(x).file, "/$x") { file, req, res ->
            val s = file.readText().replace("file:///System/Library/WidgetResources/", "/$x/")
            res.send(s)
        }
    }

    // TODO web server test
    fun wstest() {
        val server = Server(9876)
        myStaticFiles(server, "/Users/wolle/Library/Application Support/Washboard/dashboardwidgets/Sol.wdgt")
        server.start()
        Thread.sleep(123456789)
        server.threadPool.shutdownNow() // TODO does this stop?
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

    Settings.loadSettings()
    WashboardApp.launchApp()
//    WashboardApp.wstest() // TODO
}
