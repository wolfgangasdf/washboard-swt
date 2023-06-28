import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*

private val logger = KotlinLogging.logger {}

class Widget(val type: WidgetType, var url: String = "", var x: Int = 100, var y: Int = 100,
             var wx: Int = 250, var wy: Int = 250, var updateIntervalMins: Int = 30,
             var enableClicks: Boolean = true) {
    var bs: BrowserShell? = null
    var lastupdatems: Long = 0L
    override fun toString() = "[${url}]"
}

class ShellEditWidget(w: Widget, isnew: Boolean) {
    private var turl: Text? = null
    init {
        Global.toolWindowsActive.incrementAndGet()
        val shell = Shell(WashboardApp.display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = (if (isnew) "New" else "Edit") + " ${w.type.name} widget"
            layout = RowLayout(SWT.VERTICAL).apply { this.fill = true }
            setSize(350, 250)
        }
        addCloseListenerEnableTracking(shell)
        when(w.type) {
            WidgetType.LOCAL -> {
                Label(shell, SWT.NONE).apply { text = "Local widgets reside in a folder below\n${AppSettings.getLocalWidgetPath()},\nand should at least contain index.html" }
                turl = Text(shell, SWT.NONE).apply { text = w.url }
                wButton(shell, "Choose...") {
                    WashboardApp.stopFocusTimer()
                    DirectoryDialog(shell, SWT.OPEN).apply {
                        text = "Select widget folder"
                        filterPath = AppSettings.getLocalWidgetPath()
                    }.open()?.let {
                        turl!!.text = AppSettings.removePrefixPath(it, AppSettings.getLocalWidgetPath())
                    }
                    WashboardApp.startFocusTimer()
                }
            }
            WidgetType.WEB -> {
                Label(shell, SWT.NONE).apply { text = "URL:" }
                turl = Text(shell, SWT.NONE).apply { text = w.url }
            }
        }
        Label(shell, SWT.NONE).apply { text = "Update interval (minutes)" }
        val tupdi = Text(shell, SWT.NONE).apply { text = w.updateIntervalMins.toString() }
        Label(shell, SWT.NONE).apply { text = "Enable mouse clicks (otherwise open URL). Note that redirecting widgets need this enabled, too." }
        val bclic = Button(shell, SWT.CHECK).apply { selection = w.enableClicks }
        wButton(shell, if (isnew) "Save" else "Update") {
            w.url = turl!!.text
            w.updateIntervalMins = tupdi.text.toIntOrNull()?:w.updateIntervalMins
            w.enableClicks = bclic.selection
            w.bs?.loadWebviewContent()
            if (isnew) {
                WashboardApp.showWidget(w, isnew)
            }
            Settings.saveSettings()
            shell.close()
        }
        wButton(shell, "Cancel") { shell.close() }
        shell.pack()
        shell.open()
    }
}

class ShellSettings {
    init {
        Global.toolWindowsActive.incrementAndGet()
        val shell = Shell(WashboardApp.display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = "Settings"
            layout = RowLayout(SWT.VERTICAL).apply { this.fill = true }
            setSize(200, 250)
        }
        addCloseListenerEnableTracking(shell)
        Label(shell, SWT.NONE).apply { text = "Test" }
        val tgsc = Text(shell, SWT.NONE).apply { text = Settings.settings.test }
        wButton(shell, "Save") {
            Settings.settings.test = tgsc.text
            Settings.saveSettings()
            shell.close()
        }
        wButton(shell, "Cancel") { shell.close() }
        shell.pack()
        shell.open()
    }
}

fun addCloseListenerEnableTracking(shell: Shell) {
    @Suppress("ObjectLiteralToLambda") // lambda doesn't work here
    shell.addListener(SWT.Close, object: Listener { override fun handleEvent(event: Event) {
        logger.info("Re-enable focus tracking!")
        Global.toolWindowsActive.decrementAndGet()
    }})
}

class ShellHistory {
    init {
        Global.toolWindowsActive.incrementAndGet()
        val shell = Shell(WashboardApp.display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = "Washboard History"
            layout = RowLayout(SWT.VERTICAL).apply { fill = true }
            setSize(200, 250)
        }
        addCloseListenerEnableTracking(shell)
        val lv = List(shell, SWT.H_SCROLL or SWT.V_SCROLL)
        Settings.widgethistory.forEach {
            lv.add(it.toString())
        }
        wButton(shell, "Load widget") {
            if (lv.selectionIndex > -1) {
                WashboardApp.showWidget(Settings.widgethistory[lv.selectionIndex], true)
                Settings.saveSettings()
            }
        }

        wButton(shell, "Delete") {
            val seli = lv.selectionIndex
            if (seli > -1) {
                Settings.widgethistory.removeAt(seli)
                lv.remove(seli)
                if (lv.itemCount > seli) lv.select(seli) else if (seli > 0) lv.select(seli - 1)
                Settings.saveSettings()
            }
        }

        shell.pack()
        shell.open()
    }
}