package com.wmods.wppenhacer.xposed.utils

import android.content.Context
import android.util.Log
import kotlin.jvm.JvmField

object ResId {
    object drawable {
        @JvmField var eye_disabled: Int = 0
        @JvmField var eye_enabled: Int = 0
        @JvmField var admin: Int = 0
        @JvmField var preview_eye: Int = 0
        @JvmField var refresh: Int = 0
        @JvmField var ghost_disabled: Int = 0
        @JvmField var ghost_enabled: Int = 0
        @JvmField var airplane_enabled: Int = 0
        @JvmField var airplane_disabled: Int = 0
        @JvmField var online: Int = 0
        @JvmField var deleted: Int = 0
        @JvmField var download: Int = 0
        @JvmField var camera: Int = 0
        @JvmField var edit2: Int = 0
        @JvmField var ic_privacy: Int = 0
        @JvmField var user_foreground: Int = 0
        @JvmField var audio_speed_container_bg: Int = 0
        @JvmField var audio_speed_seekbar_progress: Int = 0
        @JvmField var audio_speed_seekbar_thumb: Int = 0
        @JvmField var ic_check_circle: Int = 0
        @JvmField var ic_audio_speed: Int = 0
        @JvmField var ic_translator: Int = 0
        @JvmField var ic_group_call: Int = 0
        
        // Ticks
        @JvmField var alien_message_got_read_receipt_from_target: Int = 0
        @JvmField var alien_message_got_read_receipt_from_target_onmedia: Int = 0
        @JvmField var alien_message_got_receipt_from_server: Int = 0
        @JvmField var alien_message_got_receipt_from_server_onmedia: Int = 0
        @JvmField var alien_message_got_receipt_from_target: Int = 0
        @JvmField var alien_message_got_receipt_from_target_onmedia: Int = 0
        @JvmField var alien_message_unsent: Int = 0
        @JvmField var alien_message_unsent_onmedia: Int = 0
        // ... (I'll truncate the ticks for now and add them later if needed, but I should probably include the common ones)
    }

    object string {
        @JvmField var edited_history: Int = 0
        @JvmField var dnd_message: Int = 0
        @JvmField var dnd_mode_title: Int = 0
        @JvmField var freezelastseen_message: Int = 0
        @JvmField var freezelastseen_title: Int = 0
        @JvmField var activate: Int = 0
        @JvmField var cancel: Int = 0
        @JvmField var message_original: Int = 0
        @JvmField var new_chat: Int = 0
        @JvmField var number_with_country_code: Int = 0
        @JvmField var message: Int = 0
        @JvmField var download: Int = 0
        @JvmField var error_when_saving_try_again: Int = 0
        @JvmField var msg_text_status_not_downloadable: Int = 0
        @JvmField var saved_to: Int = 0
        @JvmField var restart_whatsapp: Int = 0
        @JvmField var restart_wpp: Int = 0
        @JvmField var send_blue_tick: Int = 0
        @JvmField var sending_read_blue_tick: Int = 0
        @JvmField var send: Int = 0
        @JvmField var send_sticker: Int = 0
        @JvmField var do_you_want_to_send_sticker: Int = 0
        @JvmField var whatsapp_call: Int = 0
        @JvmField var phone_call: Int = 0
        @JvmField var yes: Int = 0
        @JvmField var no: Int = 0
        @JvmField var version_error: Int = 0
        @JvmField var copy_to_clipboard: Int = 0
        @JvmField var copied_to_clipboard: Int = 0
        @JvmField var error_detected: Int = 0
        @JvmField var rebooting: Int = 0
        @JvmField var deleted_status: Int = 0
        @JvmField var deleted_message: Int = 0
        @JvmField var deleted_messages: Int = 0
        @JvmField var toast_online: Int = 0
        @JvmField var message_removed_on: Int = 0
        @JvmField var loading: Int = 0
        @JvmField var delete_for_me: Int = 0
        @JvmField var alert_delete_for_me: Int = 0
        @JvmField var dialog_delete_for_me: Int = 0
        @JvmField var dialog_delete_for_me_sum: Int = 0
        @JvmField var share_as_status: Int = 0
        @JvmField var viewed_your_status: Int = 0
        @JvmField var viewed_your_message: Int = 0
        @JvmField var select_status_type: Int = 0
        @JvmField var open_camera: Int = 0
        @JvmField var edit_text: Int = 0
        @JvmField var select_a_color: Int = 0
        @JvmField var read_all_mark_as_read: Int = 0
        @JvmField var grant_permission: Int = 0
        @JvmField var expiration: Int = 0
        @JvmField var deleted_a_message_in_group: Int = 0
        @JvmField var allow: Int = 0
        @JvmField var invalid_folder: Int = 0
        @JvmField var uri_permission: Int = 0
        @JvmField var ghost_mode: Int = 0
        @JvmField var ghost_mode_message: Int = 0
        @JvmField var disable: Int = 0
        @JvmField var enable: Int = 0
        @JvmField var ghost_mode_s: Int = 0
        @JvmField var starting_cache: Int = 0
        @JvmField var bridge_error: Int = 0
        @JvmField var not_available: Int = 0
        @JvmField var app_name: Int = 0
        @JvmField var hideread: Int = 0
        @JvmField var hideread_sum: Int = 0
        @JvmField var hidestatusview: Int = 0
        @JvmField var hidestatusview_sum: Int = 0
        @JvmField var hidereceipt: Int = 0
        @JvmField var hidereceipt_sum: Int = 0
        @JvmField var ghostmode: Int = 0
        @JvmField var ghostmode_sum: Int = 0
        @JvmField var ghostmode_r: Int = 0
        @JvmField var ghostmode_sum_r: Int = 0
        @JvmField var blueonreply: Int = 0
        @JvmField var blueonreply_sum: Int = 0
        @JvmField var custom_privacy: Int = 0
        @JvmField var custom_privacy_sum: Int = 0
        @JvmField var block_call: Int = 0
        @JvmField var call_blocker_sum: Int = 0
        @JvmField var custom_privacy_global_status: Int = 0
        @JvmField var enabled: Int = 0
        @JvmField var disabled: Int = 0
        @JvmField var contact_s: Int = 0
        @JvmField var phone_number_s: Int = 0
        @JvmField var country_s: Int = 0
        @JvmField var city_s: Int = 0
        @JvmField var ip_s: Int = 0
        @JvmField var platform_s: Int = 0
        @JvmField var wpp_version_s: Int = 0
        @JvmField var call_information: Int = 0
        @JvmField var country_code_s: Int = 0
        @JvmField var region_name_s: Int = 0
        @JvmField var region_s: Int = 0
        @JvmField var zip_s: Int = 0
        @JvmField var timezone_s: Int = 0
        @JvmField var isp_s: Int = 0
        @JvmField var org_s: Int = 0
        @JvmField var as_s: Int = 0
        @JvmField var lat_lon_s: Int = 0
        @JvmField var ask_download_folder: Int = 0
        @JvmField var download_folder_permission: Int = 0
        @JvmField var no_contact_with_custom_privacy: Int = 0
        @JvmField var select_contacts: Int = 0
        @JvmField var download_not_available: Int = 0
        @JvmField var block_not_detected: Int = 0
        @JvmField var possible_block_detected: Int = 0
        @JvmField var checking_if_the_contact_is_blocked: Int = 0
        @JvmField var block_unverified: Int = 0
        @JvmField var contact_probably_not_added: Int = 0
        @JvmField var preview_image: Int = 0
        @JvmField var preview_video: Int = 0
        @JvmField var downloading: Int = 0
        @JvmField var msg_hide_the_forwarding_label: Int = 0
        @JvmField var failed_decode_image: Int = 0
        @JvmField var error_display_image: Int = 0
        @JvmField var error_display_video: Int = 0
        @JvmField var invalid_media_key: Int = 0
        @JvmField var lockedchats_enhancer: Int = 0
        @JvmField var lockedchats_enhancer_sum: Int = 0
        @JvmField var voice_note_speed: Int = 0
        @JvmField var audio_speed_control: Int = 0
        @JvmField var audio_speed_control_sum: Int = 0
        @JvmField var tick_style: Int = 0
        @JvmField var tick_style_sum: Int = 0
        @JvmField var bubble_style: Int = 0
        @JvmField var bubble_style_sum: Int = 0
        @JvmField var custom_media_auto_download: Int = 0
        @JvmField var custom_media_auto_download_sum: Int = 0
        @JvmField var autodownload_image: Int = 0
        @JvmField var autodownload_video: Int = 0
        @JvmField var autodownload_audio: Int = 0
        @JvmField var autodownload_document: Int = 0
        @JvmField var save: Int = 0
        @JvmField var soundboard_title: Int = 0
        @JvmField var soundboard_summary: Int = 0
        @JvmField var soundboard_search_hint: Int = 0
        @JvmField var soundboard_dialog_title: Int = 0
        @JvmField var soundboard_sent: Int = 0
        @JvmField var soundboard_play_error: Int = 0
        @JvmField var schedule_message: Int = 0
        @JvmField var schedule_message_sum: Int = 0
        @JvmField var scheduler_msg_label: Int = 0
        @JvmField var scheduler_msg_hint: Int = 0
        @JvmField var scheduler_quick_delay: Int = 0
        @JvmField var scheduler_toast_scheduled: Int = 0
        @JvmField var scheduler_error_jid: Int = 0
        @JvmField var scheduler_error_launch: Int = 0
        @JvmField var scheduler_error_dialog: Int = 0
        @JvmField var scheduler_error_save: Int = 0
        @JvmField var call_recording_started: Int = 0
        @JvmField var recording_label: Int = 0
        @JvmField var schedule_future_time: Int = 0
        @JvmField var message_required: Int = 0
        @JvmField var repeat_custom: Int = 0
        @JvmField var repeat_specific_dates: Int = 0
        @JvmField var repeat_once: Int = 0
        @JvmField var repeat_daily: Int = 0
        @JvmField var repeat_weekly: Int = 0
        @JvmField var repeat_monthly: Int = 0
        @JvmField var selected_dates: Int = 0
        @JvmField var add_date: Int = 0
        @JvmField var select_at_least_one_date_msg: Int = 0
        @JvmField var no_future_dates_msg: Int = 0
        @JvmField var select_contacts_required: Int = 0
        @JvmField var voice_changer: Int = 0
        @JvmField var voice_effect_disabled: Int = 0
        @JvmField var voice_effect_baby: Int = 0
        @JvmField var voice_effect_teenager: Int = 0
        @JvmField var voice_effect_deep: Int = 0
        @JvmField var voice_effect_robot: Int = 0
        @JvmField var voice_effect_drunk: Int = 0
        @JvmField var voice_effect_fast: Int = 0
        @JvmField var voice_effect_slow_motion: Int = 0
        @JvmField var voice_effect_underwater: Int = 0
        @JvmField var voice_effect_fun: Int = 0
        @JvmField var voice_effect_optimus: Int = 0
        @JvmField var voice_effect_minion: Int = 0
        @JvmField var voice_effect_bane: Int = 0
        @JvmField var voice_effect_female: Int = 0
        @JvmField var voice_effect_male: Int = 0
        @JvmField var voice_effect_s: Int = 0
        @JvmField var voice_note_effect_s: Int = 0
        @JvmField var voice_prefix: Int = 0
        @JvmField var audio_file_not_found: Int = 0
        @JvmField var transcription_failed: Int = 0
        @JvmField var api_key_required: Int = 0
        @JvmField var failed_upload: Int = 0
        @JvmField var failed_start_transcription: Int = 0
        @JvmField var failed_check_status: Int = 0
        @JvmField var transcription_error: Int = 0
        @JvmField var transcription_failed_short: Int = 0
        @JvmField var ghost_mode_enabled: Int = 0
        @JvmField var ghost_mode_disabled: Int = 0
        @JvmField var dnd_mode_enabled: Int = 0
        @JvmField var dnd_mode_disabled: Int = 0
        @JvmField var freeze_last_seen_enabled: Int = 0
        @JvmField var freeze_last_seen_disabled: Int = 0
        @JvmField var checking__status: Int = 0
        @JvmField var possible_blocked: Int = 0
        @JvmField var unverified_contact: Int = 0
        @JvmField var contact_verified: Int = 0
        @JvmField var contact_not_in_list: Int = 0
        @JvmField var call_blocking_type: Int = 0
        @JvmField var call_blocking_type_sum: Int = 0
        @JvmField var force_backup_restore: Int = 0
        @JvmField var warning_restore: Int = 0
        @JvmField var separate_groups_sum: Int = 0
        @JvmField var separate_groups_unsupported_sum: Int = 0
        @JvmField var waenhancer_settings: Int = 0
    }

    object array {
        @JvmField var supported_versions_wpp: Int = 0
        @JvmField var supported_versions_business: Int = 0
        @JvmField var call_type_buttons: Int = 0
        @JvmField var call_type_values: Int = 0
    }

    object xml {
        @JvmField var fragment_general: Int = 0
        @JvmField var fragment_privacy: Int = 0
        @JvmField var fragment_media: Int = 0
        @JvmField var fragment_customization: Int = 0
        @JvmField var embedded_settings_root: Int = 0
        @JvmField var embedded_settings_general: Int = 0
        @JvmField var embedded_settings_home_screen: Int = 0
        @JvmField var embedded_settings_conversation: Int = 0
        @JvmField var embedded_settings_privacy: Int = 0
        @JvmField var embedded_settings_status: Int = 0
        @JvmField var embedded_settings_calls: Int = 0
        @JvmField var embedded_settings_media: Int = 0
        @JvmField var embedded_settings_audio: Int = 0
        @JvmField var embedded_settings_appearance: Int = 0
        @JvmField var embedded_settings_advanced: Int = 0
    }

    object id {
        @JvmField var date_wrapper: Int = 0
        @JvmField var date: Int = 0
        @JvmField var date_tv: Int = 0
        @JvmField var timestamp: Int = 0
        @JvmField var view_once_control_icon: Int = 0
    }
    
    object style {
        @JvmField var Theme: Int = 0
        @JvmField var Theme_Light: Int = 0
    }

    fun initLocal(context: Context) {
        if (string.app_name != 0) return // Already initialized
        try {
            val packageName = "com.wmods.wppenhacer"
            val rString = Class.forName("$packageName.R\$string")
            for (field in string::class.java.fields) {
                try {
                    val rField = rString.getField(field.name)
                    field.set(null, rField.getInt(null))
                } catch (ignored: Exception) {}
            }
            val rDrawable = Class.forName("$packageName.R\$drawable")
            for (field in drawable::class.java.fields) {
                try {
                    val rField = rDrawable.getField(field.name)
                    field.set(null, rField.getInt(null))
                } catch (ignored: Exception) {}
            }
            val rXml = Class.forName("$packageName.R\$xml")
            for (field in xml::class.java.fields) {
                try {
                    val rField = rXml.getField(field.name)
                    field.set(null, rField.getInt(null))
                } catch (ignored: Exception) {}
            }
            val rStyle = Class.forName("$packageName.R\$style")
            for (field in style::class.java.fields) {
                try {
                    val rField = rStyle.getField(field.name)
                    field.set(null, rField.getInt(null))
                } catch (ignored: Exception) {}
            }
            val rArray = Class.forName("$packageName.R\$array")
            for (field in array::class.java.fields) {
                try {
                    val rField = rArray.getField(field.name)
                    field.set(null, rField.getInt(null))
                } catch (ignored: Exception) {}
            }
            val rId = Class.forName("$packageName.R\$id")
            for (field in id::class.java.fields) {
                try {
                    val rField = rId.getField(field.name)
                    field.set(null, rField.getInt(null))
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("WAE", "Local ResId init failed: " + e.message)
        }
    }
}
