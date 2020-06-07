import WashboardApp.display
import mu.KLogger
import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.events.SelectionListener
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
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.system.exitProcess

private lateinit var logger: KLogger

// https://www.eclipse.org/articles/Article-SWT-browser-widget/DocumentationViewer.java


class Widget(val type: WidgetType, val url: String = "", var x: Double = 100.0, var y: Double = 100.0,
             var wx: Double = 250.0, var wy: Double = 250.0, var updateIntervalMins: Double = 30.0,
             var enableClicks: Boolean = false) {
    var bs: BrowserShell? = null
    var lastupdatems: Long = 0
    override fun toString() = "[${url}]"
}

class BrowserShell(private val w: Widget) {
    val shell = Shell(display)
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
        shell.location = Point(w.x.toInt(), w.y.toInt())
        shell.size = Point(w.wx.toInt(), w.wy.toInt())
        shell.open()
        loadWebviewContent()
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
        Button(shell, SWT.PUSH).apply {
            text = "Add widget"
            addSelectionListener( SelectionListener.widgetSelectedAdapter{
                if (lv.selectionIndex > -1) {
                    WashboardApp.showWidget(Settings.widgethistory[lv.selectionIndex])
                    Settings.saveSettings()
                }
            })
        }

        Button(shell, SWT.PUSH).apply {
            text = "Delete"
            addSelectionListener( SelectionListener.widgetSelectedAdapter{
                if (lv.selectionIndex > -1) {
                    Settings.widgethistory.removeAt(lv.selectionIndex)
                    Settings.saveSettings()
                }
            })
        }

        shell.pack()
        shell.open()
    }
}

object WashboardApp {
    private lateinit var revealTimer: Timer

    private fun beforeQuit() {
        revealTimer.cancel()
        Settings.saveSettings()
        StoreSettings.releaseLock()
    }

    private fun reveal() {
        logger.debug("reveal app!")
        display.asyncExec {
            showApp()
        }
    }

    private fun showApp() {
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().activateIgnoringOtherApps(true) // TODO ugly
        val now = System.currentTimeMillis()
        Settings.widgets.forEach { w ->
            if (now - w.lastupdatems > w.updateIntervalMins*60*1000) {
                logger.info("reloading widget $w")
                w.bs!!.loadWebviewContent()
            }
        }
    }

    private fun hideApp() {
        org.eclipse.swt.internal.cocoa.NSApplication.sharedApplication().hide(null) // TODO ugly
    }

    private fun quitApp() {
        beforeQuit()
        exitProcess(0)
    }

    lateinit var display: Display
    lateinit var mainShell: Shell

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
        display = Display.getDefault() // this shows dock icon, no matter what i do! TODO
        Display.setAppName("washboard-swt") // need twice https://stackoverflow.com/a/45088431

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
        trayItem.image = Image(display, 16, 16)
        val menu = Menu(mainShell, SWT.POP_UP)
        MenuItem(menu, SWT.PUSH).apply {
            text = "Show"
            addListener(SWT.Selection) { showApp() }
        }
        MenuItem(menu, SWT.PUSH).apply {
            text = "Hide"
            addListener(SWT.Selection) { hideApp() }
        }
        MenuItem(menu, SWT.PUSH).apply {
            text = "History..."
            addListener(SWT.Selection) {
                ShellHistory()
            }
        }
        MenuItem(menu, SWT.PUSH).apply {
            text = "Exit"
            addListener(SWT.Selection) { quitApp() }
        }
        trayItem.addListener(SWT.MenuDetect) { menu.isVisible = true }

        mainShell.pack()
        mainShell.open()

        revealTimer = fixedRateTimer("reveal timer", period = 200) {
            if (StoreSettings.checkRevealFile()) {
                reveal()
            }
        }

        showAllWidgets()
//        mainShell.forceActive() // get focus

        while (!mainShell.isDisposed) {
            if (!display.readAndDispatch()) display.sleep()
        }

        beforeQuit()
        display.dispose()

    }
}


fun main() {
    // hide dock icon https://stackoverflow.com/questions/24312260/javafx-application-hide-osx-dock-icon
    System.setProperty("apple.awt.UIElement", "true") // TODO doesn't work

    // disable App transport security ATS
    val ats = org.eclipse.swt.internal.cocoa.NSDictionary.dictionaryWithObject(
            org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
            org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAllowsArbitraryLoads"))
    org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
            ats, org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAppTransportSecurity"))

    // hide tray icon // TODO doesn't work
//    org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
//            org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
//            org.eclipse.swt.internal.cocoa.NSString.stringWith("LSUIElement"))

    val oldOut: PrintStream = System.out
    val oldErr: PrintStream = System.err
    var logps: FileOutputStream? = null
    var lockRevealFilesDidExist = false

    // do before logfile is created
    if (!StoreSettings.getLock()) {
        println("Lock file exists...")
        if (StoreSettings.checkRevealFile(true)) {
            println("... but also reveal file, normal startup!")
            lockRevealFilesDidExist = true
        } else {
            println("... revealing running instance!")
            StoreSettings.setRevealFile()
            exitProcess(0)
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
//    System.setProperty("prism.verbose", "true")
    logger = KotlinLogging.logger {} // after set properties!

    logger.error("error")
    logger.warn("warn")
    logger.info("info jvm ${System.getProperty("java.version")}")
    logger.debug("debug")
    logger.trace("trace")


    logger.info("starting Washboard! lockRevealFilesDidExist=$lockRevealFilesDidExist")

    WashboardApp.launchApp()
}
