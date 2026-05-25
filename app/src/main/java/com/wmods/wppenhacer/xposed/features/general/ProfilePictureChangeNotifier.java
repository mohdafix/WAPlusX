package com.wmods.wppenhacer.xposed.features.general;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;
import com.wmods.wppenhacer.xposed.features.listeners.ContactItemListener;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ProfilePictureChangeNotifier extends Feature {

    private static final String PREF_KEY = "profile_picture_change_toast";
    private static final String HASH_PREFS = "wppenhancer_profile_hashes";
    private android.content.SharedPreferences hashPrefs;

    public ProfilePictureChangeNotifier(@NonNull ClassLoader classLoader,
                                        @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        int notificationOption = getNotificationOption();
        if (notificationOption == 0) {
            return; // Disabled
        }

        // Hook into contact item binding to detect profile picture changes
        ContactItemListener.contactListeners.add(new ProfilePictureChangeListener());

        // PROACTIVE REAL-TIME HOOK
        try {
            if (notificationOption > 0) {
                Method notifyMethod = Unobfuscator.loadNotifyUpdatePhotoMethod(classLoader);
                if (notifyMethod != null) {
                    XposedBridge.log("WaEnhancer: Hooking notifyUpdatePhotoMethod: " + notifyMethod.getName());
                    XposedBridge.hookMethod(notifyMethod, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                if (param.args == null || param.args.length == 0) return;
                                
                                // Extract JID from notification stanza using reflection
                                Object stanzaArg = param.args[0];
                                if (stanzaArg == null) return;
                                
                                // Find method to get element at index 0
                                Method getElementMethod = com.wmods.wppenhacer.xposed.utils.ReflectionUtils.findMethodUsingFilterIfExists(
                                    stanzaArg.getClass(),
                                    method -> method.getParameterCount() == 1 
                                        && method.getParameterTypes()[0] == Integer.TYPE
                                );
                                
                                if (getElementMethod == null) {
                                    XposedBridge.log("WaEnhancer: Could not find getElement method");
                                    return;
                                }
                                
                                Object element = getElementMethod.invoke(stanzaArg, 0);
                                if (element == null) return;
                                
                                // Find method to get attribute by type and name
                                Method getAttributeMethod = com.wmods.wppenhacer.xposed.utils.ReflectionUtils.findMethodUsingFilterIfExists(
                                    element.getClass(),
                                    method -> method.getParameterCount() == 2 
                                        && method.getParameterTypes()[0] == Class.class 
                                        && method.getParameterTypes()[1] == String.class
                                );
                                
                                if (getAttributeMethod == null) {
                                    XposedBridge.log("WaEnhancer: Could not find getAttribute method");
                                    return;
                                }
                                
                                // Get the JID attribute
                                // We need the WhatsApp internal UserJid class for the first argument
                                Class<?> waUserJidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.UserJid");
                                if (waUserJidClass == null) {
                                     XposedBridge.log("WaEnhancer: Could not find WhatsApp UserJid class");
                                     return;
                                }

                                Object jidObj = getAttributeMethod.invoke(element, waUserJidClass, "jid");
                                if (jidObj == null) return;
                                
                                // Convert to UserJid and get contact name
                                FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(jidObj);
                                
                                // Filter groups/non-contacts
                                if (!userJid.isContact() || userJid.isGroup() || userJid.isNewsletter()) return;

                                String contactName = WppCore.getContactName(userJid);
                                String jid = userJid.getUserRawString();
                                
                                String message = String.format(
                                    Utils.getApplication().getString(com.wmods.wppenhacer.R.string.profile_picture_has_been_updated),
                                    contactName
                                );
                                
                                if (notificationOption == 1) {
                                    Utils.showToast(message, Toast.LENGTH_LONG);
                                } else if (notificationOption == 2) {
                                    showNotification(
                                        Utils.getApplication().getString(com.wmods.wppenhacer.R.string.app_name),
                                        message,
                                        jid
                                    );
                                }
                                
                                XposedBridge.log("WaEnhancer: Profile picture changed for: " + contactName);

                                // Update stored hash just in case we need it for other things later
                                String currentHash = getProfilePictureHash(jid);
                                getHashPrefs().edit().putString(jid, currentHash).apply();
                                
                            } catch (Throwable t) {
                                XposedBridge.log("WaEnhancer: Error in profile picture hook: " + t.getMessage());
                            }
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("WaEnhancer: Error hooking real-time profile updates: " + t);
        }
    }

    private android.content.SharedPreferences getHashPrefs() {
        if (hashPrefs == null) {
            hashPrefs = Utils.getApplication().getSharedPreferences(HASH_PREFS, android.content.Context.MODE_PRIVATE);
        }
        return hashPrefs;
    }

    private int getNotificationOption() {
        try {
            return Integer.parseInt(this.prefs.getString(PREF_KEY, "0"));
        } catch (Throwable ignored) {
            // Backward compatibility: old builds used a boolean preference
            return this.prefs.getBoolean(PREF_KEY, false) ? 1 : 0;
        }
    }

    private void handleProfilePictureUpdate(WaContactWpp waContact) {
        try {
            var userJid = waContact.getUserJid();
            if (userJid.isNull() || !userJid.isContact() || userJid.isGroup()) return;

            String jid = userJid.getUserRawString();
            String contactName = WppCore.getContactName(userJid);

            checkAndNotifyProfilePictureChange(jid, contactName);

        } catch (Exception e) {
            log("Error handling profile picture update: " + e.getMessage());
        }
    }

    private void checkAndNotifyProfilePictureChange(String jid, String contactName) {
        try {
            String currentHash = getProfilePictureHash(jid);
            String previousHash = getHashPrefs().getString(jid, null);

            // If we have a previous hash and it differs from current, notify
            if (previousHash != null && !previousHash.equals(currentHash) && !currentHash.equals("no_profile")) {
                // Profile picture has changed
                String displayName = TextUtils.isEmpty(contactName) ?
                        WppCore.stripJID(jid) : contactName;

                int notificationOption = getNotificationOption();
                String title = "Profile Picture Updated";
                
                String message = String.format(
                    Utils.getApplication().getString(com.wmods.wppenhacer.R.string.profile_picture_has_been_updated),
                    displayName
                );

                if (notificationOption == 1) {
                    // Show Toast (Main Thread)
                    Utils.showToast(message, Toast.LENGTH_LONG);
                } else if (notificationOption == 2) {
                    // Show Notification
                    showNotification(title, message, jid);
                }

                log("Profile picture changed for: " + displayName);
            }

            // Update the stored hash if changed or new
            if (!currentHash.equals(previousHash)) {
                getHashPrefs().edit().putString(jid, currentHash).apply();
            }

        } catch (Exception e) {
            log("Error checking profile picture change: " + e.getMessage());
        }
    }

    private void showNotification(String title, String message, String jid) {
        try {
            var app = Utils.getApplication();
            if (app == null) return;

            PendingIntent pendingIntent = null;
            try {
                // Create intent to open chat with the contact using ACTION_VIEW standard
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setClassName(app.getPackageName(), "com.whatsapp.Conversation");

                // Fix: Only append domain if not present. Use @s.whatsapp.net as default.
                String rawJid = jid.contains("@") ? jid : jid + "@s.whatsapp.net";
                
                // Set both raw string and parcelable UserJid for maximum compatibility
                intent.putExtra("jid", rawJid);
                var jidObj = WppCore.createUserJid(rawJid);
                if (jidObj != null) {
                    try {
                        intent.putExtra("jid", (Parcelable) jidObj);
                    } catch (Throwable ignored) {
                    }
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                pendingIntent = PendingIntent.getActivity(app, rawJid.hashCode(), intent, flags);
            } catch (Throwable e) {
                log("Error preparing notification intent for " + jid + ": " + e.getMessage());
            }

            Utils.showNotification(title, message, pendingIntent);
            log("Showed profile picture notification for: " + jid);
        } catch (Exception e) {
            log("Error in showNotification: " + e.getMessage());
        }
    }

    private String getProfilePictureHash(String jid) {
        try {
            File profileFile = WppCore.getContactPhotoFile(jid);
            if (profileFile != null && profileFile.exists()) {
                return profileFile.getAbsolutePath() + "_" + profileFile.lastModified();
            }

            // Try alternative paths
            String strippedJid = WppCore.stripJID(jid);
            File[] possibleFiles = {
                    new File("/data/data/com.whatsapp/cache/Profile Pictures/" + strippedJid + ".jpg"),
                    new File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/Profile Pictures/" + strippedJid + ".jpg"),
                    new File("/data/data/com.whatsapp/files/Avatars/" + jid + ".j")
            };

            for (File file : possibleFiles) {
                if (file.exists()) {
                    return file.getAbsolutePath() + "_" + file.lastModified();
                }
            }

        } catch (Exception e) {
            log("Error getting profile picture hash: " + e.getMessage());
        }

        return "no_profile";
    }

    private class ProfilePictureChangeListener extends ContactItemListener.OnContactItemListener {
        @Override
        public void onBind(WaContactWpp waContact, android.view.View view) {
            try {
                // Only update hash silently during bind to avoid false positives when opening chats
                Utils.getExecutor().execute(() -> {
                    var userJid = waContact.getUserJid();
                    if (userJid != null && !userJid.isNull() && userJid.isContact() && !userJid.isGroup()) {
                        String jid = userJid.getUserRawString();
                        String currentHash = getProfilePictureHash(jid);
                        getHashPrefs().edit().putString(jid, currentHash).apply();
                    }
                });

            } catch (Exception e) {
                log("Error in profile picture change listener: " + e.getMessage());
            }
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Profile Picture Change Notifier";
    }
}
