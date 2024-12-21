
import mu.KotlinLogging
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*


private val logger = KotlinLogging.logger {}

object AppSettings {
    private var settpath = ""

    init {
        settpath = when {
            Helpers.isMac() -> System.getProperty("user.home") + "/Library/Application Support/Washboard"
            Helpers.isLinux() -> System.getProperty("user.home") + "/.washboard"
            Helpers.isWin() -> Helpers.toJavaPathSeparator(System.getenv("APPDATA")) + "\\Washboard"
            else -> error(Exception("operating system not found"))
        }
        File(getLocalWidgetPath()).let { if (!it.isDirectory) it.mkdirs() }
    }

    fun getSettingFile(): File = File("$settpath/washboard.properties")
    fun getLocalWidgetPath(): String = "$settpath/widgets"
    fun removePrefixPath(path: String, prefixPath: String): String =
            File(path).absolutePath.removePrefix(File(prefixPath).absolutePath + "/")

    val lockFile = File("$settpath/lockfile.lock")
    fun getLock(): Boolean = lockFile.createNewFile()
    fun releaseLock(): Boolean = lockFile.delete()
}

///////////////////////// settings

class MainSettings(var test: String = "")

enum class WidgetType(val i: Int) {
    WEB(0),
    LOCAL(1);
    companion object {
        fun fromInt(v: Int) = entries.first { it.i == v }
    }
}

object Settings {
    val widgets = arrayListOf<Widget>()
    val settings = MainSettings()
    val widgethistory = arrayListOf<Widget>()

    private fun saveWidget(props: Properties, prefix: String, w: Widget, isHistory: Boolean) {
        // logger.debug("save widget [$prefix]: $w")
        props["$prefix.type"] = w.type.i.toString()
        props["$prefix.url"] = w.url
        props["$prefix.x"] = (if (isHistory) w.x else w.bs!!.shell.location.x).toString()
        props["$prefix.y"] = (if (isHistory) w.y else w.bs!!.shell.location.y).toString()
        props["$prefix.wx"] = (if (isHistory) w.wx else w.bs!!.shell.size.x).toString()
        props["$prefix.wy"] = (if (isHistory) w.wy else w.bs!!.shell.size.y).toString()
        props["$prefix.updateInterval"] = w.updateIntervalMins.toString()
        props["$prefix.enableClicks"] = w.enableClicks.toString()
    }
    private fun loadWidget(props: Map<String, String>, prefix: String): Widget {
        return Widget(WidgetType.fromInt(props.getOrDefault("$prefix.type", "0").toInt()),
                props.getOrDefault("$prefix.url", ""),
                props.getOrDefault("$prefix.x", "").toInt(),
                props.getOrDefault("$prefix.y", "").toInt(),
                props.getOrDefault("$prefix.wx", "").toInt(),
                props.getOrDefault("$prefix.wy", "").toInt(),
                props.getOrDefault("$prefix.updateInterval", "30").toInt(),
                props.getOrDefault("$prefix.enableClicks", "false").toBoolean())
    }

    fun saveSettings() {
        val props = Properties()
        props["settingsversion"] = "1"
        props["wb.test"] = settings.test
        props["widgets"] = widgets.size.toString()
        widgets.forEachIndexed { idx, w -> saveWidget(props, "w.$idx", w, false) }
        props["widgethistorysize"] = widgethistory.size.toString()
        widgethistory.forEachIndexed { idx, w -> saveWidget(props, "wh.$idx", w, true) }
        AppSettings.getSettingFile().parentFile.mkdirs()
        val fw = FileWriter(AppSettings.getSettingFile())
        props.store(fw, null)
        logger.info("settings saved!")
    }

    fun loadSettings() {
        logger.info("load settings ${AppSettings.getSettingFile()}")
        widgets.clear()
        if (AppSettings.getSettingFile().exists()) {
            val propsx = Properties()
            val fr = FileReader(AppSettings.getSettingFile())
            propsx.load(fr)
            val props = propsx.map { (k, v) -> k.toString() to v.toString() }.toMap()
            if (props["settingsversion"] != "1") error(Exception("wrong settingsversion!"))
            try {
                settings.test = props.getOrDefault("wb.test", "")
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
                error("Error loading settings")
            }
            logger.info("settings loaded!")
        }
    }
}

