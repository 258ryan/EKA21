package com.github.eka2l1.belle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.belleui.kit.Act
import com.belleui.kit.AlphaIndex
import com.belleui.kit.Belle
import com.belleui.kit.BelleActivity
import com.belleui.kit.BelleShell
import com.belleui.kit.ListDialog
import com.belleui.kit.ListState
import com.belleui.kit.MenuItem
import com.belleui.kit.QueryDialog
import com.belleui.kit.Row
import com.belleui.kit.Screen
import com.belleui.kit.Ui
import com.github.eka2l1.applist.AppItem
import com.github.eka2l1.config.ProfileModel
import com.github.eka2l1.config.ProfilesManager
import com.github.eka2l1.emu.Emulator
import com.github.eka2l1.settings.AppDataStore
import com.github.eka2l1.settings.KeyMapper
import com.github.eka2l1.util.FileUtils
import java.io.File

/**
 * EKA21's own screens, built with the Belle kit: the Symbian app list, installing apps and devices, per-app settings
 * and general settings. Opening an app goes straight into it (see [BelleAppsProvider.launchIntent]).
 */
class BelleMainActivity : BelleActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private var pickCb: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Emulator.initializePath(this)
        super.onCreate(savedInstanceState)
        if (FileUtils.isExternalStorageLegacy() && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 7)
        }
    }

    override fun createRoot(shell: BelleShell): Screen {
        try { Emulator.initializeFolders() } catch (_: Throwable) { }
        return AppsScreen(this)
    }

    fun post(r: () -> Unit) { ui.post(r) }

    /** Runs [work] off the UI thread, then [done] on it. */
    fun <T> bg(work: () -> T, done: (Result<T>) -> Unit) {
        Thread {
            val r = runCatching(work)
            ui.post { done(r) }
        }.start()
    }

    /** Opens EKA21's file or folder picker; [cb] gets the path. */
    fun pick(dir: Boolean, ext: Array<String>, cb: (String) -> Unit) {
        pickCb = cb
        startActivityForResult(
            Intent(this, PickerHostActivity::class.java)
                .putExtra(PickerHostActivity.EXTRA_DIR, dir)
                .putExtra(PickerHostActivity.EXTRA_EXT, ext),
            11,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == RESULT_OK) data?.getStringExtra(PickerHostActivity.EXTRA_PATH)?.let { pickCb?.invoke(it) }
    }

    fun launch(uid: Long, name: String) {
        val code = try {
            val codes = Emulator.getDeviceFirmwareCodes()
            val id = Emulator.getCurrentDevice()
            if (id in codes.indices) codes[id] else null
        } catch (_: Throwable) { null }
        startActivity(BelleAppsProvider.launchIntent(this, uid, name, code))
    }

    /** Opens the screen again from scratch, as the stock app did after installing something. */
    fun reloadApps() {
        (shell.screen as? AppsScreen)?.reload()
    }
}

// ================================================================================================
// apps
// ================================================================================================

class AppsScreen(private val act: BelleMainActivity) : Screen() {
    override val title get() = "Symbian apps"
    private val list = ListState()
    private val alpha = AlphaIndex()
    private var apps: List<AppItem> = emptyList()
    private var loaded = false
    private var error: String? = null
    private val icons = HashMap<Long, Bitmap>()

    private fun letter(a: AppItem) = a.title.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

    override fun onShow() {
        if (!loaded) reload()
    }

    fun reload() {
        loaded = true
        act.bg({ Emulator.getAppsList().blockingGet().sortedBy { it.title.lowercase() } }) { r ->
            r.onSuccess {
                apps = it
                error = null
                list.sectionOf = { i -> if (i == 0 || letter(apps[i]) != letter(apps[i - 1])) letter(apps[i]) else null }
            }
            r.onFailure { error = "No device installed yet" }
            shell.invalidate()
        }
    }

    override fun menu() = listOf(
        MenuItem("Open") { openSel() },
        MenuItem("App settings") { sel()?.let { shell.push(AppSettingsScreen(act, it.uid, it.title)) } },
        MenuItem("Install app") { installApp() },
        MenuItem("Devices") { shell.push(DevicesScreen(act)) },
        MenuItem("Settings") { shell.push(GeneralScreen(act)) },
        MenuItem("Uninstall") { uninstall() },
    )

    private fun sel() = apps.getOrNull(list.sel)

    private fun openSel() {
        sel()?.let { act.launch(it.uid, it.title) }
    }

    private fun installApp() {
        act.pick(false, arrayOf(".sis", ".sisx")) { path ->
            shell.toast("Installing...")
            act.bg({ Emulator.installApp(path) }) { r ->
                if (r.getOrNull() == 0) {
                    shell.toast("Installed")
                    reload()
                } else shell.toast("Install failed")
            }
        }
    }

    private fun uninstall() {
        val a = sel() ?: return
        shell.showOverlay(QueryDialog("Uninstall ${a.title}?", "Uninstall", "Cancel") {
            act.bg({ Emulator.uninstallApp(a.uid.toInt()) }) { reload() }
        })
    }

    private fun icon(a: AppItem): Bitmap? = a.icon

    override fun draw(c: Canvas, ui: Ui, r: RectF) {
        if (apps.isEmpty()) {
            val msg = error ?: if (loaded) "No apps yet. Menu > Install app" else "Loading..."
            ui.text(c, msg, r.centerX(), ui.baselineFor(r.centerY(), 20f * ui.sc), 20f * ui.sc, Belle.TEXT_MID, Paint.Align.CENTER, maxW = r.width() - 40f * ui.sc)
            return
        }
        list.draw(c, ui, r, apps.size) { i -> Row(apps[i].title, icon = icon(apps[i]), drill = true) }
        alpha.set(apps.indices.mapNotNull { list.sectionOf?.invoke(it) })
        alpha.draw(c, ui, r, apps.getOrNull(list.sel)?.let { letter(it) })
    }

    private fun area() = RectF(shell.contentLeft, shell.contentTop, shell.contentRight, shell.contentBottom)

    override fun onAct(a: Act): Boolean {
        if (apps.isEmpty()) return false
        return when (a) {
            Act.OK -> { openSel(); true }
            Act.RIGHT -> { sel()?.let { shell.push(AppSettingsScreen(act, it.uid, it.title)) }; true }
            else -> list.act(a, apps.size, list.visible(shell.ui, shell.contentBottom - shell.contentTop))
        }
    }

    override fun onTap(x: Float, y: Float): Boolean {
        val l = alpha.hit(shell.ui, area(), x, y)
        if (l != null) {
            list.jumpTo(l, apps.size)
            return true
        }
        val i = list.hit(shell.ui, area(), y, apps.size)
        if (i < 0) return false
        list.sel = i
        openSel()
        return true
    }

    override fun onLongPress(x: Float, y: Float) {
        val i = list.hit(shell.ui, area(), y, apps.size)
        if (i >= 0) {
            list.sel = i
            shell.push(AppSettingsScreen(act, apps[i].uid, apps[i].title))
        }
    }

    override fun onScroll(dy: Float) {
        list.scrollBy(if (dy > 0) 1 else -1, apps.size, list.visible(shell.ui, shell.contentBottom - shell.contentTop))
    }
}

// ================================================================================================
// a plain list of rows with an action each
// ================================================================================================

abstract class ListScreen(protected val act: BelleMainActivity) : Screen() {
    protected val list = ListState()
    abstract fun rows(): List<Row>
    abstract fun open(i: Int)

    private fun area() = RectF(shell.contentLeft, shell.contentTop, shell.contentRight, shell.contentBottom)

    override fun draw(c: Canvas, ui: Ui, r: RectF) {
        val rs = rows()
        list.draw(c, ui, r, rs.size) { rs[it] }
    }

    override fun onAct(a: Act): Boolean {
        val n = rows().size
        if (n == 0) return false
        if (a == Act.OK || a == Act.RIGHT) {
            open(list.sel.coerceIn(0, n - 1))
            shell.invalidate()
            return true
        }
        return list.act(a, n, list.visible(shell.ui, shell.contentBottom - shell.contentTop))
    }

    override fun onTap(x: Float, y: Float): Boolean {
        val i = list.hit(shell.ui, area(), y, rows().size)
        if (i < 0) return false
        list.sel = i
        open(i)
        shell.invalidate()
        return true
    }

    override fun onScroll(dy: Float) {
        list.scrollBy(if (dy > 0) 1 else -1, rows().size, list.visible(shell.ui, shell.contentBottom - shell.contentTop))
    }
}

// ================================================================================================
// per-app settings
// ================================================================================================

class AppSettingsScreen(act: BelleMainActivity, private val uid: Long, private val name: String) : ListScreen(act) {
    override val title get() = name

    private val dir = File(Emulator.getConfigsDir(), java.lang.Long.toHexString(uid).uppercase())
    private val p: ProfileModel = ProfilesManager.loadConfigOrDefault(dir, null).also {
        if (it.isNew) BelleDefaults.apply(it)
    }

    private val orient = listOf("Device default", "Auto-rotate", "Portrait", "Landscape")
    private val scale = listOf("Original size", "Fit screen", "Fill screen")

    private fun scaleIndex() = when (p.screenScaleType) { 2 -> 2; 0 -> 0; else -> 1 }

    private fun save() {
        dir.mkdirs()
        ProfilesManager.saveConfig(p)
        try { Emulator.updateAppSetting(uid.toInt()) } catch (_: Throwable) { }
    }

    override fun rows() = listOf(
        Row("Orientation", right = orient.getOrElse(p.orientation) { orient[0] }, drill = true),
        Row("Screen size", right = scale[scaleIndex()], drill = true),
        Row("On-screen keyboard", right = if (p.showKeyboard) "On" else "Off", drill = true),
        Row("Touch screen", right = if (p.touchInput) "On" else "Off", drill = true),
        Row("Controller keys", subtitle = "Back to the Belle layout", drill = true),
        Row("Delete saved settings", subtitle = "Start over with the defaults", drill = true),
    )

    override fun open(i: Int) {
        when (i) {
            0 -> shell.showOverlay(ListDialog("Orientation", orient, p.orientation) { p.orientation = it; save() })
            1 -> shell.showOverlay(ListDialog("Screen size", scale, scaleIndex()) { p.screenScaleType = intArrayOf(0, 1, 2)[it]; save() })
            2 -> { p.showKeyboard = !p.showKeyboard; save() }
            3 -> { p.touchInput = !p.touchInput; save() }
            4 -> { p.keyMappings = KeyMapper.getDefaultKeyMap(); save(); shell.toast("Controller keys reset") }
            5 -> shell.showOverlay(QueryDialog("Delete the saved settings of $name?", "Delete", "Cancel") {
                File(dir.path + Emulator.APP_CONFIG_FILE).delete()
                shell.toast("Deleted")
                shell.pop()
            })
        }
    }
}

// ================================================================================================
// devices
// ================================================================================================

class DevicesScreen(act: BelleMainActivity) : ListScreen(act) {
    override val title get() = "Devices"
    private var devices: List<String> = emptyList()

    override fun onShow() = refresh()

    private fun refresh() {
        devices = try { Emulator.getDevices().toList() } catch (_: Throwable) { emptyList() }
        shell.invalidate()
    }

    override fun menu() = listOf(
        MenuItem("Install from firmware (VPL)") { installVpl() },
        MenuItem("Install from ROM + RPKG") { installRom() },
        MenuItem("Rescan") { act.bg({ Emulator.rescanDevices() }) { refresh() } },
        MenuItem("Delete device") { deleteSel() },
    )

    override fun rows(): List<Row> {
        val cur = try { Emulator.getCurrentDevice() } catch (_: Throwable) { -1 }
        if (devices.isEmpty()) return listOf(Row("No device installed", subtitle = "Menu > Install"))
        return devices.mapIndexed { i, d -> Row(d, subtitle = if (i == cur) "In use" else null, check = i == cur) }
    }

    override fun open(i: Int) {
        if (devices.isEmpty()) return
        Emulator.setCurrentDevice(i, false)
        shell.toast("Using ${devices[i]}")
    }

    private fun deleteSel() {
        val i = list.sel
        val name = devices.getOrNull(i) ?: return
        shell.showOverlay(QueryDialog("Delete $name?", "Delete", "Cancel") {
            act.bg({ Emulator.deleteDevice(i) }) { refresh() }
        })
    }

    private fun installVpl() {
        act.pick(false, arrayOf(".VPL", ".vpl")) { path ->
            shell.toast("Installing device...")
            act.bg({ Emulator.installDevice("", path, false) }) { r ->
                shell.toast(if (r.getOrNull() == 0) "Device installed" else "Install failed (${r.getOrNull()})")
                refresh()
            }
        }
    }

    private fun installRom() {
        act.pick(false, arrayOf(".ROM", ".rom")) { rom ->
            val needs = try { Emulator.doesRomNeedRPKG(rom) } catch (_: Throwable) { true }
            val go = { rpkg: String ->
                shell.toast("Installing device...")
                act.bg({ Emulator.installDevice(rpkg, rom, rpkg.isNotEmpty()) }) { r ->
                    shell.toast(if (r.getOrNull() == 0) "Device installed" else "Install failed (${r.getOrNull()})")
                    refresh()
                }
            }
            if (needs) act.pick(false, arrayOf(".RPKG", ".rpkg")) { go(it) } else go("")
        }
    }
}

// ================================================================================================
// general settings
// ================================================================================================

class GeneralScreen(act: BelleMainActivity) : ListScreen(act) {
    override val title get() = "Settings"
    private val store get() = AppDataStore.getAndroidStore()

    private fun flag(key: String, def: Boolean) = store.getBoolean(key, def)

    private fun toggle(key: String, def: Boolean) {
        store.putBoolean(key, !flag(key, def))
        store.save()
    }

    override fun rows() = listOf(
        Row("Keep screen on", right = if (flag(com.github.eka2l1.emu.Constants.PREF_KEEP_SCREEN, false)) "On" else "Off", drill = true),
        Row("Vibration", right = if (flag(com.github.eka2l1.emu.Constants.PREF_VIBRATION, true)) "On" else "Off", drill = true),
        Row("Memory card", subtitle = "Pick the folder used as the memory card", drill = true),
        Row("Devices", drill = true),
        Row("About", subtitle = "EKA21 with Belle UI", drill = true),
    )

    override fun open(i: Int) {
        when (i) {
            0 -> toggle(com.github.eka2l1.emu.Constants.PREF_KEEP_SCREEN, false)
            1 -> toggle(com.github.eka2l1.emu.Constants.PREF_VIBRATION, true)
            2 -> act.pick(true, emptyArray()) { path ->
                Emulator.mountSdCard(path)
                shell.toast("Memory card set")
            }
            3 -> shell.push(DevicesScreen(act))
            4 -> shell.toast("EKA2L1 emulator, Belle UI by 258ryan")
        }
    }
}
