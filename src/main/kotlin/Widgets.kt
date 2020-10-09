import mu.KotlinLogging
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*

private val logger = KotlinLogging.logger {}

class Widget(val type: WidgetType, var url: String = "", var x: Int = 100, var y: Int = 100,
             var wx: Int = 250, var wy: Int = 250, var updateIntervalMins: Int = 30,
             var enableClicks: Boolean = false) {
    var bs: BrowserShell? = null
    var lastupdatems: Long = 0L
    override fun toString() = "[${url}]"
}

class ShellEditWidget(w: Widget) {
    private var turl: Text? = null
    init {
        val shell = Shell(WashboardApp.display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = (if (w.lastupdatems == 0L) "New" else "Edit") + " ${w.type.name} widget"
            layout = RowLayout(SWT.VERTICAL).apply { this.fill = true }
            setSize(350, 250)
        }
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

class ShellSettings {
    init {
        val shell = Shell(WashboardApp.display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
            text = "Settings"
            layout = RowLayout(SWT.VERTICAL).apply { this.fill = true }
            setSize(200, 250)
        }
        Label(shell, SWT.NONE).apply { text = "Global keyboard shortcut (KeyStroke like \"shift F12\")" }
        val tgsc = Text(shell, SWT.NONE).apply { text = Settings.settings.globalshortcut }
        wButton(shell, "Save") {
            Settings.settings.globalshortcut = tgsc.text
            WashboardApp.updateGlobalshortcut()
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
        val shell = Shell(WashboardApp.display/*, SWT.BORDER or SWT.APPLICATION_MODAL*/).apply {
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