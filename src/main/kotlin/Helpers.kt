
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import java.awt.Desktop
import java.io.File
import java.net.URI

object Helpers {
    fun isMac() = System.getProperty("os.name").lowercase().contains("mac")
    fun isLinux() = System.getProperty("os.name").lowercase().matches("(.*nix)|(.*nux)".toRegex())
    fun isWin() = System.getProperty("os.name").lowercase().contains("win")

    fun toJavaPathSeparator(input: String): String =
            if (isWin()) input.replace("""\\""", "/")
            else input

    fun revealFile(file: File, gointo: Boolean = false) {
        when {
            isMac() -> Runtime.getRuntime().exec(arrayOf("open", if (gointo) "" else "-R", file.path))
            isWin() -> Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,${file.path}"))
            isLinux() -> error("not supported OS, tell me how to do it!")
            else -> error("not supported OS, tell me how to do it!")
        }
    }

    fun openURL(url: String) {
        if (Desktop.isDesktopSupported() && url != "") {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(URI(url))
                } catch (e: Exception) {
                    println("ignoring exception openurl: $e")
                    e.printStackTrace()
                }
            }
        }
    }

}

// SWT helpers

fun wButton(parent: Composite, text: String, action: () -> Unit): Button {
    return Button(parent, SWT.PUSH).apply { this.text = text
        addSelectionListener(SelectionListener.widgetSelectedAdapter {
            action()
        })
    }
}
fun wMenuItem(parent: Menu, text: String, action: () -> Unit): MenuItem {
    return MenuItem(parent, SWT.PUSH).apply { this.text = text
        addListener(SWT.Selection) { action() }
    }
}

