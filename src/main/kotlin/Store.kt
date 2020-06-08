
import mu.KotlinLogging
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.ServerSocket
import java.util.*


private val logger = KotlinLogging.logger {}

object StoreSettings {
    private var settpath = ""

    init {
        settpath = when {
            Helpers.isMac() -> System.getProperty("user.home") + "/Library/Application Support/WashboardApp" // TODO remove App
            Helpers.isLinux() -> System.getProperty("user.home") + "/.washboard"
            Helpers.isWin() -> Helpers.toJavaPathSeparator(System.getenv("APPDATA")) + "\\Washboard"
            else -> throw Exception("operating system not found")
        }
        File(getLocalWidgetPath()).let { if (!it.isDirectory) it.mkdirs() }
        File(getDashboardWidgetPath()).let { if (!it.isDirectory) it.mkdirs() }
    }

    fun getSettingFile(): File = File("$settpath/washboard.properties")
    fun getLocalWidgetPath(): String = "$settpath/widgets"
    fun getDashboardWidgetPath(): String = "$settpath/dashboardwidgets"
    val lockFile = File("$settpath/lockfile.lock")
    private val revealFile = File("$settpath/reveal")

    fun getLock(): Boolean = lockFile.createNewFile()

    fun releaseLock(): Boolean = lockFile.delete()

    fun checkRevealFile(deleteIfExisted: Boolean = true): Boolean {
        val res = revealFile.exists()
        if (deleteIfExisted && res) revealFile.delete()
        return res
    }

    fun setRevealFile() { revealFile.createNewFile() }
}

///////////////////////// settings

class MainSettings(var hideTimeout: Int = 50)

enum class WidgetType(val i: Int) {
    WEB(0),
    LOCAL(1),
    DASHBOARD(2);
    companion object {
        fun fromInt(v: Int) = values().first { it.i == v }
    }
}

object Settings {
    val widgets = arrayListOf<Widget>()
    val settings = MainSettings()
    val widgethistory = arrayListOf<Widget>()

    private fun saveWidget(props: Properties, prefix: String, w: Widget) {
        props["$prefix.type"] = w.type.i.toString()
        props["$prefix.url"] = w.url
        props["$prefix.x"] = w.bs!!.shell.location.x.toString()
        props["$prefix.y"] = w.bs!!.shell.location.y.toString()
        props["$prefix.wx"] = w.bs!!.shell.size.x.toString()
        props["$prefix.wy"] = w.bs!!.shell.size.y.toString()
        props["$prefix.updateInterval"] = w.updateIntervalMins.toString()
        props["$prefix.enableClicks"] = w.enableClicks.toString()
    }
    private fun loadWidget(props: Map<String, String>, prefix: String): Widget {
        return Widget(WidgetType.fromInt(props.getOrDefault("$prefix.type", "0").toInt()),
                props.getOrDefault("$prefix.url", ""),
                props.getOrDefault("$prefix.x", "").toDouble().toInt(), // TODO remove todouble
                props.getOrDefault("$prefix.y", "").toDouble().toInt(),
                props.getOrDefault("$prefix.wx", "").toDouble().toInt(),
                props.getOrDefault("$prefix.wy", "").toDouble().toInt(),
                props.getOrDefault("$prefix.updateInterval", "30").toDouble().toInt(),
                props.getOrDefault("$prefix.enableClicks", "false").toBoolean())
    }

    fun saveSettings() {
        // TODO
        logger.error("savesett disabled !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        return
        val props = Properties()
        props["settingsversion"] = "1"
        props["wb.hideTimeout"] = settings.hideTimeout.toString()
        props["widgets"] = widgets.size.toString()
        widgets.forEachIndexed { idx, w -> saveWidget(props, "w.$idx", w) }
        props["widgethistorysize"] = widgethistory.size.toString()
        widgethistory.forEachIndexed { idx, w -> saveWidget(props, "wh.$idx", w) }
        StoreSettings.getSettingFile().parentFile.mkdirs()
        val fw = FileWriter(StoreSettings.getSettingFile())
        props.store(fw, null)
        logger.info("settings saved!")
    }

    private fun loadSettings() {
        logger.info("load settings ${StoreSettings.getSettingFile()}")
        widgets.clear()
        if (StoreSettings.getSettingFile().exists()) {
            val propsx = Properties()
            val fr = FileReader(StoreSettings.getSettingFile())
            propsx.load(fr)
            val props = propsx.map { (k, v) -> k.toString() to v.toString() }.toMap()
            if (props["settingsversion"] != "1") throw Exception("wrong settingsversion!")
            try {
                settings.hideTimeout = props.getOrDefault("wb.hideTimeout", "50").toInt()
                for (idx in 0 until props.getOrDefault("widgets", "0").toInt()) {
                    widgets += loadWidget(props, "w.$idx")
                    logger.debug("loaded widget ${widgets.last()}")
                }
                for (idx in 0 until props.getOrDefault("widgethistorysize", "0").toInt()) {
                    widgethistory += loadWidget(props, "wh.$idx")
                }
            } catch (e: Exception) {
                logger.error("error loading settings: ${e.message}")
                e.printStackTrace()
            }
            logger.info("settings loaded!")
        }
    }

    init {
        loadSettings()
    }

}

