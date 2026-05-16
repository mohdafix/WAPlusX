package com.wmods.wppenhacer.xposed.core.components;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AlertDialogWpp {


    private static Method getAlertDialog;
    private static Method setItemsMethod;
    private static boolean isAvailable;
    private static Method setMessageMethod;
    private static Method setNegativeButtonMethod;
    private static Method setPositiveButtonMethod;
    private static Method setMultiChoiceItemsMethod;
    private final Context mContext;
    private AlertDialog.Builder mAlertDialog;
    private Object mAlertDialogWpp;
    private Dialog mCreate;

    public static void initDialog(ClassLoader loader) {
        try {
            getAlertDialog = Unobfuscator.loadMaterialAlertDialog(loader);
            Class<?> alertDialogClass = getAlertDialog.getReturnType();
            setItemsMethod = ReflectionUtils.findMethodUsingFilter(alertDialogClass, method -> method.getParameterCount() == 2 && method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && method.getParameterTypes()[1].equals(CharSequence[].class));
            setMultiChoiceItemsMethod = ReflectionUtils.findMethodUsingFilter(alertDialogClass, method -> method.getParameterCount() == 3 && method.getParameterTypes()[0].equals(DialogInterface.OnMultiChoiceClickListener.class) && method.getParameterTypes()[1].equals(CharSequence[].class));
            setMessageMethod = ReflectionUtils.findMethodUsingFilter(alertDialogClass, method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(CharSequence.class));
            var buttons = ReflectionUtils.findAllMethodsUsingFilter(alertDialogClass, method -> method.getParameterCount() == 2 && method.getParameterTypes()[0].equals(DialogInterface.OnClickListener.class) && method.getParameterTypes()[1].equals(CharSequence.class));
            setNegativeButtonMethod = buttons[0];
            setPositiveButtonMethod = buttons[2];
            isAvailable = true;
        } catch (Throwable e) {
            isAvailable = false;
            XposedBridge.log(e);
            Utils.showToast("Failed to load MaterialAlertDialog", Toast.LENGTH_SHORT);
        }
    }

    public AlertDialogWpp(Context context) {
        mContext = context;
        XposedBridge.log("[AlertDialogWpp] Constructor called with context: " + (context != null ? context.getClass().getName() : "null"));
        if (!isSystemDialog()) {
            try {
                mAlertDialogWpp = getAlertDialog.invoke(null, context);
                // Remove Default Message
                setMessage(null);
                return;
            } catch (Exception e) {
                XposedBridge.log("[AlertDialogWpp] Failed to create MaterialAlertDialog: " + e.getMessage());
            }
        }
        XposedBridge.log("[AlertDialogWpp] Falling back to System AlertDialog.Builder");
        mAlertDialog = new AlertDialog.Builder(context);
        mAlertDialogWpp = null;
    }

    public Context getContext() {
        return mContext;
    }

    public static boolean isSystemDialog() {
        return !isAvailable;
    }

    public AlertDialogWpp setTitle(String title) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setTitle(title);
            return this;
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title);
        return this;
    }

    public AlertDialogWpp setTitle(int title) {
        if (title == 0) return this;
        if (mAlertDialogWpp == null) {
            mAlertDialog.setTitle(title);
            return this;
        }
        try {
            XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", getContext().getString(title));
        } catch (Exception e) {
            XposedBridge.log("[AlertDialogWpp] Failed to set title: " + e.getMessage());
        }
        return this;
    }

    public AlertDialogWpp setMessage(CharSequence message) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setMessage(message);
            return this;
        }
        try {
            setMessageMethod.invoke(mAlertDialogWpp, message);
        } catch (Exception ignored) {
        }
        return this;
    }

    public AlertDialogWpp setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setItems(items, listener);
            return this;
        }
        try {
            setItemsMethod.invoke(mAlertDialogWpp, listener, items);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return this;
    }


    public AlertDialogWpp setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setMultiChoiceItems(items, checkedItems, listener);
            return this;
        }
        try {
            setMultiChoiceItemsMethod.invoke(mAlertDialogWpp, listener, items, checkedItems);
        } catch (Exception ignored) {
        }
        return this;
    }

    public AlertDialogWpp setNegativeButton(int text, DialogInterface.OnClickListener listener) {
        if (text == 0) return this;
        return setNegativeButton(getContext().getString(text), listener);
    }

    public AlertDialogWpp setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setNegativeButton(text, listener);
            return this;
        }
        try {
            setNegativeButtonMethod.invoke(mAlertDialogWpp, listener, text);
        } catch (Exception ignored) {
        }
        return this;
    }

    public AlertDialogWpp setPositiveButton(int text, DialogInterface.OnClickListener listener) {
        if (text == 0) return this;
        return setPositiveButton(getContext().getString(text), listener);
    }

    public AlertDialogWpp setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setPositiveButton(text, listener);
            return this;
        }
        try {
            setPositiveButtonMethod.invoke(mAlertDialogWpp, listener, text);
        } catch (Exception ignored) {
        }
        return this;
    }

    public AlertDialogWpp setView(View view) {
        if (mAlertDialogWpp == null) {
            mAlertDialog.setView(view);
            return this;
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setView", view);
        return this;
    }

    private boolean mCancelable = true;

    public AlertDialogWpp setCancelable(boolean cancelable) {
        this.mCancelable = cancelable;
        if (mAlertDialogWpp == null) {
            mAlertDialog.setCancelable(cancelable);
        }
        return this;
    }

    public Dialog create() {
        if (mCreate != null) return mCreate;
        if (mAlertDialogWpp == null) {
            mCreate = mAlertDialog.create();
        } else {
            mCreate = (Dialog) XposedHelpers.callMethod(mAlertDialogWpp, "create");
        }
        if (!mCancelable) {
            mCreate.setCancelable(false);
        }
        return mCreate;
    }

    public void dismiss() {
        if (mCreate == null) return;
        mCreate.dismiss();
    }

    public void show() {
        if (mContext instanceof Activity) {
            Activity activity = (Activity) mContext;
            activity.runOnUiThread(() -> {
                try {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        XposedBridge.log("[AlertDialogWpp] Activity is finishing/destroyed, cannot show dialog");
                        return;
                    }
                    if (mAlertDialogWpp == null) {
                        XposedBridge.log("[AlertDialogWpp] Showing System Dialog");
                        mAlertDialog.show();
                    } else {
                        XposedBridge.log("[AlertDialogWpp] Showing Material Dialog");
                        create().show();
                    }
                } catch (Exception e) {
                    XposedBridge.log("[AlertDialogWpp] Error showing dialog: " + e.getMessage());
                    // Final fallback to Toast if dialog fails
                    Toast.makeText(mContext, "Dialog Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            XposedBridge.log("[AlertDialogWpp] Non-activity context, cannot show dialog safely");
        }
    }

}
