import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.browser.LocationEvent
import org.eclipse.swt.browser.LocationListener
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

class BrowserShell(private val w: Widget) {
    val shell = Shell(WashboardApp.display)
    // https://www.eclipse.org/articles/Article-SWT-browser-widget/DocumentationViewer.java
    val browser = Browser(shell, SWT.NONE)
    private var loadedcontent = false

    fun loadWebviewContent() {
        when(w.type) {
            WidgetType.WEB -> {
                // test connection to avoid hangs
                try {
                    val urlConnect = URL(w.url).openConnection() as HttpURLConnection
                    urlConnect.connect()
                    urlConnect.disconnect()
                    w.bs?.browser?.url = w.url
                    w.lastupdatems = System.currentTimeMillis()
                } catch (e: Exception) {
                    logger.error("loadwvc $w test connection: exception $e")
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
        browser.addFocusListener(object: FocusListener {
            override fun focusLost(e: FocusEvent?) {}
            override fun focusGained(e: FocusEvent?) {
                WashboardApp.lastActiveWidget = w
            }
        })
        browser.addMouseListener(object: MouseListener {
            override fun mouseDoubleClick(e: MouseEvent?) {}
            override fun mouseDown(e: MouseEvent?) {
                if (!w.enableClicks) Helpers.openURL(w.url)
            }
            override fun mouseUp(e: MouseEvent?) {}
        })
        browser.addMouseTrackListener(object: MouseTrackListener {
            override fun mouseEnter(e: MouseEvent?) {
                shell.setFocus() // on mouse enter, give this widget focus so that first clicks works!
            }
            override fun mouseExit(e: MouseEvent?) {}
            override fun mouseHover(e: MouseEvent?) {}
        })

        shell.open()
        loadWebviewContent()
        browser.addLocationListener(object: LocationListener { // for preventing clicks if desired
            override fun changing(event: LocationEvent?) {
                if (loadedcontent && !w.enableClicks) event?.doit = false
            }
            override fun changed(event: LocationEvent?) {
                loadedcontent = true
            }
        })
    }
}