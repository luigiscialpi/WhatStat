-- addon_message_media definition

CREATE TABLE addon_message_media(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,message_row_id INTEGER NOT NULL,addon_message_index INTEGER NOT NULL,chat_row_id INTEGER,file_path TEXT,file_size INTEGER,media_key BLOB,media_key_timestamp INTEGER,width INTEGER,height INTEGER,direct_path TEXT,message_url TEXT,mime_type TEXT,file_length INTEGER,file_hash TEXT,enc_file_hash TEXT,partial_media_hash TEXT,partial_media_enc_hash TEXT,original_file_hash TEXT,thumbnail TEXT,thumbnail_direct_path TEXT,thumbnail_hash TEXT,enc_thumbnail_hash TEXT,scans_sidecar BLOB, transferred INTEGER);


-- agent_chat_assignment definition

CREATE TABLE agent_chat_assignment (jid_row_id INTEGER PRIMARY KEY, agent_id TEXT NOT NULL, is_opened BOOLEAN, account_jid_row_id INTEGER);


-- agent_devices definition

CREATE TABLE agent_devices (agent_id TEXT PRIMARY KEY NOT NULL,agent_name TEXT UNIQUE NOT NULL,device INTEGER,last_modified_time INTEGER,is_deleted BOOLEAN );


-- agent_message_attribution definition

CREATE TABLE agent_message_attribution (message_row_id INTEGER PRIMARY KEY, agent_id TEXT NOT NULL);


-- ai_agentic_metadata definition

CREATE TABLE ai_agentic_metadata(message_row_id INTEGER PRIMARY KEY,bot_progress_indicator_metadata BLOB);


-- ai_rich_response_message_additional_info definition

CREATE TABLE ai_rich_response_message_additional_info(message_row_id INTEGER PRIMARY KEY,ai_rich_response_additional_blob BLOB);


-- ai_rich_response_message_core_info definition

CREATE TABLE ai_rich_response_message_core_info(message_row_id INTEGER PRIMARY KEY,ai_rich_response_message_type INTEGER NOT NULL DEFAULT 0,ai_rich_response_submessage_types TEXT NOT NULL DEFAULT '',additional_table_mask INTEGER NOT NULL DEFAULT 0,ai_rich_response_core_blob BLOB, planning_status INTEGER, foa_native_data BLOB, foa_native_mutation BLOB, foa_native_mutation_extended BLOB);


-- ai_thread_info definition

CREATE TABLE ai_thread_info(thread_id_row_id INTEGER PRIMARY KEY,title TEXT,creation_ts INTEGER NOT NULL,variant INTEGER NOT NULL DEFAULT 1,last_thread_messages_row_id INTEGER,last_message_timestamp INTEGER, title_source INTEGER, unseen_message_count INTEGER, origin_chat_row_id INTEGER, selected_mode INTEGER, selected_modes BLOB);


-- ai_thread_info_fts definition

CREATE VIRTUAL TABLE ai_thread_info_fts USING FTS4 (
            search_content
          );


-- ai_thread_info_fts_content definition

CREATE TABLE 'ai_thread_info_fts_content'(docid INTEGER PRIMARY KEY, 'c0search_content');


-- ai_thread_info_fts_docsize definition

CREATE TABLE 'ai_thread_info_fts_docsize'(docid INTEGER PRIMARY KEY, size BLOB);


-- ai_thread_info_fts_segdir definition

CREATE TABLE 'ai_thread_info_fts_segdir'(level INTEGER,idx INTEGER,start_block INTEGER,leaves_end_block INTEGER,end_block INTEGER,root BLOB,PRIMARY KEY(level, idx));


-- ai_thread_info_fts_segments definition

CREATE TABLE 'ai_thread_info_fts_segments'(blockid INTEGER PRIMARY KEY, block BLOB);


-- ai_thread_info_fts_stat definition

CREATE TABLE 'ai_thread_info_fts_stat'(id INTEGER PRIMARY KEY, value BLOB);


-- android_metadata definition

CREATE TABLE android_metadata (locale TEXT);


-- audio_data definition

CREATE TABLE audio_data (message_row_id INTEGER PRIMARY KEY, waveform BLOB, background_color INTEGER NOT NULL DEFAULT 0, transcription_status INTEGER, transcription_locale INTEGER, transcription_confidence_threshold INTEGER, transcription_request_locale INTEGER, transcription_feedback_submitted INTEGER, transcription_id TEXT, background_color_changed INTEGER DEFAULT 0);


-- away_messages definition

CREATE TABLE away_messages (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT UNIQUE NOT NULL);


-- away_messages_exemptions definition

CREATE TABLE away_messages_exemptions (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid TEXT UNIQUE NOT NULL, exempt BOOLEAN, exempt_until INTEGER);


-- backup_changes definition

CREATE TABLE backup_changes(_id INTEGER PRIMARY KEY AUTOINCREMENT, operation TEXT NOT NULL, table_name TEXT NOT NULL, table_row_id INTEGER NOT NULL);


-- bcall_session definition

CREATE TABLE bcall_session(_id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL UNIQUE,media_type INTEGER NOT NULL,master_key BLOB NOT NULL, caption TEXT);


-- bot_chat_info definition

CREATE TABLE bot_chat_info(chat_row_id INTEGER PRIMARY KEY,pip_enabled BOOLEAN DEFAULT 0, welcome_request_message_sent BOOLEAN, bot_metrics_thread_origin TEXT);


-- bot_memory_metadata definition

CREATE TABLE bot_memory_metadata(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,memory_annotated_user_message_key_id TEXT NOT NULL,memory TEXT,memory_id TEXT NOT NULL,added INTEGER NOT NULL, bot_jid_row_id INTEGER);


-- bot_message_info definition

CREATE TABLE bot_message_info(message_row_id INTEGER PRIMARY KEY,target_id TEXT,message_state INTEGER DEFAULT 0, invoker_jid_row_id INTEGER, model_type INTEGER, message_disclaimer TEXT, keyword_json TEXT, promotion_message TEXT, imagine_json TEXT, age_collection INTEGER, bot_response_id TEXT, bot_jid_row_id INTEGER, in_app_thread_survey TEXT, verification_metadata BLOB, response_viewed INTEGER, bot_group_json TEXT, metrics_metadata_json TEXT, bot_deep_link_token TEXT);


-- bot_message_prompts definition

CREATE TABLE bot_message_prompts(message_row_id INTEGER PRIMARY KEY,chat_row_id INTEGER UNIQUE,prompts TEXT,impression_logged INTEGER DEFAULT 0);


-- bot_message_sharing_info definition

CREATE TABLE bot_message_sharing_info(message_row_id INTEGER PRIMARY KEY,message_id TEXT,bot_entry_point_origin INTEGER,forward_score INTEGER NOT NULL DEFAULT 0);


-- bot_plugin_metadata definition

CREATE TABLE bot_plugin_metadata(message_row_id INTEGER PRIMARY KEY,search_provider INTEGER,plugin_type INTEGER,thumbnail_cdn_url TEXT,profile_photo_cdn_url TEXT,search_provider_url TEXT,reference_index INTEGER,profile_photo_thumbnail BLOB, search_query TEXT, favicon_cdn_url TEXT);


-- broadcast_chat_details definition

CREATE TABLE broadcast_chat_details(chat_row_id INTEGER PRIMARY KEY NOT NULL,use_case INTEGER NOT NULL);


-- business_message_forward_info definition

CREATE TABLE business_message_forward_info(message_row_id INTEGER PRIMARY KEY,business_owner_jid_row_id INTEGER NOT NULL);


-- call_link definition

CREATE TABLE call_link (_id INTEGER PRIMARY KEY AUTOINCREMENT, token TEXT NOT NULL, creator_jid_row_id INTEGER NOT NULL DEFAULT 0);


-- call_log definition

CREATE TABLE call_log (    _id               INTEGER PRIMARY KEY AUTOINCREMENT,    jid_row_id        INTEGER,    from_me           INTEGER,    call_id           TEXT,    transaction_id    INTEGER,    timestamp         INTEGER,    video_call        INTEGER,    duration          INTEGER,    call_result       INTEGER,    bytes_transferred INTEGER, group_jid_row_id INTEGER NOT NULL DEFAULT 0, is_joinable_group_call INTEGER, call_creator_device_jid_row_id INTEGER NOT NULL DEFAULT 0, call_random_id TEXT, call_link_row_id INTEGER NOT NULL DEFAULT 0, is_dnd_mode_on INTEGER, call_type INTEGER, offer_silence_reason INTEGER, scheduled_id TEXT, telecom_uuid TEXT, terminated_by_device_switch INTEGER);


-- call_log_participant_v2 definition

CREATE TABLE call_log_participant_v2 (    _id             INTEGER PRIMARY KEY AUTOINCREMENT,    call_log_row_id INTEGER,    jid_row_id      INTEGER,    call_result     INTEGER);


-- call_unknown_caller definition

CREATE TABLE call_unknown_caller(call_log_row_id INTEGER PRIMARY KEY);


-- chat definition

CREATE TABLE chat (    _id                                   INTEGER PRIMARY KEY AUTOINCREMENT,    jid_row_id                            INTEGER UNIQUE,    hidden                                INTEGER,    subject                               TEXT,    created_timestamp                     INTEGER,    display_message_row_id                INTEGER,    last_message_row_id                   INTEGER,    last_read_message_row_id              INTEGER,    last_read_receipt_sent_message_row_id INTEGER,    last_important_message_row_id         INTEGER,    archived                              INTEGER,    sort_timestamp                        INTEGER,    mod_tag                               INTEGER,    gen                                   REAL,    spam_detection                        INTEGER,    unseen_earliest_message_received_time INTEGER,    unseen_message_count                  INTEGER,    unseen_missed_calls_count             INTEGER,    unseen_row_count                      INTEGER,    plaintext_disabled                    INTEGER,    vcard_ui_dismissed                    INTEGER,    change_number_notified_message_row_id INTEGER,    show_group_description                INTEGER, ephemeral_expiration INTEGER, last_read_ephemeral_message_row_id INTEGER, ephemeral_setting_timestamp INTEGER, unseen_important_message_count INTEGER NOT NULL DEFAULT 0, ephemeral_disappearing_messages_initiator INTEGER, group_type INTEGER NOT NULL DEFAULT 0, last_message_reaction_row_id INTEGER, last_seen_message_reaction_row_id INTEGER, unseen_message_reaction_count INTEGER, growth_lock_level INTEGER, growth_lock_expiration_ts INTEGER, last_read_message_sort_id INTEGER, display_message_sort_id INTEGER, last_message_sort_id INTEGER, last_read_receipt_sent_message_sort_id INTEGER, has_new_community_admin_dialog_been_acknowledged INTEGER NOT NULL DEFAULT 0, history_sync_progress INTEGER, ephemeral_displayed_exemptions INTEGER, chat_lock INTEGER, chat_origin TEXT, unseen_comment_message_count INTEGER, participation_status INTEGER, account_jid_row_id INTEGER, chat_encryption_state INTEGER, group_member_count INTEGER, limited_sharing INTEGER, limited_sharing_setting_timestamp INTEGER, is_contact INTEGER);


-- chat_ephemeral definition

CREATE TABLE chat_ephemeral(chat_row_id INTEGER PRIMARY KEY,ephemeral_trigger INTEGER,ephemeral_initiated_by_me BOOLEAN);


-- community_chat definition

CREATE TABLE community_chat (chat_row_id INTEGER PRIMARY KEY, last_activity_ts INTEGER, last_activity_seen_ts INTEGER , join_ts INTEGER, closed INTEGER NOT NULL DEFAULT 0);


-- composition definition

CREATE TABLE composition(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_row_id INTEGER NOT NULL,quoted_message_row_id INTEGER,timestamp INTEGER NOT NULL,message_type INTEGER NOT NULL,composition_type INTEGER NOT NULL,text TEXT,lookup_tables INTEGER NOT NULL DEFAULT 0, last_seen_timestamp INTEGER);


-- composition_media definition

CREATE TABLE composition_media(composition_row_id INTEGER PRIMARY KEY NOT NULL,media_uri TEXT,media_duration_in_seconds INTEGER, media_name TEXT, file_length INTEGER);


-- composition_mention definition

CREATE TABLE composition_mention(_id INTEGER PRIMARY KEY AUTOINCREMENT,composition_row_id INTEGER NOT NULL,jid_row_id INTEGER NOT NULL, mention_type INTEGER);


-- conversion_tuples definition

CREATE TABLE conversion_tuples (jid_row_id INTEGER PRIMARY KEY, data TEXT, source TEXT, biz_count INTEGER, has_user_sent_last_message BOOLEAN, last_interaction INTEGER);


-- data_sharing_disclosure_metadata definition

CREATE TABLE data_sharing_disclosure_metadata(message_row_id INTEGER PRIMARY KEY,show_mm_disclosure BOOLEAN);


-- deleted_chat_job definition

CREATE TABLE deleted_chat_job
(        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_row_id INTEGER NOT NULL,
        block_size INTEGER,
        deleted_message_row_id INTEGER,
        deleted_starred_message_row_id INTEGER,
        deleted_messages_remove_files BOOLEAN,
        deleted_categories_message_row_id INTEGER,
        deleted_categories_starred_message_row_id INTEGER,
        deleted_categories_remove_files BOOLEAN,
        deleted_message_categories TEXT, singular_message_delete_rows_id TEXT, delete_files_singular_delete BOOLEAN);


-- dynamic_audience_sources definition

CREATE TABLE dynamic_audience_sources(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_row_id INTEGER NOT NULL,dynamic_audience_type INTEGER NOT NULL,dynamic_audience_id INTEGER NOT NULL);


-- extended_media_data definition

CREATE TABLE extended_media_data(row_id INTEGER PRIMARY KEY AUTOINCREMENT,type INTEGER NOT NULL,direct_path TEXT,file_path TEXT,file_hash TEXT,file_size INTEGER,media_key BLOB,media_key_timestamp INTEGER,enc_file_hash TEXT,width INTEGER,height INTEGER,media_caption TEXT,transferred INTEGER, preview_path TEXT, external_url TEXT, mime_type TEXT, display_type INTEGER);


-- favorite definition

CREATE TABLE favorite(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid_row_id INTEGER UNIQUE,favorite_type INTEGER,sort_order INTEGER);


-- feature_key_store definition

CREATE TABLE feature_key_store(_id INTEGER PRIMARY KEY AUTOINCREMENT,key_id TEXT NOT NULL,key BLOB,key_type INTEGER NOT NULL,creation_timestamp INTEGER NOT NULL,expiry_timestamp INTEGER);


-- forwarded_newsletter_message_info definition

CREATE TABLE forwarded_newsletter_message_info(message_row_id INTEGER PRIMARY KEY,newsletter_jid_row_id INTEGER NOT NULL,newsletter_server_message_id INTEGER NOT NULL, newsletter_name TEXT NOT NULL DEFAULT '', profile_name TEXT);


-- frequent definition

CREATE TABLE frequent (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid_row_id INTEGER NOT NULL, type INTEGER NOT NULL, message_count INTEGER NOT NULL);


-- frequent_forward_chat definition

CREATE TABLE frequent_forward_chat(chat_row_id INTEGER PRIMARY KEY,num_forward INTEGER,last_forward_timestamp INTEGER, last_scan INTEGER, num_image INTEGER, num_video INTEGER, num_gif INTEGER);


-- gap_enforcement_business_chat_thread_info_cache definition

CREATE TABLE gap_enforcement_business_chat_thread_info_cache(business_chat_row_id INTEGER PRIMARY KEY NOT NULL,business_chat_is_mm_thread INTEGER, business_chat_thread_type INTEGER);


-- group_history_bundle definition

CREATE TABLE group_history_bundle(message_row_id INTEGER PRIMARY KEY NOT NULL,process_state INTEGER NOT NULL DEFAULT 0, send_state INTEGER NOT NULL DEFAULT 0);


-- group_history_bundle_association definition

CREATE TABLE group_history_bundle_association(message_row_id INTEGER PRIMARY KEY NOT NULL,bundle_message_row_id INTEGER NOT NULL, message_sort_id INTEGER, bundle_sender_jid_row_id INTEGER, bundle_message_key_id TEXT, bundle_message_key_from_me BOOLEAN, bundle_message_key_chat_row_id INTEGER);


-- group_history_metadata definition

CREATE TABLE group_history_metadata(message_row_id INTEGER PRIMARY KEY NOT NULL,history_receivers TEXT NOT NULL,first_message_timestamp_seconds INTEGER NOT NULL,message_count INTEGER NOT NULL, non_history_receivers TEXT, oldest_message_timestamp_in_bundle_seconds INTEGER);


-- group_history_share_reporting_info definition

CREATE TABLE group_history_share_reporting_info(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,stanza_id TEXT NOT NULL,reporting_token BLOB,reporting_token_version INTEGER,added_timestamp DATETIME NOT NULL,send_timestamp DATETIME,reporting_tag BLOB,is_send INTEGER);


-- group_notification_version definition

CREATE TABLE group_notification_version (group_jid_row_id INTEGER PRIMARY KEY, subject_timestamp INTEGER NOT NULL, announcement_version INTEGER NOT NULL, participant_version INTEGER NOT NULL, group_join_request_timestamp INTEGER);


-- group_participant_device definition

CREATE TABLE group_participant_device (_id INTEGER PRIMARY KEY AUTOINCREMENT, group_participant_row_id INTEGER NOT NULL, device_jid_row_id INTEGER NOT NULL, sent_sender_key INTEGER, sent_add_on_sender_key INTEGER);


-- group_participant_label_metadata definition

CREATE TABLE group_participant_label_metadata(group_participant_user_row_id INTEGER PRIMARY KEY,edit_time DATETIME NOT NULL);


-- group_participant_user definition

CREATE TABLE group_participant_user (_id INTEGER PRIMARY KEY AUTOINCREMENT, group_jid_row_id INTEGER NOT NULL, user_jid_row_id INTEGER NOT NULL, rank INTEGER, pending INTEGER, add_timestamp INTEGER, label TEXT, join_method INTEGER);


-- group_participants definition

CREATE TABLE group_participants (_id INTEGER PRIMARY KEY AUTOINCREMENT, gjid TEXT NOT NULL, jid TEXT NOT NULL, admin INTEGER, pending INTEGER, sent_sender_key INTEGER);

CREATE UNIQUE INDEX group_participants_index on group_participants (gjid, jid);


-- group_participants_history definition

CREATE TABLE group_participants_history (_id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp DATETIME NOT NULL, gjid TEXT NOT NULL, jid TEXT NOT NULL, action INTEGER NOT NULL, old_phash TEXT NOT NULL, new_phash TEXT NOT NULL);

CREATE INDEX group_participants_history_index on group_participants_history (gjid);


-- group_past_participant_user definition

CREATE TABLE group_past_participant_user (_id INTEGER PRIMARY KEY AUTOINCREMENT, group_jid_row_id INTEGER NOT NULL, user_jid_row_id INTEGER NOT NULL, is_leave INTEGER NOT NULL, timestamp INTEGER);


-- integrator_display_name definition

CREATE TABLE integrator_display_name (integrator_id INTEGER PRIMARY KEY NOT NULL, display_name TEXT NOT NULL, status INTEGER NOT NULL, icon_path TEXT NOT NULL DEFAULT '', opt_in_status INTEGER NOT NULL DEFAULT 0, identifier_type INTEGER NOT NULL DEFAULT 0);


-- integrity_chat_info definition

CREATE TABLE integrity_chat_info(chat_row_id INTEGER PRIMARY KEY,is_reach_out INTEGER, is_eligible_for_link_friction_banner INTEGER);


-- integrity_deleted_chat_message_count definition

CREATE TABLE integrity_deleted_chat_message_count(id INTEGER PRIMARY KEY AUTOINCREMENT,lid TEXT NOT NULL DEFAULT '',messages_receive_date TEXT NOT NULL DEFAULT '',messages_count INTEGER NOT NULL DEFAULT 0);


-- integrity_deleted_chat_metadata definition

CREATE TABLE integrity_deleted_chat_metadata(id INTEGER PRIMARY KEY AUTOINCREMENT,lid TEXT NOT NULL DEFAULT '',chat_type INTEGER NOT NULL DEFAULT 1,is_first_reach_out INTEGER NOT NULL DEFAULT 0,chat_creation_timestamp INTEGER NOT NULL DEFAULT 0,last_incoming_message_timestamp INTEGER NOT NULL DEFAULT 0,lidHash TEXT NOT NULL DEFAULT '');


-- interactive_message_bloks_widget definition

CREATE TABLE interactive_message_bloks_widget(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,message_row_id INTEGER NOT NULL,uuid TEXT,data TEXT,type TEXT);


-- interactive_message_header_content definition

CREATE TABLE interactive_message_header_content(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,message_row_id INTEGER NOT NULL,header_title TEXT,header_sub_title TEXT,header_thumbnail BLOB,document_url TEXT,document_direct_path TEXT,document_media_key BLOB,document_media_key_timestamp_ms INTEGER,document_media_hash TEXT,document_media_enc_hash TEXT,document_mime_type TEXT,document_file_name TEXT,document_file_path TEXT,document_file_length INTEGER);


-- interactive_message_sections definition

CREATE TABLE interactive_message_sections(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,message_row_id INTEGER NOT NULL,section_index INTEGER NOT NULL,section_title TEXT,section_highlight_label TEXT,item_index INTEGER NOT NULL,item_id TEXT,item_header TEXT,item_title TEXT,item_description TEXT);


-- invoice_transactions definition

CREATE TABLE invoice_transactions (message_row_id INTEGER PRIMARY KEY,pay_transaction_id INTEGER);


-- jid definition

CREATE TABLE jid (    _id        INTEGER PRIMARY KEY AUTOINCREMENT,    user       TEXT    NOT NULL,    server     TEXT    NOT NULL,    agent      INTEGER,    type       INTEGER,    raw_string TEXT, device INTEGER);


-- jid_map definition

CREATE TABLE jid_map (lid_row_id INTEGER PRIMARY KEY NOT NULL, jid_row_id INTEGER NOT NULL, sort_id INTEGER);


-- jid_user_metadata definition

CREATE TABLE jid_user_metadata(jid_row_id INTEGER PRIMARY KEY NOT NULL,country_code TEXT);


-- joinable_call_log definition

CREATE TABLE joinable_call_log (call_log_row_id INTEGER PRIMARY KEY,call_id TEXT NOT NULL, group_jid_row_id INTEGER NOT NULL DEFAULT 0, joinable_video_call INTEGER NOT NULL DEFAULT 0, phash_identifier TEXT, self_other_device_connected INTEGER);


-- keywords definition

CREATE TABLE keywords (_id INTEGER PRIMARY KEY AUTOINCREMENT, keyword TEXT UNIQUE NOT NULL);


-- labeled_jid definition

CREATE TABLE labeled_jid (_id INTEGER PRIMARY KEY AUTOINCREMENT, label_id INTEGER NOT NULL, jid_row_id INTEGER NOT NULL);


-- labeled_messages definition

CREATE TABLE labeled_messages (_id INTEGER PRIMARY KEY AUTOINCREMENT, label_id INTEGER NOT NULL, message_row_id INTEGER NOT NULL);

CREATE UNIQUE INDEX labeled_messages_index on labeled_messages (label_id, message_row_id);


-- labeled_messages_fts definition

CREATE VIRTUAL TABLE labeled_messages_fts USING FTS3();


-- labeled_messages_fts_content definition

CREATE TABLE 'labeled_messages_fts_content'(docid INTEGER PRIMARY KEY, 'c0content');


-- labeled_messages_fts_segdir definition

CREATE TABLE 'labeled_messages_fts_segdir'(level INTEGER,idx INTEGER,start_block INTEGER,leaves_end_block INTEGER,end_block INTEGER,root BLOB,PRIMARY KEY(level, idx));


-- labeled_messages_fts_segments definition

CREATE TABLE 'labeled_messages_fts_segments'(blockid INTEGER PRIMARY KEY, block BLOB);


-- labels definition

CREATE TABLE labels (_id INTEGER PRIMARY KEY AUTOINCREMENT, label_name TEXT, predefined_id INTEGER, color_id INTEGER, sort_id INTEGER NOT NULL DEFAULT 0, type INTEGER NOT NULL DEFAULT 0, hidden INTEGER, mute_end_time INTEGER, mute_schedule_enabled_days INTEGER, mute_schedule_time_from INTEGER, mute_schedule_time_to INTEGER, is_immutable INTEGER, is_aura_benefit_enabled INTEGER);


-- lid_chat_state definition

CREATE TABLE lid_chat_state (jid_row_id INTEGER PRIMARY KEY NOT NULL, is_pn_shared INTEGER NOT NULL DEFAULT 0, pn_requested_ts INTEGER NOT NULL DEFAULT 0, pnh_duplicate_lid_thread INTEGER NOT NULL DEFAULT 0);


-- lid_display_name definition

CREATE TABLE lid_display_name (lid_row_id INTEGER PRIMARY KEY NOT NULL, display_name TEXT NOT NULL, username TEXT);


-- media_hash_thumbnail definition

CREATE TABLE media_hash_thumbnail ( media_hash TEXT PRIMARY KEY, thumbnail BLOB);


-- media_processed_video definition

CREATE TABLE media_processed_video(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER,direct_path TEXT,height INTEGER,width INTEGER,file_size INTEGER,bitrate INTEGER,quality INTEGER,capabilities TEXT);


-- media_refs definition

CREATE TABLE media_refs (_id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT UNIQUE, ref_count INTEGER);


-- message definition

CREATE TABLE message (    _id                      INTEGER PRIMARY KEY AUTOINCREMENT,    chat_row_id              INTEGER NOT NULL,    from_me                  INTEGER NOT NULL,    key_id                   TEXT    NOT NULL,    sender_jid_row_id        INTEGER,    status                   INTEGER,    broadcast                INTEGER,    recipient_count          INTEGER,    participant_hash         TEXT,    origination_flags        INTEGER,    origin                   INTEGER,    timestamp                INTEGER,    received_timestamp       INTEGER,    receipt_server_timestamp INTEGER,    message_type             INTEGER,    text_data                TEXT,    starred                  INTEGER,    lookup_tables            INTEGER, sort_id INTEGER NOT NULL DEFAULT 0, message_add_on_flags INTEGER, view_mode INTEGER, translated_text TEXT, view_replies_thread_id INTEGER);


-- message_add_on definition

CREATE TABLE message_add_on (_id INTEGER PRIMARY KEY AUTOINCREMENT, chat_row_id INTEGER, from_me INTEGER, key_id TEXT NOT NULL, sender_jid_row_id INTEGER, parent_message_row_id INTEGER, timestamp INTEGER, status INTEGER, message_add_on_type INTEGER, received_timestamp INTEGER, expiry_duration_in_secs INTEGER, server_timestamp INTEGER, expiry_timestamp INTEGER, expiry_type INTEGER);


-- message_add_on_event_response definition

CREATE TABLE message_add_on_event_response(message_add_on_row_id INTEGER PRIMARY KEY,response INTEGER DEFAULT 0,sender_timestamp INTEGER NOT NULL, extra_guest_count INTEGER);


-- message_add_on_keep_in_chat definition

CREATE TABLE message_add_on_keep_in_chat (message_add_on_row_id INTEGER PRIMARY KEY, keep_in_chat_state INTEGER NOT NULL DEFAULT 0, sender_timestamp INTEGER, keep_count INTEGER NOT NULL DEFAULT 0, actor_device_jid_row_id INTEGER);


-- message_add_on_pin_in_chat definition

CREATE TABLE message_add_on_pin_in_chat (message_add_on_row_id INTEGER PRIMARY KEY, pin_in_chat_state INTEGER NOT NULL DEFAULT 0, sender_timestamp INTEGER);


-- message_add_on_poll_vote definition

CREATE TABLE message_add_on_poll_vote (message_add_on_row_id INTEGER PRIMARY KEY, sender_timestamp INTEGER);


-- message_add_on_poll_vote_selected_option definition

CREATE TABLE message_add_on_poll_vote_selected_option (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_add_on_row_id INTEGER, message_poll_option_id INTEGER);


-- message_add_on_question_response definition

CREATE TABLE message_add_on_question_response(message_add_on_row_id INTEGER PRIMARY KEY,response TEXT);


-- message_add_on_reaction definition

CREATE TABLE message_add_on_reaction (message_add_on_row_id INTEGER PRIMARY KEY, reaction TEXT, sender_timestamp INTEGER);


-- message_add_on_receipt_device definition

CREATE TABLE message_add_on_receipt_device (receipt_device_id INTEGER PRIMARY KEY AUTOINCREMENT, message_add_on_row_id INTEGER, receipt_device_jid_row_id INTEGER, receipt_device_timestamp INTEGER, primary_device_version INTEGER);


-- message_add_on_scheduled_call_edit definition

CREATE TABLE message_add_on_scheduled_call_edit(message_add_on_row_id INTEGER PRIMARY KEY,edit_type INTEGER NOT NULL DEFAULT 0,message_edit_version INTEGER);


-- message_add_on_status_question_answer definition

CREATE TABLE message_add_on_status_question_answer(message_add_on_row_id INTEGER PRIMARY KEY,answer TEXT);


-- message_add_on_status_sticker_interaction definition

CREATE TABLE message_add_on_status_sticker_interaction(message_add_on_row_id INTEGER PRIMARY KEY,sticker_key TEXT,type INTEGER);


-- message_ai_media_collection definition

CREATE TABLE message_ai_media_collection(message_row_id INTEGER PRIMARY KEY,collection_id TEXT,expected_media_count INTEGER,has_global_caption INTEGER);


-- message_album definition

CREATE TABLE message_album(message_row_id INTEGER PRIMARY KEY,image_count INTEGER NOT NULL DEFAULT 0,video_count INTEGER NOT NULL DEFAULT 0, expected_image_count INTEGER, expected_video_count INTEGER);


-- message_association definition

CREATE TABLE message_association(_id INTEGER PRIMARY KEY AUTOINCREMENT,child_message_row_id INTEGER NOT NULL,parent_message_row_id INTEGER NOT NULL,association_type INTEGER NOT NULL);


-- message_bcall_session definition

CREATE TABLE message_bcall_session(message_row_id INTEGER PRIMARY KEY,bcall_session_row_id INTEGER);


-- message_biz_context_info definition

CREATE TABLE message_biz_context_info(message_row_id INTEGER PRIMARY KEY,weblink_render_config INTEGER);


-- message_bot_feedback definition

CREATE TABLE message_bot_feedback(message_row_id INTEGER PRIMARY KEY,bot_feedback_kind INTEGER NOT NULL,bot_feedback_text TEXT NOT NULL,bot_feedback_key_remote_jid TEXT NOT NULL,bot_feedback_key_from_me INTEGER NOT NULL,bot_feedback_key_id TEXT NOT NULL, bot_feedback_kind_positive INTEGER NOT NULL DEFAULT 0, bot_feedback_kind_negative INTEGER NOT NULL DEFAULT 0);


-- message_broadcast_ephemeral definition

CREATE TABLE message_broadcast_ephemeral (message_row_id INTEGER PRIMARY KEY, shared_secret BLOB NOT NULL );


-- message_call_log definition

CREATE TABLE message_call_log(message_row_id INTEGER PRIMARY KEY,call_log_row_id INTEGER);


-- message_comment definition

CREATE TABLE message_comment(_id INTEGER PRIMARY KEY AUTOINCREMENT,parent_message_row_id INTEGER,message_row_id INTEGER);


-- message_comment_parent definition

CREATE TABLE message_comment_parent(message_row_id INTEGER PRIMARY KEY,chat_row_id INTEGER,number_of_comments INTEGER NOT NULL,last_comment_ts INTEGER,last_comment_message_row_id INTEGER);


-- message_conditional_reveal definition

CREATE TABLE message_conditional_reveal(message_row_id INTEGER PRIMARY KEY,enc_payload BLOB,enc_iv BLOB,proto_data BLOB,reveal_key_index INTEGER,conditional_reveal_type INTEGER);


-- message_details definition

CREATE TABLE message_details (message_row_id INTEGER PRIMARY KEY, author_device_jid INTEGER);


-- message_edit_info definition

CREATE TABLE message_edit_info (message_row_id INTEGER PRIMARY KEY, original_key_id TEXT NOT NULL, edited_timestamp INTEGER NOT NULL, sender_timestamp INTEGER NOT NULL);


-- message_ephemeral definition

CREATE TABLE "message_ephemeral"(message_row_id INTEGER PRIMARY KEY, duration INTEGER NOT NULL, expire_timestamp INTEGER NOT NULL, keep_in_chat INTEGER NOT NULL DEFAULT 0, ephemeral_trigger INTEGER, ephemeral_initiated_by_me INTEGER, after_read_duration INTEGER);


-- message_ephemeral_setting definition

CREATE TABLE "message_ephemeral_setting" (message_row_id INTEGER PRIMARY KEY, setting_duration INTEGER, setting_reason INTEGER, user_jid_row_id_csv TEXT, ephemeral_trigger INTEGER, ephemeral_initiated_by_me INTEGER, pre_setting_duration INTEGER, after_read_duration INTEGER);


-- message_ephemeral_sync_response definition

CREATE TABLE message_ephemeral_sync_response(chat_jid TEXT PRIMARY KEY NOT NULL, last_sync_response_sent_timestamp INTEGER NOT NULL, no_of_retries_sent_already INTEGER NOT NULL DEFAULT 0);


-- message_event definition

CREATE TABLE message_event(message_row_id INTEGER PRIMARY KEY,is_canceled INTEGER DEFAULT 0,name TEXT NOT NULL,description TEXT,location_latitude REAL,location_longitude REAL,location_name TEXT,location_address TEXT,join_link TEXT,start_time DATETIME NOT NULL, chat_row_id INTEGER, event_state INTEGER NOT NULL DEFAULT 0, end_time DATETIME, allow_extra_guests INTEGER, is_schedule_call INTEGER, has_reminder INTEGER, reminder_offset_sec INTEGER, show_upcoming_banner INTEGER);


-- message_external_ad_content definition

CREATE TABLE message_external_ad_content (message_row_id INTEGER PRIMARY KEY, title TEXT, body TEXT, media_type INTEGER, thumbnail_url TEXT, full_thumbnail BLOB, micro_thumbnail BLOB, media_url TEXT, source_type TEXT, source_id TEXT, source_url TEXT, render_larger_thumbnail BOOLEAN, show_ad_attribution BOOLEAN, has_icebreaker_auto_response BOOLEAN, has_click_to_call_auto_response BOOLEAN, ad_context_preview_dismissed INTEGER, source_app TEXT, automated_greeting_message_shown INTEGER, greeting_message_body TEXT, cta_payload TEXT, disable_nudge INTEGER, original_image_url TEXT, automated_greeting_message_cta_type TEXT, ctwa_clid TEXT, wtwa_ad_format BOOLEAN, ad_preview_url TEXT, wtwa_website_url TEXT);


-- message_fixed_content_placeholder definition

CREATE TABLE message_fixed_content_placeholder(message_row_id INTEGER PRIMARY KEY,placeholder_type INTEGER NOT NULL);


-- message_forwarded definition

CREATE TABLE message_forwarded(message_row_id INTEGER PRIMARY KEY, forward_score INTEGER, forward_origin INTEGER);


-- message_future definition

CREATE TABLE message_future (message_row_id INTEGER PRIMARY KEY, version INTEGER, data BLOB, future_message_type INTEGER, future_proof_stanza BLOB, edit_version INTEGER, message_stanza_data BLOB);


-- message_group_invite definition

CREATE TABLE message_group_invite (message_row_id INTEGER PRIMARY KEY, group_jid_row_id INTEGER NOT NULL, admin_jid_row_id INTEGER NOT NULL, group_name TEXT, invite_code TEXT, expiration INTEGER, invite_time INTEGER, expired INTEGER, group_type INTEGER NOT NULL DEFAULT 0);


-- message_inline_video_metadata definition

CREATE TABLE message_inline_video_metadata(message_row_id INTEGER PRIMARY KEY NOT NULL,video_content_url TEXT NOT NULL,is_muted INTEGER NOT NULL DEFAULT 0, caption TEXT);


-- message_invoice definition

CREATE TABLE message_invoice (message_row_id INTEGER PRIMARY KEY,wa_invoice_id TEXT NOT NULL,amount TEXT NOT NULL,note TEXT NOT NULL,token TEXT,sender_jid_row_id INTEGER,receiver_jid_row_id INTEGER,status INTEGER,status_ts INTEGER,creation_ts INTEGER,attachment_type INTEGER,attachment_mimetype TEXT,attachment_media_key BLOB,attachment_media_key_ts INTEGER,attachment_file_sha256 BLOB,attachment_file_enc_sha256 BLOB,attachment_direct_path TEXT,attachment_jpeg_thumbnail BLOB,metadata TEXT);


-- message_limit_sharing_setting definition

CREATE TABLE message_limit_sharing_setting(message_row_id INTEGER PRIMARY KEY,enabled INTEGER,trigger INTEGER);


-- message_link definition

CREATE TABLE message_link (_id INTEGER PRIMARY KEY AUTOINCREMENT, chat_row_id INTEGER, message_row_id INTEGER, link_index INTEGER);


-- message_location definition

CREATE TABLE message_location (message_row_id INTEGER PRIMARY KEY, chat_row_id INTEGER, latitude REAL, longitude REAL, place_name TEXT, place_address TEXT, url TEXT, live_location_share_duration INTEGER, live_location_sequence_number INTEGER, live_location_final_latitude REAL, live_location_final_longitude REAL, live_location_final_timestamp INTEGER, map_download_status INTEGER);


-- message_logging_funnel_id definition

CREATE TABLE message_logging_funnel_id(message_row_id INTEGER PRIMARY KEY,fs_funnel_id STRING,ps_funnel_id STRING);


-- message_media definition

CREATE TABLE message_media (message_row_id INTEGER PRIMARY KEY, chat_row_id INTEGER, autotransfer_retry_enabled INTEGER, multicast_id TEXT, media_job_uuid TEXT, transferred INTEGER, transcoded INTEGER, file_path TEXT, file_size INTEGER, suspicious_content INTEGER, trim_from INTEGER, trim_to INTEGER, face_x INTEGER, face_y INTEGER, media_key BLOB, media_key_timestamp INTEGER, width INTEGER, height INTEGER, has_streaming_sidecar INTEGER, gif_attribution INTEGER, thumbnail_height_width_ratio REAL, direct_path TEXT, first_scan_sidecar BLOB, first_scan_length INTEGER, message_url TEXT, mime_type TEXT, file_length INTEGER, media_name TEXT, file_hash TEXT, media_duration INTEGER, page_count INTEGER, enc_file_hash TEXT, partial_media_hash TEXT, partial_media_enc_hash TEXT, is_animated_sticker INTEGER, original_file_hash TEXT, mute_video INTEGER DEFAULT 0, media_caption TEXT, media_upload_handle TEXT, sticker_flags INTEGER, raw_transcription_text TEXT, first_viewed_timestamp INTEGER, doodle_id TEXT, media_source_type INTEGER, accessibility_label TEXT, media_transcode_quality INTEGER DEFAULT 0, metadata_url TEXT, motion_photo_presentation_offset_ms INTEGER, qr_url TEXT, media_key_domain INTEGER, e2ee_media_key BLOB, premium_message INTEGER);


-- message_media_interactive_annotation definition

CREATE TABLE message_media_interactive_annotation (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, location_latitude REAL, location_longitude REAL, location_name TEXT, sort_order INTEGER, newsletter_jid_row_id INTEGER, newsletter_server_message_id INTEGER, newsletter_name TEXT, skip_confirmation INTEGER NOT NULL DEFAULT 0, newsletter_content_type INTEGER, newsletter_accessibility_text TEXT, child_message_row_id INTEGER, type INTEGER, fp_interactive_annotation BLOB, status_link_type INTEGER);


-- message_media_interactive_annotation_embedded_music definition

CREATE TABLE message_media_interactive_annotation_embedded_music(message_media_interactive_annotation_row_id INTEGER PRIMARY KEY,music_content_media_id TEXT,song_id TEXT,author TEXT,title TEXT,artwork_direct_path TEXT,artwork_sha256 BLOB,artwork_enc_sha256 BLOB,artist_attribution TEXT,country_blocklist BLOB, artwork_media_key BLOB, is_explicit INTEGER, pending_embedded_music_type INTEGER, start_time_ms INTEGER, derived_content_start_time_ms INTEGER, overlap_duration_ms INTEGER, audio_library_product TEXT);


-- message_media_interactive_annotation_vertex definition

CREATE TABLE message_media_interactive_annotation_vertex (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_media_interactive_annotation_row_id INTEGER, x REAL, y REAL, sort_order INTEGER);


-- message_media_map definition

CREATE TABLE message_media_map(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,chat_row_id INTEGER NOT NULL,media_row_id INTEGER NOT NULL);


-- message_media_vcard_count definition

CREATE TABLE message_media_vcard_count(_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, count INTEGER);


-- message_mentions definition

CREATE TABLE message_mentions (_id  INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, jid_row_id INTEGER, display_name STRING, mention_type INTEGER);


-- message_newsletter_admin_invite definition

CREATE TABLE message_newsletter_admin_invite(message_row_id INTEGER PRIMARY KEY,newsletter_jid_row_id INTEGER NOT NULL,newsletter_name TEXT NOT NULL,expiration INTEGER NOT NULL);


-- message_newsletter_follower_invite definition

CREATE TABLE message_newsletter_follower_invite(message_row_id INTEGER PRIMARY KEY,newsletter_jid_row_id INTEGER NOT NULL,newsletter_name TEXT NOT NULL);


-- message_order definition

CREATE TABLE message_order (message_row_id INTEGER PRIMARY KEY, order_id TEXT,thumbnail BLOB,order_title TEXT,item_count INTEGER,status INTEGER,surface INTEGER,message TEXT,seller_jid INTEGER,token TEXT, currency_code TEXT, total_amount_1000 INTEGER, message_version INTEGER, catalog_type TEXT);


-- message_orphan definition

CREATE TABLE message_orphan(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_row_id INTEGER,from_me INTEGER,key_id TEXT NOT NULL,sender_jid_row_id INTEGER,parent_chat_row_id INTEGER,parent_from_me INTEGER,parent_key_id TEXT,parent_sender_jid_row_id INTEGER,timestamp INTEGER,orphan_message_data BLOB,orphan_message_type INTEGER,orphan_message_stanza_data BLOB, orphan_message_reason INTEGER);


-- message_orphaned_edit definition

CREATE TABLE message_orphaned_edit (    _id               INTEGER PRIMARY KEY,    key_id            TEXT    NOT NULL,    from_me           INTEGER NOT NULL,    chat_row_id       INTEGER NOT NULL,    sender_jid_row_id INTEGER NOT NULL                              DEFAULT (0),    timestamp         INTEGER,    message_type      INTEGER NOT NULL,    revoked_key_id    TEXT,    retry_count       INTEGER, admin_jid_row_id INTEGER, orphan_message_data BLOB, reporting_token BLOB, reporting_tag BLOB, reporting_version INTEGER);


-- message_parent_association definition

CREATE TABLE message_parent_association(message_row_id INTEGER PRIMARY KEY,parent_message_row_id INTEGER NOT NULL,association_type INTEGER NOT NULL);


-- message_payment definition

CREATE TABLE message_payment (    message_row_id                         INTEGER PRIMARY KEY,    sender_jid_row_id                      INTEGER,    receiver_jid_row_id                    INTEGER,    amount_with_symbol                     TEXT,    remote_resource                        TEXT,    remote_message_sender_jid_row_id       INTEGER,    remote_message_from_me                 INTEGER,    remote_message_key                     TEXT);


-- message_payment_invite definition

CREATE TABLE message_payment_invite (message_row_id INTEGER PRIMARY KEY, service INTEGER, expiration_timestamp INTEGER, incentive_eligible INTEGER, referral_id TEXT);


-- message_payment_status_update definition

CREATE TABLE message_payment_status_update (    message_row_id         INTEGER PRIMARY KEY,    transaction_info       TEXT,    transaction_data       TEXT,    init_timestamp         TEXT,    update_timestamp       TEXT,    amount_data            TEXT);


-- message_payment_transaction_reminder definition

CREATE TABLE message_payment_transaction_reminder (    message_row_id            INTEGER PRIMARY KEY,    web_stub                  TEXT,    amount                    TEXT,    transfer_date             TEXT,    payment_sender_name       INTEGER,    expiration                INTEGER,    remote_message_key        TEXT);


-- message_poll definition

CREATE TABLE message_poll (message_row_id INTEGER PRIMARY KEY, enc_key BLOB, selectable_options_count INTEGER, invalid_state INTEGER NOT NULL DEFAULT 0, poll_logging_id INTEGER NOT NULL DEFAULT 0, poll_type INTEGER, correct_option_id INTEGER, content_type INTEGER, hide_participant_names INTEGER, end_time INTEGER);


-- message_poll_option definition

CREATE TABLE message_poll_option (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, option_sha256 TEXT, option_name TEXT, vote_total INTEGER, option_hash TEXT);


-- message_privacy_state definition

CREATE TABLE message_privacy_state (message_row_id INTEGER NOT NULL PRIMARY KEY, host_storage INTEGER, actual_actors INTEGER, privacy_mode_ts INTEGER NOT NULL, business_name TEXT);


-- message_product definition

CREATE TABLE message_product (message_row_id INTEGER PRIMARY KEY, business_owner_jid INTEGER, product_id TEXT, title TEXT, description TEXT, currency_code TEXT, amount_1000 INTEGER, retailer_id TEXT, url TEXT, product_image_count INTEGER, sale_amount_1000 INTEGER, body TEXT, footer TEXT, signed_url TEXT);


-- message_quarantine definition

CREATE TABLE message_quarantine(message_row_id INTEGER PRIMARY KEY,chat_row_id INTEGER,timestamp INTEGER NOT NULL,original_protobuf BLOB NOT NULL,serialized_stanza BLOB, protobuf_type INTEGER);


-- message_question definition

CREATE TABLE message_question(message_row_id INTEGER PRIMARY KEY,response_count INTEGER DEFAULT 0, response_read_count INTEGER DEFAULT 0, is_enabled INTEGER DEFAULT 1);


-- message_quote_invoice definition

CREATE TABLE message_quote_invoice (message_row_id INTEGER PRIMARY KEY,amount TEXT NOT NULL,note TEXT NOT NULL,status INTEGER,attachment_jpeg_thumbnail BLOB);


-- message_quoted definition

CREATE TABLE message_quoted (    message_row_id             INTEGER PRIMARY KEY AUTOINCREMENT,    chat_row_id                INTEGER NOT NULL,    parent_message_chat_row_id INTEGER NOT NULL,    from_me                    INTEGER NOT NULL,    sender_jid_row_id          INTEGER,    key_id                     TEXT    NOT NULL,    timestamp                  INTEGER,    message_type               INTEGER,    text_data                  TEXT,    payment_transaction_id     TEXT,    lookup_tables              INTEGER, origin INTEGER, quoted_source INTEGER, quoted_type INTEGER);


-- message_quoted_blank_reply definition

CREATE TABLE message_quoted_blank_reply (message_row_id INTEGER PRIMARY KEY, parent_group_jid TEXT, group_subject  TEXT);


-- message_quoted_call_log definition

CREATE TABLE message_quoted_call_log(message_row_id INTEGER PRIMARY KEY,video_call BOOLEAN,call_result INTEGER);


-- message_quoted_group_invite definition

CREATE TABLE message_quoted_group_invite (message_row_id INTEGER PRIMARY KEY, group_jid_row_id INTEGER NOT NULL, admin_jid_row_id INTEGER NOT NULL, group_name TEXT, invite_code TEXT, expiration INTEGER, invite_time INTEGER, expired INTEGER, group_type INTEGER NOT NULL DEFAULT 0);


-- message_quoted_group_invite_legacy definition

CREATE TABLE "message_quoted_group_invite_legacy" (message_row_id INTEGER PRIMARY KEY, group_jid_row_id INTEGER NOT NULL, admin_jid_row_id INTEGER NOT NULL, group_name TEXT, invite_code TEXT, expiration INTEGER, invite_time INTEGER, expired INTEGER, group_type INTEGER NOT NULL DEFAULT 0);


-- message_quoted_location definition

CREATE TABLE message_quoted_location (message_row_id INTEGER PRIMARY KEY, latitude REAL, longitude REAL, place_name TEXT, place_address TEXT, url TEXT, thumbnail BLOB);


-- message_quoted_media definition

CREATE TABLE message_quoted_media (message_row_id INTEGER PRIMARY KEY, media_job_uuid TEXT, transferred INTEGER, file_path TEXT, file_size INTEGER, media_key BLOB, media_key_timestamp INTEGER, width INTEGER, height INTEGER, direct_path TEXT, message_url TEXT, mime_type TEXT, file_length INTEGER, media_name TEXT, file_hash TEXT, media_duration INTEGER, page_count INTEGER, enc_file_hash TEXT, thumbnail BLOB, media_caption TEXT, accessibility_label TEXT);


-- message_quoted_mentions definition

CREATE TABLE message_quoted_mentions (_id  INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, jid_row_id INTEGER, display_name STRING, mention_type INTEGER);


-- message_quoted_order definition

CREATE TABLE message_quoted_order (message_row_id INTEGER PRIMARY KEY, order_id TEXT,thumbnail BLOB,order_title TEXT,item_count INTEGER,status INTEGER,surface INTEGER,message TEXT,seller_jid INTEGER,token TEXT, currency_code TEXT, total_amount_1000 INTEGER, message_version INTEGER, catalog_type TEXT);


-- message_quoted_payment_invite definition

CREATE TABLE message_quoted_payment_invite (message_row_id INTEGER PRIMARY KEY, service INTEGER, expiration_timestamp INTEGER, incentive_eligible INTEGER, referral_id TEXT);


-- message_quoted_product definition

CREATE TABLE message_quoted_product (message_row_id INTEGER PRIMARY KEY, business_owner_jid INTEGER, product_id TEXT, title TEXT, description TEXT, currency_code TEXT, amount_1000 INTEGER, retailer_id TEXT, url TEXT, product_image_count INTEGER, sale_amount_1000 INTEGER, body TEXT, footer TEXT, signed_url TEXT);


-- message_quoted_text definition

CREATE TABLE message_quoted_text (message_row_id INTEGER PRIMARY KEY, thumbnail BLOB);


-- message_quoted_ui_elements definition

CREATE TABLE message_quoted_ui_elements(message_row_id INTEGER NOT NULL PRIMARY KEY, element_type INTEGER, element_content TEXT);


-- message_quoted_ui_elements_reply definition

CREATE TABLE message_quoted_ui_elements_reply (message_row_id INTEGER PRIMARY KEY, element_type INTEGER,reply_values TEXT, reply_description TEXT);


-- message_quoted_ui_elements_reply_legacy definition

CREATE TABLE message_quoted_ui_elements_reply_legacy (message_row_id INTEGER PRIMARY KEY, element_type INTEGER,reply_values TEXT,reply_description TEXT );


-- message_quoted_vcard definition

CREATE TABLE message_quoted_vcard (_id  INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, vcard TEXT);


-- message_rating definition

CREATE TABLE message_rating (message_row_id INTEGER PRIMARY KEY, rating INTEGER NOT NULL);


-- message_revoked definition

CREATE TABLE message_revoked (message_row_id INTEGER PRIMARY KEY, revoked_key_id TEXT NOT NULL, admin_jid_row_id INTEGER, revoke_timestamp INTEGER);


-- message_scheduled_call definition

CREATE TABLE message_scheduled_call (message_row_id INTEGER PRIMARY KEY, scheduled_timestamp_ms INTEGER, call_type INTEGER, title TEXT);


-- message_secret definition

CREATE TABLE message_secret (message_row_id INTEGER PRIMARY KEY, message_secret BLOB);


-- message_send_count definition

CREATE TABLE message_send_count (message_row_id INTEGER PRIMARY KEY, send_count INTEGER);


-- message_span_indices definition

CREATE TABLE message_span_indices(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER,start_index INTEGER,end_index INTEGER,span_type INTEGER);


-- message_status_psa_campaign definition

CREATE TABLE message_status_psa_campaign (message_row_id INTEGER PRIMARY KEY, campaign_id TEXT , duration INTEGER, first_seen_timestamp INTEGER, action_link_url TEXT, action_link_button_title TEXT);


-- message_sticker_pack definition

CREATE TABLE message_sticker_pack(message_row_id INTEGER PRIMARY KEY,sticker_pack_id TEXT NOT NULL,tray_icon_file_name TEXT NOT NULL,pack_name TEXT NOT NULL,pack_description TEXT,publisher TEXT, image_data_hash TEXT, sticker_pack_size INTEGER, sticker_pack_origin INTEGER);


-- message_sticker_pack_stickers definition

CREATE TABLE message_sticker_pack_stickers(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,file_name TEXT NOT NULL,is_animated INTEGER NOT NULL DEFAULT 0,emojis TEXT,accessibility_label TEXT,is_lottie INTEGER NOT NULL DEFAULT 0,mimetype TEXT);


-- message_streaming_sidecar definition

CREATE TABLE message_streaming_sidecar (message_row_id INTEGER PRIMARY KEY, sidecar BLOB, chunk_lengths BLOB, timestamp INTEGER);


-- message_structure_analysis_result definition

CREATE TABLE message_structure_analysis_result(message_row_id INTEGER PRIMARY KEY NOT NULL,message_field_json_array TEXT,submessage_field_json_array TEXT,button_value_json_array TEXT, cta_url_unique_count INTEGER, body_url_count INTEGER, body_url_unique_count INTEGER, url_unique_count INTEGER, decision_id TEXT, decision_sources TEXT);


-- message_system definition

CREATE TABLE message_system (message_row_id INTEGER PRIMARY KEY, action_type INTEGER NOT NULL);


-- message_system_biz_callback_disabled definition

CREATE TABLE message_system_biz_callback_disabled(message_row_id INTEGER PRIMARY KEY,callback_expiry_timestamp INTEGER, outgoing_failed_call_id TEXT);


-- message_system_biz_callback_enabled definition

CREATE TABLE message_system_biz_callback_enabled(message_row_id INTEGER PRIMARY KEY,callback_expiry_timestamp INTEGER, outgoing_failed_call_id TEXT);


-- message_system_biz_per_customer_3pd_data_share_state definition

CREATE TABLE message_system_biz_per_customer_3pd_data_share_state(message_row_id INTEGER PRIMARY KEY,data_sharing_enabled BOOLEAN);


-- message_system_block_contact definition

CREATE TABLE message_system_block_contact (message_row_id INTEGER PRIMARY KEY, is_blocked INTEGER);


-- message_system_business_broadcast definition

CREATE TABLE message_system_business_broadcast(message_row_id INTEGER PRIMARY KEY,broadcast_raw_jid STRING NOT NULL);


-- message_system_business_state definition

CREATE TABLE message_system_business_state (message_row_id INTEGER PRIMARY KEY, privacy_message_type INTEGER NOT NULL, business_name TEXT, is_deprecated INTEGER);


-- message_system_chat_assignment definition

CREATE TABLE message_system_chat_assignment(message_row_id INTEGER PRIMARY KEY, agent_name TEXT, is_unassigned_chat INTEGER);


-- message_system_chat_participant definition

CREATE TABLE message_system_chat_participant (message_row_id INTEGER, user_jid_row_id INTEGER);


-- message_system_community_link_changed definition

CREATE TABLE message_system_community_link_changed(message_row_id INTEGER PRIMARY KEY, old_group_type INTEGER, new_group_type INTEGER NOT NULL, linked_parent_group_jid_row_id INTEGER);


-- message_system_detected_outcomes_labeled_chat definition

CREATE TABLE message_system_detected_outcomes_labeled_chat(message_row_id INTEGER PRIMARY KEY,predefined_id INTEGER);


-- message_system_device_change definition

CREATE TABLE message_system_device_change (message_row_id INTEGER PRIMARY KEY, device_added_count INTEGER, device_removed_count INTEGER);


-- message_system_ephemeral_setting_not_applied definition

CREATE TABLE message_system_ephemeral_setting_not_applied(message_row_id INTEGER PRIMARY KEY, setting_duration INTEGER);


-- message_system_event_updates definition

CREATE TABLE message_system_event_updates(message_row_id INTEGER PRIMARY KEY,event_message_row_id INTEGER NOT NULL,event_name STRING NOT NULL);


-- message_system_group definition

CREATE TABLE message_system_group (message_row_id INTEGER PRIMARY KEY, is_me_joined INTEGER);


-- message_system_group_auto_restrict definition

CREATE TABLE message_system_group_auto_restrict(message_row_id INTEGER PRIMARY KEY,threshold INTEGER);


-- message_system_group_with_parent definition

CREATE TABLE message_system_group_with_parent(message_row_id INTEGER PRIMARY KEY, linked_parent_group_name TEXT );


-- message_system_initial_privacy_provider definition

CREATE TABLE message_system_initial_privacy_provider(message_row_id INTEGER PRIMARY KEY, privacy_provider INTEGER NOT NULL DEFAULT 0, verified_biz_name TEXT, biz_state_id INTEGER, is_deprecated INTEGER);


-- message_system_lid_change definition

CREATE TABLE message_system_lid_change(message_row_id INTEGER PRIMARY KEY,old_lid_row_id INTEGER,new_lid_row_id INTEGER,display_name TEXT);


-- message_system_linked_group_call definition

CREATE TABLE message_system_linked_group_call(message_row_id INTEGER PRIMARY KEY, call_id TEXT, is_video_call INTEGER, call_type INTEGER);


-- message_system_number_change definition

CREATE TABLE message_system_number_change (message_row_id INTEGER PRIMARY KEY, old_jid_row_id INTEGER, new_jid_row_id INTEGER);


-- message_system_opt_out definition

CREATE TABLE message_system_opt_out(message_row_id INTEGER PRIMARY KEY,biz_opt_out_category INTEGER,biz_opt_out_action INTEGER);


-- message_system_payment_invite_setup definition

CREATE TABLE message_system_payment_invite_setup(message_row_id INTEGER PRIMARY KEY, service INTEGER, invite_used INTEGER);


-- message_system_photo_change definition

CREATE TABLE message_system_photo_change (message_row_id INTEGER PRIMARY KEY, new_photo_id TEXT, old_photo BLOB, new_photo BLOB);


-- message_system_privacy definition

CREATE TABLE message_system_privacy(message_row_id INTEGER PRIMARY KEY,is_transition INTEGER,message_privacy_type INTEGER);


-- message_system_reminder_sent definition

CREATE TABLE message_system_reminder_sent(message_row_id INTEGER PRIMARY KEY,original_message_row_id INTEGER NOT NULL UNIQUE,original_reminder_content TEXT NOT NULL);


-- message_system_reminder_setup definition

CREATE TABLE message_system_reminder_setup(message_row_id INTEGER PRIMARY KEY,original_message_row_id INTEGER NOT NULL UNIQUE,reminder_timestamp_ms INTEGER NOT NULL,reminder_content TEXT NOT NULL);


-- message_system_scheduled_call_start definition

CREATE TABLE message_system_scheduled_call_start(message_row_id INTEGER PRIMARY KEY, creation_message_row_id INTEGER NOT NULL UNIQUE, call_title TEXT NOT NULL, call_timestamp_ms INTEGER NOT NULL);


-- message_system_sibling_group_link_change definition

CREATE TABLE message_system_sibling_group_link_change (message_row_id INTEGER NOT NULL, subgroup_raw_jid TEXT NOT NULL, subgroup_subject TEXT NOT NULL, parent_group_jid_row_id INTEGER, PRIMARY KEY (message_row_id, subgroup_raw_jid));


-- message_system_side_chat_privacy definition

CREATE TABLE message_system_side_chat_privacy(message_row_id INTEGER PRIMARY KEY,origin_chat_row_id INTEGER NOT NULL);


-- message_system_update_audience_linking definition

CREATE TABLE message_system_update_audience_linking(message_row_id INTEGER PRIMARY KEY,lists_to_remove_count INTEGER,lists_to_sync_count INTEGER);


-- message_system_username_change definition

CREATE TABLE message_system_username_change(message_row_id INTEGER PRIMARY KEY,user_jid INTEGER,old_username TEXT,new_username TEXT,display_name TEXT);


-- message_system_value_change definition

CREATE TABLE message_system_value_change (message_row_id INTEGER PRIMARY KEY, old_data TEXT);


-- message_system_with_group_nodes definition

CREATE TABLE message_system_with_group_nodes (message_row_id INTEGER NOT NULL, group_jid_row_id INTEGER NOT NULL, group_node_type INTEGER NOT NULL, group_subject TEXT, version INTEGER, PRIMARY KEY (message_row_id, group_jid_row_id, group_node_type));


-- message_template definition

CREATE TABLE message_template (    message_row_id            INTEGER PRIMARY KEY,    content_text_data         TEXT NOT NULL,    footer_text_data          TEXT, template_id TEXT, csat_trigger_expiration_ts INTEGER, category TEXT, tag TEXT, mask_linked_devices INTEGER);

CREATE INDEX message_template_index ON message_template (message_row_id);


-- message_template_button definition

CREATE TABLE message_template_button (    _id                       INTEGER PRIMARY KEY AUTOINCREMENT,    message_row_id            INTEGER,    text_data                 TEXT NOT NULL,    extra_data                TEXT,    button_type               INTEGER,    used                      INTEGER, selected_index INTEGER, otp_button_type INTEGER, extra_consent_data TEXT, otp_matched_package_name TEXT, webview_presentation INTEGER, selected_carousel_card_index INTEGER, webview_interaction INTEGER);


-- message_template_quoted definition

CREATE TABLE message_template_quoted (    message_row_id            INTEGER PRIMARY KEY,    content_text_data         TEXT NOT NULL,    footer_text_data          TEXT);


-- message_text definition

CREATE TABLE message_text (message_row_id INTEGER PRIMARY KEY, description TEXT, page_title TEXT, url TEXT, font_style INTEGER, text_color INTEGER, background_color INTEGER, preview_type INTEGER, invite_link_group_type INTEGER NOT NULL DEFAULT 0, counter_abuse_token TEXT, fb_experiment_id INTEGER, social_media_post_type INTEGER, link_media_duration_seconds INTEGER, link_end_index INTEGER);


-- message_thumbnail definition

CREATE TABLE message_thumbnail (message_row_id INTEGER PRIMARY KEY, thumbnail BLOB);


-- message_ui_elements definition

CREATE TABLE message_ui_elements(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, message_row_id INTEGER NOT NULL, element_type INTEGER, element_content TEXT, description TEXT, template_id TEXT, hsm_tag TEXT, footer_text TEXT, button_text TEXT, message_type INTEGER);


-- message_ui_elements_reply definition

CREATE TABLE message_ui_elements_reply (message_row_id INTEGER PRIMARY KEY, element_type INTEGER,reply_values TEXT, reply_description TEXT, flow_id TEXT);


-- message_vcard definition

CREATE TABLE message_vcard (_id  INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, vcard TEXT);


-- message_vcard_jid definition

CREATE TABLE message_vcard_jid (_id  INTEGER PRIMARY KEY AUTOINCREMENT, vcard_jid_row_id INTEGER, vcard_row_id INTEGER, message_row_id INTEGER);


-- message_view_once_media definition

CREATE TABLE message_view_once_media (message_row_id INTEGER PRIMARY KEY, state INTEGER NOT NULL );


-- messages_hydrated_four_row_template definition

CREATE TABLE messages_hydrated_four_row_template (message_row_id INTEGER PRIMARY KEY, message_template_id TEXT);


-- messages_quotes definition

CREATE TABLE messages_quotes(_id INTEGER PRIMARY KEY AUTOINCREMENT,key_remote_jid TEXT NOT NULL,key_from_me INTEGER,key_id TEXT NOT NULL,status INTEGER,needs_push INTEGER,data TEXT,timestamp INTEGER,media_url TEXT,media_mime_type TEXT,media_wa_type TEXT,media_size INTEGER,media_name TEXT,media_caption TEXT,media_hash TEXT,media_duration INTEGER,origin INTEGER,latitude REAL,longitude REAL,thumb_image TEXT,remote_resource TEXT,received_timestamp INTEGER,send_timestamp INTEGER,receipt_server_timestamp INTEGER,receipt_device_timestamp INTEGER,read_device_timestamp INTEGER,played_device_timestamp INTEGER,raw_data BLOB,recipient_count INTEGER,participant_hash TEXT,starred INTEGER,quoted_row_id INTEGER,mentioned_jids TEXT,multicast_id TEXT,edit_version INTEGER,media_enc_hash TEXT,payment_transaction_id TEXT,forwarded INTEGER,preview_type INTEGER,send_count INTEGER,lookup_tables INTEGER,future_message_type INTEGER,message_add_on_flags INTEGER);


-- missed_call_log_participant definition

CREATE TABLE missed_call_log_participant (_id INTEGER PRIMARY KEY AUTOINCREMENT, call_logs_row_id INTEGER, jid TEXT, call_result INTEGER);


-- missed_call_logs definition

CREATE TABLE missed_call_logs (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, timestamp INTEGER, video_call INTEGER, group_jid_row_id INTEGER NOT NULL DEFAULT 0, is_joinable_group_call INTEGER, is_dnd_mode_on INTEGER, offer_silence_reason INTEGER);


-- mms_metadata definition

CREATE TABLE mms_metadata(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER,direct_path TEXT,media_key BLOB,media_key_timestamp INTEGER,enc_thumb_hash TEXT,thumb_hash TEXT,thumb_width INTEGER,thumb_height INTEGER,transferred INTEGER,micro_thumbnail BLOB,insert_timestamp INTEGER,handle TEXT,type INTEGER);


-- mms_thumbnail_metadata definition

CREATE TABLE mms_thumbnail_metadata (message_row_id INTEGER PRIMARY KEY, direct_path TEXT, media_key BLOB, media_key_timestamp INTEGER, enc_thumb_hash TEXT, thumb_hash TEXT, thumb_width INTEGER, thumb_height INTEGER, transferred INTEGER, micro_thumbnail BLOB, insert_timestamp INTEGER, handle TEXT);


-- newsletter definition

CREATE TABLE newsletter (chat_row_id INTEGER PRIMARY KEY, name TEXT NOT NULL, name_id INTEGER NOT NULL, description TEXT NOT NULL, description_id INTEGER NOT NULL, picture_url TEXT, picture_id INTEGER NOT NULL, preview_url TEXT, preview_id INTEGER NOT NULL, invite_code TEXT, handle TEXT, subscribers_count INTEGER NOT NULL, membership INTEGER NOT NULL,privacy INTEGER NOT NULL,verified INTEGER NOT NULL,muted INTEGER NOT NULL,oldest_message_retrieved INTEGER NOT NULL, suspended INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, show_enforced_update_banner INTEGER, reaction_setting INTEGER, reaction_setting_blocklist STRING, reaction_setting_update_ts INTEGER, verification_source INTEGER, admin_count INTEGER, wamo_sub_plan_id INTEGER, capabilities INTEGER, wamo_sub_status INTEGER, fts_index_state INTEGER, last_fts_message_indexed INTEGER, admin_activity_tone TEXT, follower_activity_tone TEXT, admin_activity_vibrate INTEGER, follower_activity_vibrate INTEGER, admin_profile_id INTEGER, admin_profile_name TEXT, admin_profile_picture_id INTEGER, admin_profile_picture_url TEXT, last_status_server_id INTEGER, last_filled_status_server_id INTEGER, refresh_after_interval_sec INTEGER);


-- newsletter_linked_account definition

CREATE TABLE newsletter_linked_account (_id INTEGER PRIMARY KEY AUTOINCREMENT, chat_row_id INTEGER NOT NULL, name TEXT NOT NULL,link TEXT NOT NULL);


-- newsletter_message definition

CREATE TABLE newsletter_message (message_row_id INTEGER PRIMARY KEY, chat_row_id INTEGER NOT NULL, server_message_id INTEGER NOT NULL,comments_count INTEGER NOT NULL, reaction_from_me TEXT, extra_newsletter_tables INTEGER NOT NULL DEFAULT 0, extra_table_last_update_ts INTEGER, reactions_from_me_ts INTEGER, view_count INTEGER, is_autodelete_eligible INTEGER, is_wamo_sub INTEGER, forwards_count INTEGER, admin_profile_id INTEGER, admin_profile_name TEXT, admin_profile_picture_id INTEGER, admin_profile_picture_url TEXT, is_paid_partnership INTEGER);


-- newsletter_message_reaction definition

CREATE TABLE newsletter_message_reaction(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,reaction TEXT NOT NULL,reaction_count INTEGER NOT NULL);


-- newsletter_my_reaction_orphan_message definition

CREATE TABLE newsletter_my_reaction_orphan_message(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_row_id INTEGER NOT NULL,server_message_id INTEGER NOT NULL,reaction_from_me TEXT,reactions_from_me_ts INTEGER, votes_from_me TEXT, votes_from_me_ts INTEGER);


-- newsletter_subscribers definition

CREATE TABLE newsletter_subscribers(_id INTEGER PRIMARY KEY,chat_row_id INTEGER NOT NULL,jid_row_id INTEGER,display_name TEXT,profile_picture_direct_path TEXT,subscription_time INTEGER,role INTEGER NOT NULL DEFAULT 0,type_of_fetch INTEGER NOT NULL DEFAULT 0,fetched_time INTEGER NOT NULL DEFAULT 0, admin_profile_id TEXT, admin_profile_name TEXT, admin_profile_picture_id INTEGER, admin_profile_picture_url TEXT);


-- optimised_delivery_info definition

CREATE TABLE optimised_delivery_info(message_row_id INTEGER PRIMARY KEY,msg_disclosed_token TEXT,msg_undisclosed_token TEXT,msg_timestamp LONG UNIQUE,token_timestamp LONG,business_jid_row_id INTEGER NOT NULL, msg_timestamp_v2 INTEGER);


-- parent_group_participants definition

CREATE TABLE parent_group_participants (parent_group_jid_row_id INTEGER NOT NULL, user_jid_row_id INTEGER NOT NULL);


-- pay_transaction definition

CREATE TABLE pay_transaction (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER, remote_jid_row_id INTEGER, key_id TEXT, interop_id TEXT, id TEXT, timestamp INTEGER, status INTEGER, error_code TEXT, sender_jid_row_id INTEGER, receiver_jid_row_id INTEGER, type INTEGER, currency_code TEXT, amount_1000, credential_id TEXT, methods TEXT, bank_transaction_id TEXT, metadata TEXT, init_timestamp INTEGER, request_key_id TEXT, country TEXT, version INTEGER, future_data BLOB, service_id INTEGER, background_id TEXT, purchase_initiator INTEGER);


-- payment_background definition

CREATE TABLE payment_background (background_id TEXT PRIMARY KEY, file_size INTEGER, width INTEGER, height INTEGER, mime_type TEXT, placeholder_color INTEGER, text_color INTEGER, subtext_color INTEGER, fullsize_url TEXT, description TEXT, lg TEXT, media_key BLOB, media_key_timestamp INTEGER, file_sha256 TEXT, file_enc_sha256 TEXT, direct_path TEXT);


-- payment_background_order definition

CREATE TABLE payment_background_order (background_id TEXT PRIMARY KEY, background_order INTEGER);


-- payment_extended_metadata definition

CREATE TABLE payment_extended_metadata(message_row_id INTEGER PRIMARY KEY,platform TEXT,type INTEGER,message_params_json TEXT);


-- payment_link_metadata definition

CREATE TABLE payment_link_metadata(message_row_id INTEGER PRIMARY KEY,link_header_type INTEGER,cta_button_text TEXT, params_json TEXT);


-- played_self_receipt definition

CREATE TABLE played_self_receipt(message_row_id INTEGER PRIMARY KEY,to_jid_row_id INTEGER NOT NULL,participant_jid_row_id INTEGER,message_id TEXT NOT NULL);


-- premium_message_info definition

CREATE TABLE premium_message_info(message_row_id INTEGER PRIMARY KEY,campaign_id STRING NOT NULL, chat_row_id INTEGER, account_jid_row_id INTEGER);


-- primary_device_version definition

CREATE TABLE primary_device_version (user_jid_row_id INTEGER PRIMARY KEY, version INTEGER NOT NULL DEFAULT 0);


-- priority_inbox definition

CREATE TABLE priority_inbox (_id INTEGER PRIMARY KEY AUTOINCREMENT, priority_score DOUBLE NOT NULL, version INTEGER NOT NULL, chat_row_id INTEGER NOT NULL, is_priority BOOLEAN, label_removed BOOLEAN, time_created INTEGER, deep_conversion_rate BOOLEAN);


-- props definition

CREATE TABLE props (_id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE, value TEXT);


-- question_reply_quoted_message definition

CREATE TABLE question_reply_quoted_message(message_row_id INTEGER PRIMARY KEY,question_text TEXT,question_message_type INTEGER,response_text TEXT,server_question_id INTEGER);


-- quick_replies definition

CREATE TABLE quick_replies (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT UNIQUE NOT NULL, content TEXT NOT NULL);


-- quick_reply_attachments definition

CREATE TABLE quick_reply_attachments(_id INTEGER PRIMARY KEY AUTOINCREMENT, quick_reply_id TEXT NOT NULL, uri TEXT NOT NULL, caption TEXT, media_type INTEGER);


-- quick_reply_keywords definition

CREATE TABLE quick_reply_keywords (_id INTEGER PRIMARY KEY AUTOINCREMENT, quick_reply_id TEXT NOT NULL, keyword_id TEXT NOT NULL);


-- quick_reply_usage definition

CREATE TABLE quick_reply_usage (_id INTEGER PRIMARY KEY AUTOINCREMENT, quick_reply_id TEXT NOT NULL, usage_date DATE, usage_count INTEGER);


-- receipt_device definition

CREATE TABLE receipt_device (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER NOT NULL, receipt_device_jid_row_id INTEGER NOT NULL, receipt_device_timestamp INTEGER, primary_device_version INTEGER);


-- receipt_orphaned definition

CREATE TABLE receipt_orphaned (_id INTEGER PRIMARY KEY AUTOINCREMENT, chat_row_id INTEGER NOT NULL, from_me INTEGER NOT NULL, key_id TEXT NOT NULL, receipt_device_jid_row_id INTEGER NOT NULL, receipt_recipient_jid_row_id INTEGER, status INTEGER, timestamp INTEGER);


-- receipt_user definition

CREATE TABLE receipt_user (_id INTEGER PRIMARY KEY AUTOINCREMENT, message_row_id INTEGER NOT NULL, receipt_user_jid_row_id INTEGER NOT NULL, receipt_timestamp INTEGER, read_timestamp INTEGER, played_timestamp INTEGER);


-- receipts definition

CREATE TABLE receipts (_id INTEGER PRIMARY KEY AUTOINCREMENT, key_remote_jid TEXT NOT NULL, key_id TEXT NOT NULL, remote_resource TEXT, receipt_device_timestamp INTEGER, read_device_timestamp INTEGER, played_device_timestamp INTEGER);


-- recently_selected_search_table definition

CREATE TABLE recently_selected_search_table(recent_chat_row_id INTEGER PRIMARY KEY,search_timestamp INTEGER);


-- reminder definition

CREATE TABLE reminder(_id INTEGER PRIMARY KEY AUTOINCREMENT,reminder_id TEXT NOT NULL,message_row_id INTEGER,call_log_row_id INTEGER,surface INTEGER NOT NULL,timestamp DATETIME NOT NULL,notified INTEGER NOT NULL DEFAULT 0);


-- reporting_info definition

CREATE TABLE reporting_info(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,reporting_tag BLOB NOT NULL,reporting_token BLOB,reporting_token_content BLOB,reporting_token_version INTEGER,stanza_id STRING,send_timestamp LONG,receive_timestamp LONG, stanza_id_text TEXT, receive_flow INTEGER, is_message_add_on INTEGER);


-- scheduled_calls definition

CREATE TABLE scheduled_calls (creation_message_row_id INTEGER PRIMARY KEY, key_id TEXT NOT NULL, key_from_me INTEGER NOT NULL, key_chat_row_id INTEGER NOT NULL, call_type INTEGER NOT NULL, scheduled_timestamp INTEGER NOT NULL, call_title TEXT NOT NULL, creator_jid_row_id INTEGER NOT NULL, is_upcoming BOOLEAN NOT NULL, call_log_row_id INTEGER);


-- scheduled_reminder_message definition

CREATE TABLE scheduled_reminder_message(message_row_id INTEGER PRIMARY KEY,scheduled_reminder_timestamp_ms INTEGER NOT NULL, chat_row_id INTEGER);


-- smart_suggestions_key_value definition

CREATE TABLE smart_suggestions_key_value(_id INTEGER PRIMARY KEY AUTOINCREMENT,key TEXT UNIQUE,value BLOB);


-- smb_order definition

CREATE TABLE smb_order (_id INTEGER PRIMARY KEY AUTOINCREMENT,status TEXT,jid_row_id INTEGER NOT NULL,reference_id TEXT NOT NULL,transaction_id TEXT,payment_type TEXT,content TEXT);


-- status definition

CREATE TABLE status (_id INTEGER PRIMARY KEY AUTOINCREMENT, jid_row_id INTEGER UNIQUE, message_table_id INTEGER, last_read_message_table_id INTEGER, last_read_receipt_sent_message_table_id INTEGER, first_unread_message_table_id INTEGER, autodownload_limit_message_table_id INTEGER, timestamp INTEGER, unseen_count INTEGER, total_count INTEGER, unseen_count_close_friends INTEGER);


-- status_attribution definition

CREATE TABLE status_attribution(_id INTEGER PRIMARY KEY AUTOINCREMENT,status_row_id INTEGER NOT NULL,type INTEGER NOT NULL,content BLOB);


-- status_crossposting_v3 definition

CREATE TABLE status_crossposting_v3(status_message_row_id INTEGER,crossposting_session_id TEXT,crossposting_status_unique_id TEXT,state INTEGER,media_file_path TEXT,direct_url_path TEXT,destination INTEGER,PRIMARY KEY (status_message_row_id, destination));


-- status_info_ranking_signals definition

CREATE TABLE status_info_ranking_signals(chat_jid TEXT PRIMARY KEY NOT NULL,first_status_timestamp INTEGER NOT NULL DEFAULT 0,last_expired_status_timestamp INTEGER NOT NULL DEFAULT 0, cached_engagement_data BLOB, cached_engagement_timestamp INTEGER);


-- status_list definition

CREATE TABLE status_list (_id INTEGER PRIMARY KEY AUTOINCREMENT, key_remote_jid TEXT UNIQUE, message_table_id INTEGER, last_read_message_table_id INTEGER, last_read_receipt_sent_message_table_id INTEGER, timestamp INTEGER, unseen_count INTEGER, total_count INTEGER, first_unread_message_table_id INTEGER, autodownload_limit_message_table_id INTEGER);


-- status_message_info definition

CREATE TABLE status_message_info(message_row_id INTEGER PRIMARY KEY,status_distribution_mode INTEGER NOT NULL, is_mentioned INTEGER, status_mentions TEXT, cannot_receive_reactions INTEGER, cannot_be_ranked INTEGER, has_embedded_music INTEGER, status_mention_source TEXT, status_attribution_type INTEGER, is_group_status INTEGER, can_be_reshared INTEGER, ranking_version INTEGER, external_media_duration_seconds INTEGER, original_status_message_row_id INTEGER, original_poster_notification_type INTEGER, status_source_type INTEGER, selected_audience_list TEXT, override_notification_recipient_jid TEXT, can_receive_multi_reactions INTEGER, audience_type INTEGER, status_poster_contact_type INTEGER, status_audience_custom_list_name TEXT, status_audience_custom_list_emoji TEXT);


-- status_notification_info definition

CREATE TABLE status_notification_info(message_row_id INTEGER PRIMARY KEY,response_status_row_id INTEGER NOT NULL,original_status_row_id INTEGER NOT NULL,type INTEGER NOT NULL);


-- status_privacy_custom_list definition

CREATE TABLE status_privacy_custom_list(row_id INTEGER PRIMARY KEY AUTOINCREMENT,list_id TEXT NOT NULL,name TEXT,emoji TEXT,is_selected INTEGER NOT NULL DEFAULT 0,member_jids TEXT);


-- status_quoted_message definition

CREATE TABLE status_quoted_message(message_row_id INTEGER PRIMARY KEY,description_text TEXT NOT NULL,thumbnail BLOB,type INTEGER,original_status_key_id TEXT,original_status_is_from_me INTEGER,original_status_chat_id TEXT,original_status_sender_id TEXT,add_on_key_id TEXT,add_on_is_from_me INTEGER,add_on_chat_id TEXT,add_on_sender_id TEXT);


-- suggest_as_you_type definition

CREATE TABLE suggest_as_you_type(message_row_id INTEGER PRIMARY KEY);


-- suggested_replies definition

CREATE TABLE suggested_replies (message_row_id INTEGER PRIMARY KEY, customer_message_row_id INTEGER NOT NULL, tokenized_customer_message STRING, customer_message_embedding BLOB);


-- support_citation_metadata definition

CREATE TABLE support_citation_metadata(message_row_id INTEGER PRIMARY KEY,help_article_citations TEXT);


-- tee_chat_request_table definition

CREATE TABLE tee_chat_request_table(message_row_id INTEGER PRIMARY KEY NOT NULL,chat_request_type TEXT NOT NULL);


-- tee_message_info_table definition

CREATE TABLE tee_message_info_table(message_row_id INTEGER PRIMARY KEY NOT NULL,message_interaction_type INTEGER,message_outgoing_status INTEGER,message_source INTEGER NOT NULL,message_replay_metadata BLOB);


-- template_messages_metadata definition

CREATE TABLE template_messages_metadata(message_row_id INTEGER PRIMARY KEY,message_template_id TEXT,message_hsm_tag TEXT, message_source_type TEXT, message_delivery_decision_id TEXT, message_delivery_decision_sources TEXT);


-- thread_id definition

CREATE TABLE thread_id(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,chat_row_id INTEGER NOT NULL,from_me INTEGER NOT NULL,key_id TEXT NOT NULL,sender_jid_row_id INTEGER NOT NULL DEFAULT 0,thread_type INTEGER NOT NULL DEFAULT 0, pin_timestamp INTEGER, deleted INTEGER NOT NULL DEFAULT 0);


-- thread_messages definition

CREATE TABLE thread_messages(_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,thread_id INTEGER NOT NULL,message_row_id INTEGER NOT NULL);


-- transcription_segment definition

CREATE TABLE transcription_segment(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,substring_start INTEGER NOT NULL,substring_length INTEGER NOT NULL,timestamp INTEGER,duration INTEGER,confidence INTEGER);


-- url_tracking_map_element definition

CREATE TABLE url_tracking_map_element(_id INTEGER PRIMARY KEY AUTOINCREMENT,message_row_id INTEGER NOT NULL,original_url TEXT,consented_users_url TEXT,unconsented_users_url TEXT,card_index INTEGER);


-- user_device definition

CREATE TABLE user_device (_id INTEGER PRIMARY KEY AUTOINCREMENT, user_jid_row_id INTEGER, device_jid_row_id INTEGER, key_index INTEGER NOT NULL DEFAULT 0);


-- user_device_info definition

CREATE TABLE user_device_info (user_jid_row_id INTEGER PRIMARY KEY, raw_id INTEGER NOT NULL, timestamp INTEGER NOT NULL, expected_timestamp INTEGER, expected_ts_last_device_job_ts INTEGER, expected_timestamp_update_ts INTEGER, account_encryption_type INTEGER);