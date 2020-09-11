package com.example.android_rc

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager


object RuntimePermissionUtils {

    fun hasSelfPermissions(context: Context, vararg permissions: String): Boolean {
        for (permission in permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun checkGrantResults(vararg grantResults: Int): Boolean {
        require(grantResults.isNotEmpty()) { "grantResults is empty" }
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun shouldShowRequestPermissionRationale(activity: Activity, permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    fun showAlertDialog(fragmentManager: FragmentManager, permission: String) {
        val dialog: RuntimePermissionAlertDialogFragment =
            RuntimePermissionAlertDialogFragment.newInstance(
                permission
            )
        dialog.show(fragmentManager, RuntimePermissionAlertDialogFragment.TAG)
    }

    class RuntimePermissionAlertDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val permission = arguments?.getString(ARG_PERMISSION_NAME)

            val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                .setMessage("$permission permission have not been granted, please grant it from the Permissions section of the App info.")
                .setPositiveButton("App info", DialogInterface.OnClickListener { _, _ ->
                    dismiss()
                    // システムのアプリ設定画面
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity?.packageName)
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity?.startActivity(intent)
                })
                .setNegativeButton(
                    "cancel",
                    DialogInterface.OnClickListener { _, _ -> dismiss() })
            return dialogBuilder.create()
        }

        companion object {
            const val TAG = "RuntimePermissionAlertDialogFragment"
            private const val ARG_PERMISSION_NAME = "permissionName"

            fun newInstance(permission: String): RuntimePermissionAlertDialogFragment {
                val fragment = RuntimePermissionAlertDialogFragment()
                val args = Bundle()
                args.putString(ARG_PERMISSION_NAME, permission)
                fragment.arguments = args
                return fragment
            }
        }
    }

}