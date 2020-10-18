import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.browser.*
import org.eclipse.swt.events.*
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Shell
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val logger = KotlinLogging.logger {}

private class BrowserLogFunction(private val w: Widget, browser: Browser, name: String): BrowserFunction(browser, name) {
    override fun function(arguments: Array<out Any>?): Any {
        logger.debug("BROWSER LOG [$w]: ${arguments?.toList()?.joinToString(",")}")
        return ""
    }
}

class BrowserShell(private val w: Widget) {
    val shell = Shell(WashboardApp.display)
    // https://www.eclipse.org/articles/Article-SWT-browser-widget/DocumentationViewer.java
    val browser = Browser(shell, SWT.NONE)
    private var loadedcontent = false

    fun loadWebviewContent() {
        loadedcontent = false
        @Suppress("UNUSED_VARIABLE") val bfun = BrowserLogFunction(w, browser, "logit")
        // redirect all console and errors to logit browserfunction bfun!
        browser.execute("var console = {};console.log = logit;console.info = logit;console.warn = logit;console.error = logit;window.console=console;")
        browser.execute("window.onerror = function (msg, url, lineNo, columnNo, error) {logit(msg+\":\"+url+\":\"+lineNo+\":\"+error);return false;}")
        when(w.type) {
            WidgetType.WEB -> {
                // test connection to avoid hangs
                try {
                    val urlConnect = URL(w.url).openConnection() as HttpURLConnection
                    urlConnect.connect()
                    urlConnect.disconnect()
                    browser.url = w.url
                    w.lastupdatems = System.currentTimeMillis()
                } catch (e: Exception) {
                    logger.error("loadwvc $w test connection: exception $e")
                }
            }
            WidgetType.LOCAL -> {
                val f = File("${AppSettings.getLocalWidgetPath()}/${w.url}/index.html")
                logger.debug("[$w] url: ${f.toURI()}")
                browser.url = f.toURI().toString()
                w.lastupdatems = System.currentTimeMillis()
            }
        }
    }

    init {
        shell.layout = FillLayout()
        shell.location = Point(w.x, w.y)
        shell.size = Point(w.wx, w.wy)
        shell.text = "$w"
        @Suppress("ObjectLiteralToLambda") // lambda doesn't work here
        shell.addListener(SWT.Close, object: Listener { override fun handleEvent(event: Event) {
            logger.info("Removing widget $w from widgets!")
            Settings.widgets.remove(w)
            Settings.widgethistory.add(0, w)
            Settings.saveSettings()
        }})
        browser.addFocusListener(object: FocusListener {
            override fun focusLost(e: FocusEvent?) {}
            override fun focusGained(e: FocusEvent?) {
                WashboardApp.lastActiveWidget = w
            }
        })
        browser.addMouseListener(object: MouseListener {
            override fun mouseDoubleClick(e: MouseEvent?) {}
            override fun mouseDown(e: MouseEvent?) {
                if (w.type == WidgetType.WEB && !w.enableClicks) Helpers.openURL(w.url)
            }
            override fun mouseUp(e: MouseEvent?) {}
        })
        browser.addMouseTrackListener(object: MouseTrackListener {
            override fun mouseEnter(e: MouseEvent?) {
                if (Global.toolWindowsActive.get() == 0) shell.setFocus() // on mouse enter, give this widget focus so that first clicks works!
            }
            override fun mouseExit(e: MouseEvent?) {}
            override fun mouseHover(e: MouseEvent?) {}
        })

        shell.open()
        loadWebviewContent()

        browser.addLocationListener(object: LocationListener {
            override fun changing(event: LocationEvent?) {
                // for preventing clicks if desired. for redirecting widgets enableclicks must be true!
                if (loadedcontent && !w.enableClicks) event?.doit = false
            }
            override fun changed(event: LocationEvent?) {
                loadedcontent = true
            }
        })
        // this is to do stuff after load etc.
        browser.addProgressListener( object: ProgressListener {
            override fun changed(event: ProgressEvent?) {
                //logger.debug("$w b.progresslistener: changed $event")
            }
            override fun completed(event: ProgressEvent?) {
                logger.debug("$w b.progresslistener: completed loading!")
                // browser.evaluate("logit(\"huhu from logit\");")
                // browser.evaluate("console.log(\"huhu from console.log\");")
            }

        })
    }
}