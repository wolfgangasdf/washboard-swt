import org.eclipse.swt.SWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.graphics.Device
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.internal.cocoa.NSDictionary
import org.eclipse.swt.internal.cocoa.NSMutableDictionary
import org.eclipse.swt.internal.cocoa.NSString
import org.eclipse.swt.internal.cocoa.id
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.*

// https://www.eclipse.org/articles/Article-SWT-browser-widget/DocumentationViewer.java

class BrowserShell(display: Display, url: String) {
    val shell2 = Shell(display)
    val b2 = Browser(shell2, SWT.NONE)
    init {
        shell2.layout = FillLayout()
        b2.url = url
        shell2.location = Point(0,0)
        shell2.size = Point(300,500)
        shell2.open()
    }
}

fun main() {
    var sss = ""
    val bundle = org.eclipse.swt.internal.cocoa.NSBundle.mainBundle()
    val infodict = bundle.infoDictionary()
//    i.setValue(, NSString.stringWith("NSAppTransportSecurity"))
    sss += "keysn=" + infodict.count() + "\n"

//    val xxx = NSString.stringWith("nnnname")
//    infodict.setValue(xxx, NSString.stringWith("CFBundleName"))

    // THIS should change it like for python here: https://stackoverflow.com/questions/34613902/how-to-disable-app-transport-security-for-a-script
    // TODO it doesn't work, possibly because I make the infodict larger, need to extend it somehow?
    val yyy = NSDictionary.dictionaryWithObject(NSString.stringWith("YES"), NSString.stringWith("NSAllowsArbitraryLoads"))
    infodict.setValue(yyy, NSString.stringWith("NSAppTransportSecurity"))


    sss += "keysn=" + infodict.count() + "\n"

    val zz = bundle.objectForInfoDictionaryKey(NSString.stringWith("NSAppTransportSecurity"))
    val dd = NSDictionary(zz.id)
    val ee = dd.objectEnumerator()
    var oo = ee.nextObject()
    while (oo != null) {
        sss += "   oo: ${NSString(oo).string}\n"
        oo = ee.nextObject()
    }

    val allk = infodict.allKeys()
    for (iii in 0 until infodict.count()) {
        val o = allk.objectAtIndex(iii)
        sss += "n: ${NSString(o.id).string}\n"
        sss += " v: ${NSString(infodict.objectForKey(allk.objectAtIndex(iii))).string}\n"

    }

//    println("CCC: " + infodict.valueForKey(NSString.stringWith("NSAppTransportSecurity")))
//    val o = infodict.objectForKey(NSString.stringWith("NSAppTransportSecurity"))
//    println("CCC: " + o.toString())

    Device.DEBUG = true
    Display.setAppName("washboard-swt")
//    val display = Display.getDefault()
    val display = Display()
    Display.setAppName("washboard-swt") // need twice https://stackoverflow.com/a/45088431
    val shell = Shell(display)
    shell.text = "WashboardSwt: " + infodict.valueForKey(NSString.stringWith("NSAppTransportSecurity"))
    val t = Text(shell, SWT.BORDER)
    shell.layout = FillLayout()
    t.text = sss

//    shell.text = "SWT Browser - Documentation Viewer"
//    shell.layout = GridLayout()
//    val compTools = Composite(shell, SWT.NONE)
//    var data = GridData(GridData.FILL_HORIZONTAL)
//    compTools.layoutData = data
//    compTools.layout = GridLayout(2, false)
//    val tocBar = ToolBar(compTools, SWT.NONE)
//    val openItem = ToolItem(tocBar, SWT.PUSH)
//    openItem.text = "Browse"
//    val navBar = ToolBar(compTools, SWT.NONE)
//    navBar.layoutData = GridData(GridData.FILL_HORIZONTAL or GridData.HORIZONTAL_ALIGN_END)
//    val back = ToolItem(navBar, SWT.PUSH)
//    back.text = "Back"
//    back.isEnabled = false
//    val forward = ToolItem(navBar, SWT.PUSH)
//    forward.text = "Forward"
//    forward.isEnabled = false
//    val comp = Composite(shell, SWT.NONE)
//    data = GridData(GridData.FILL_BOTH)
//    comp.layoutData = data
//    comp.layout = FillLayout()
//    val form = SashForm(comp, SWT.HORIZONTAL)
//    form.layout = FillLayout()
//
//    val browser = Browser(form, SWT.NONE)
//


//    shell.layout = FillLayout()
    val browser = Browser(shell, SWT.NONE)
    browser.url = "http://nix:8083/mobile"
//    browser.url = "https://quphotonics.org"
//    browser.url = "https://self-signed.badssl.com/" // also doesn't work because certificate can't be stored???!! bug
        // BUT after visiting with safari and accepting certificate, it works!

    shell.location = Point(500,500)

//    val bss = mutableListOf<BrowserShell>()
//    bss += BrowserShell(display, "https://quphotonics.org")
//    bss += BrowserShell(display, "https://apod.nasa.gov/apod/astropix.html")
//    bss += BrowserShell(display, "https://quphotonics.org/weer/")
//    bss += BrowserShell(display, "file:////Users/wolle/Library/Application Support/WashboardApp/widgets/weer/index.html")
//    bss += BrowserShell(display, "file:////Users/wolle/Library/Application Support/WashboardApp/widgets/weatherwidget.io/index.html")
//    bss += BrowserShell(display, "http://nix:8083/mobile")

    shell.open()

    while (!shell.isDisposed) {
        if (!display.readAndDispatch()) display.sleep()
    }
    display.dispose()
}
