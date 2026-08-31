package nl.markmaaktmedia.markmaaktai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import nl.markmaaktmedia.markmaaktai.R

/**
 * Every icon in the app, in one place.
 *
 * These are Material Symbols in the Rounded style, shipped as vector drawables
 * rather than pulled from `Icons.Rounded`. The bundled Compose set is the older
 * Material Icons drawing, which has squarer joins and thinner strokes, and next to a
 * rounded typeface it reads as a different app's icons. Symbols Rounded matches the
 * terminals of the type, which is what makes the whole thing look drawn by one hand.
 *
 * A side effect worth having: nothing pulls in `material-icons-extended`, which is a
 * few thousand vectors compiled into the APK to use thirty of them.
 */
object MarkIcons {

    // Navigation
    val Chat: Painter @Composable get() = painterResource(R.drawable.sym_chat_bubble)
    val ChatFilled: Painter @Composable get() = painterResource(R.drawable.sym_chat_bubble_filled)
    val Shots: Painter @Composable get() = painterResource(R.drawable.sym_imagesmode)
    val ShotsFilled: Painter @Composable get() = painterResource(R.drawable.sym_imagesmode_filled)
    val Digest: Painter @Composable get() = painterResource(R.drawable.sym_inbox)
    val DigestFilled: Painter @Composable get() = painterResource(R.drawable.sym_inbox_filled)
    val Settings: Painter @Composable get() = painterResource(R.drawable.sym_tune)
    val SettingsFilled: Painter @Composable get() = painterResource(R.drawable.sym_tune_filled)

    // Chrome
    val Back: Painter @Composable get() = painterResource(R.drawable.sym_arrow_back)
    val Close: Painter @Composable get() = painterResource(R.drawable.sym_close)
    val More: Painter @Composable get() = painterResource(R.drawable.sym_more_vert)
    val ChevronRight: Painter @Composable get() = painterResource(R.drawable.sym_chevron_right)
    val ChevronDown: Painter @Composable get() = painterResource(R.drawable.sym_keyboard_arrow_down)
    val Search: Painter @Composable get() = painterResource(R.drawable.sym_search)
    val Refresh: Painter @Composable get() = painterResource(R.drawable.sym_refresh)
    val Delete: Painter @Composable get() = painterResource(R.drawable.sym_delete)
    val Add: Painter @Composable get() = painterResource(R.drawable.sym_add)
    val Check: Painter @Composable get() = painterResource(R.drawable.sym_check)
    val Help: Painter @Composable get() = painterResource(R.drawable.sym_question_mark)
    val Info: Painter @Composable get() = painterResource(R.drawable.sym_info)
    val Error: Painter @Composable get() = painterResource(R.drawable.sym_error)
    val OpenInNew: Painter @Composable get() = painterResource(R.drawable.sym_open_in_new)

    // Composer
    val Send: Painter @Composable get() = painterResource(R.drawable.sym_arrow_upward)
    val Stop: Painter @Composable get() = painterResource(R.drawable.sym_stop)
    val Mic: Painter @Composable get() = painterResource(R.drawable.sym_mic)
    val AddPhoto: Painter @Composable get() = painterResource(R.drawable.sym_add_photo_alternate)
    val Camera: Painter @Composable get() = painterResource(R.drawable.sym_photo_camera)
    val Web: Painter @Composable get() = painterResource(R.drawable.sym_public)
    val Phone: Painter @Composable get() = painterResource(R.drawable.sym_smartphone)
    val Copy: Painter @Composable get() = painterResource(R.drawable.sym_content_copy)
    val Link: Painter @Composable get() = painterResource(R.drawable.sym_link)
    val History: Painter @Composable get() = painterResource(R.drawable.sym_history)
    val Pin: Painter @Composable get() = painterResource(R.drawable.sym_keep)
    val PinOff: Painter @Composable get() = painterResource(R.drawable.sym_keep_off)
    val NewChat: Painter @Composable get() = painterResource(R.drawable.sym_add_comment)
    val Sparkle: Painter @Composable get() = painterResource(R.drawable.sym_auto_awesome)
    val SparkleFilled: Painter @Composable get() = painterResource(R.drawable.sym_auto_awesome_filled)

    // Digest and screenshots
    val Urgent: Painter @Composable get() = painterResource(R.drawable.sym_priority_high)
    val Calendar: Painter @Composable get() = painterResource(R.drawable.sym_calendar_month)
    val Star: Painter @Composable get() = painterResource(R.drawable.sym_star)
    val StarFilled: Painter @Composable get() = painterResource(R.drawable.sym_star_filled)
    val Image: Painter @Composable get() = painterResource(R.drawable.sym_image)
    val Mail: Painter @Composable get() = painterResource(R.drawable.sym_mail)
    val Forum: Painter @Composable get() = painterResource(R.drawable.sym_forum)
    val Receipt: Painter @Composable get() = painterResource(R.drawable.sym_receipt_long)
    val Delivery: Painter @Composable get() = painterResource(R.drawable.sym_local_shipping)
    val Travel: Painter @Composable get() = painterResource(R.drawable.sym_travel_explore)
    val Recipe: Painter @Composable get() = painterResource(R.drawable.sym_restaurant)
    val Document: Painter @Composable get() = painterResource(R.drawable.sym_description)

    // Settings sections
    val Palette: Painter @Composable get() = painterResource(R.drawable.sym_palette)
    val DarkMode: Painter @Composable get() = painterResource(R.drawable.sym_dark_mode)
    val Model: Painter @Composable get() = painterResource(R.drawable.sym_memory)
    val Notifications: Painter @Composable get() = painterResource(R.drawable.sym_notifications_active)
    val Battery: Painter @Composable get() = painterResource(R.drawable.sym_battery_full)
    val Assistant: Painter @Composable get() = painterResource(R.drawable.sym_touch_app)
    val Key: Painter @Composable get() = painterResource(R.drawable.sym_key)
    val Code: Painter @Composable get() = painterResource(R.drawable.sym_code)
    val Update: Painter @Composable get() = painterResource(R.drawable.sym_system_update)
    val Download: Painter @Composable get() = painterResource(R.drawable.sym_download)
    val Folder: Painter @Composable get() = painterResource(R.drawable.sym_folder_open)
    val Shield: Painter @Composable get() = painterResource(R.drawable.sym_shield)
    val Idea: Painter @Composable get() = painterResource(R.drawable.sym_lightbulb)
    val Sync: Painter @Composable get() = painterResource(R.drawable.sym_sync)
    val CheckCircle: Painter @Composable get() = painterResource(R.drawable.sym_check_circle)
}
