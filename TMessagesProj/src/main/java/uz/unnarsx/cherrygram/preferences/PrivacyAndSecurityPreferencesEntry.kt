package uz.unnarsx.cherrygram.preferences

import android.app.Activity
import android.content.SharedPreferences
import android.os.Environment
import android.widget.Toast
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import uz.unnarsx.cherrygram.CherrygramConfig
import uz.unnarsx.cherrygram.helpers.AppRestartHelper
import uz.unnarsx.cherrygram.ui.dialogs.DeleteAccountDialog
import uz.unnarsx.cherrygram.ui.tgkit.preference.category
import uz.unnarsx.cherrygram.ui.tgkit.preference.contract
import uz.unnarsx.cherrygram.ui.tgkit.preference.switch
import uz.unnarsx.cherrygram.ui.tgkit.preference.textIcon
import uz.unnarsx.cherrygram.ui.tgkit.preference.tgKitScreen
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitTextIconRow
import java.io.File

class PrivacyAndSecurityPreferencesEntry : BasePreferencesEntry {
    val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("SP_Category_PrivacyAndSecurity", R.string.SP_Category_PrivacyAndSecurity)) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(CherrygramConfig.listener)
        category(LocaleController.getString("SP_Header_Privacy", R.string.SP_Header_Privacy)) {
            switch {
                title = LocaleController.getString("AS_NoProxyPromo", R.string.SP_NoProxyPromo)

                contract({
                    return@contract CherrygramConfig.hideProxySponsor
                }) {
                    CherrygramConfig.hideProxySponsor = it
                    bf.parentActivity.recreate()
                }
            }
            switch {
                title = LocaleController.getString("SP_GoogleAnalytics", R.string.SP_GoogleAnalytics)
                description = LocaleController.getString("SP_GoogleAnalytics_Desc", R.string.SP_GoogleAnalytics_Desc)

                contract({
                    return@contract CherrygramConfig.googleAnalytics
                }) {
                    CherrygramConfig.googleAnalytics = it
                    AppRestartHelper.createRestartBulletin(bf)
                }
            }
            switch {
                title = LocaleController.getString("SP_AppCenterAnalytics", R.string.SP_AppCenterAnalytics)
                description = LocaleController.getString("SP_AppCenterAnalytics_Desc", R.string.SP_AppCenterAnalytics_Desc)

                contract({
                    return@contract CherrygramConfig.appcenterAnalytics
                }) {
                    CherrygramConfig.appcenterAnalytics = it
                    AppRestartHelper.createRestartBulletin(bf)
                }
            }

            textIcon {
                title = LocaleController.getString("SP_CleanOld", R.string.SP_CleanOld)

                listener = TGKitTextIconRow.TGTIListener {
                    val file = File(Environment.getExternalStorageDirectory(), "Telegram")
                    file.deleteRecursively()
                    Toast.makeText(bf.parentActivity, LocaleController.getString("SP_RemovedS", R.string.SP_RemovedS), Toast.LENGTH_SHORT).show()
                }
            }
        }

        category(LocaleController.getString("SP_Category_Account", R.string.SP_Category_Account)) {
            textIcon {
                title = LocaleController.getString("SP_DeleteAccount", R.string.SP_DeleteAccount)
                icon = R.drawable.msg_delete
                listener = TGKitTextIconRow.TGTIListener {
                    DeleteAccountDialog.showDeleteAccountDialog(bf)
                }
            }
        }

    }
}