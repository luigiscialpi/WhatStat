-- android_metadata definition

CREATE TABLE android_metadata (locale TEXT);


-- community_settings definition

CREATE TABLE community_settings(community_raw_jid TEXT NOT NULL,subgroup_member_count_poll_time INTEGER, suggested_groups_view_time_seconds INTEGER, nesting_state INTEGER);

CREATE UNIQUE INDEX community_settings_raw_jid_index ON community_settings(community_raw_jid);


-- dismissed_chat definition

CREATE TABLE dismissed_chat(chat_jid TEXT PRIMARY KEY,timestamp INTEGER NOT NULL);


-- dismissed_contacts definition

CREATE TABLE dismissed_contacts(jid TEXT PRIMARY KEY NOT NULL,timestamp INTEGER NOT NULL);


-- dismissed_suggested_contacts definition

CREATE TABLE dismissed_suggested_contacts(jid TEXT PRIMARY KEY NOT NULL,timestamp INTEGER NOT NULL);


-- dogfooder_diagnostics_events definition

CREATE TABLE dogfooder_diagnostics_events(_id INTEGER PRIMARY KEY AUTOINCREMENT,event_type TEXT NOT NULL,event_message TEXT,event_timestamp INTEGER);

CREATE INDEX dogfooder_event_type_message_index
ON dogfooder_diagnostics_events (event_type, event_message);


-- foa_crosspost definition

CREATE TABLE foa_crosspost(crosspost_request_id TEXT PRIMARY KEY NOT NULL,creation_time INTEGER NOT NULL,status_id TEXT);


-- group_membership_approval_requests definition

CREATE TABLE group_membership_approval_requests(group_jid TEXT NOT NULL,requester_jid TEXT NOT NULL,request_method TEXT NOT NULL,request_creation_time INTEGER,parent_group_jid TEXT,requested_by_jid TEXT,PRIMARY KEY (group_jid,requester_jid));

CREATE INDEX request_creation_time_index ON group_membership_approval_requests(request_creation_time);


-- group_membership_count definition

CREATE TABLE group_membership_count(jid_row_id INTEGER PRIMARY KEY,member_count INTEGER NOT NULL);


-- group_non_wa_invites definition

CREATE TABLE group_non_wa_invites(_id INTEGER PRIMARY KEY AUTOINCREMENT,group_jid TEXT NOT NULL,invitee_jid TEXT NOT NULL,invite_ts INTEGER NOT NULL);

CREATE UNIQUE INDEX group_jid_invitee_jid_unique ON group_non_wa_invites (group_jid, invitee_jid);
CREATE INDEX invite_ts ON group_non_wa_invites (invite_ts);


-- group_relationship definition

CREATE TABLE group_relationship(parent_raw_jid TEXT NOT NULL,subgroup_raw_id TEXT NOT NULL);

CREATE INDEX group_relationship_parent_raw_jid_index ON group_relationship (parent_raw_jid);
CREATE UNIQUE INDEX group_relationship_subgroup_raw_jid_index ON group_relationship (subgroup_raw_id);


-- group_suspension_appeal definition

CREATE TABLE group_suspension_appeal(jid TEXT PRIMARY KEY,appeal_status INTEGER NOT NULL,appeal_update_time INTEGER NOT NULL);


-- member_suggested_groups_v2 definition

CREATE TABLE member_suggested_groups_v2(group_jid TEXT NOT NULL,parent_group_jid TEXT NOT NULL,subject TEXT NOT NULL,description TEXT,creator_jid TEXT NOT NULL,request_creation_time INTEGER NOT NULL,participant_count INTEGER NOT NULL DEFAULT 0,is_existing_group BOOLEAN NOT NULL DEFAULT 0,is_hidden_subgroup INTEGER,PRIMARY KEY (parent_group_jid,group_jid,creator_jid));


-- meta_ai_memory definition

CREATE TABLE meta_ai_memory(memory_id TEXT PRIMARY KEY,memory_content TEXT NOT NULL, bot_jid_row_id INTEGER);

CREATE INDEX meta_ai_memory_memory_content_index ON meta_ai_memory (memory_content);


-- mm_notification_impression definition

CREATE TABLE mm_notification_impression(chat_lid TEXT PRIMARY KEY,impression_counter INTEGER NOT NULL DEFAULT 0,timestamp INTEGER NOT NULL);


-- non_admin_group_membership_approval_requests definition

CREATE TABLE non_admin_group_membership_approval_requests(group_jid TEXT NOT NULL,requested_by_jid TEXT NOT NULL,requested_for_jid TEXT NOT NULL,request_creation_time INTEGER,PRIMARY KEY (group_jid, requested_by_jid, requested_for_jid));

CREATE INDEX membership_request_creation_time_index ON non_admin_group_membership_approval_requests(request_creation_time);


-- profile_links definition

CREATE TABLE profile_links(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,type TEXT NOT NULL,username TEXT NOT NULL, vid TEXT, url TEXT,UNIQUE (jid, type, username) ON CONFLICT REPLACE);

CREATE INDEX profile_links_jid_index ON profile_links (jid);


-- quick_promotion_payload definition

CREATE TABLE quick_promotion_payload(surface_id INTEGER NOT NULL,trigger_id TEXT NOT NULL,trigger_context TEXT,qp_details BLOB NOT NULL,insertion_time DATETIME NOT NULL);

CREATE UNIQUE INDEX quick_promotion_payload_index ON quick_promotion_payload (surface_id, trigger_id);


-- recently_accepted_deeplink_invites definition

CREATE TABLE recently_accepted_deeplink_invites(user_jid TEXT NOT NULL,invite_accepted_time INTEGER,invite_receiver_reason TEXT,PRIMARY KEY (user_jid));

CREATE INDEX user_jid_index ON recently_accepted_deeplink_invites (user_jid);


-- start_chat_trust_signals definition

CREATE TABLE start_chat_trust_signals(jid TEXT PRIMARY KEY NOT NULL,is_sender_suspicious INTEGER,is_sender_new_account INTEGER,created_ts REAL);


-- status_ranking definition

CREATE TABLE status_ranking(_id INTEGER PRIMARY KEY AUTOINCREMENT,total_impressions_per_day INTEGER NOT NULL,total_clicks_per_day INTEGER NOT NULL,jid TEXT NOT NULL,date_time DATETIME NOT NULL, total_views_per_day INTEGER, total_likes_per_day INTEGER, total_text_replies_per_day INTEGER, total_quick_replies_per_day INTEGER, lid_jid TEXT, total_reshares_per_day INTEGER);

CREATE INDEX date_and_jid_index
ON status_ranking(jid, date_time);
CREATE INDEX date_index
      ON status_ranking(date_time);
CREATE INDEX date_and_lid_index
      ON status_ranking(lid_jid, date_time);


-- subgroup_info definition

CREATE TABLE subgroup_info(subgroup_raw_jid TEXT NOT NULL,subject TEXT NOT NULL,subject_ts INTEGER,group_type INTEGER NOT NULL DEFAULT 2,group_membership_approval_state INTEGER,linking_timestamp INTEGER,last_known_member_count INTEGER);

CREATE UNIQUE INDEX subgroup_raw_jid_index ON subgroup_info (subgroup_raw_jid);


-- system_contacts_version_table definition

CREATE TABLE system_contacts_version_table(id INTEGER PRIMARY KEY,version INTEGER);


-- wa_about_block_list definition

CREATE TABLE wa_about_block_list(jid TEXT NOT NULL);


-- wa_address_book definition

CREATE TABLE wa_address_book(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT,is_whatsapp_user BOOLEAN,company TEXT,display_name TEXT,family_name TEXT,given_name TEXT,is_contact_synced INTEGER,is_starred BOOLEAN,nickname TEXT,number TEXT,phone_label TEXT,phone_type INTEGER,raw_contact_id INTEGER,sort_name TEXT,sync_policy INTEGER,title TEXT);

CREATE INDEX address_book_is_wa_index ON wa_address_book (is_whatsapp_user);
CREATE INDEX address_book_jid_index ON wa_address_book (jid);
CREATE INDEX address_book_raw_contact_id_index ON wa_address_book (raw_contact_id);


-- wa_biz_brand_id_list definition

CREATE TABLE wa_biz_brand_id_list(jid TEXT NOT NULL,brand_id TEXT NOT NULL);

CREATE UNIQUE INDEX brand_id_list_jid_index
        ON wa_biz_brand_id_list (jid, brand_id);


-- wa_biz_category_service_offerings definition

CREATE TABLE wa_biz_category_service_offerings(_id TEXT PRIMARY KEY NOT NULL,category_id TEXT NOT NULL,category_name TEXT NOT NULL,offering_name TEXT NOT NULL);

CREATE UNIQUE INDEX biz_category_id_service_offerings_index ON wa_biz_category_service_offerings(_id, category_id);


-- wa_biz_integrity_chat_settings definition

CREATE TABLE wa_biz_integrity_chat_settings(jid TEXT PRIMARY KEY NOT NULL,last_delivered_sort_id LONG NOT NULL,last_read_sort_id LONG NOT NULL);

CREATE UNIQUE INDEX biz_integrity_chat_settings_index
        ON wa_biz_integrity_chat_settings (jid);


-- wa_biz_integrity_signals definition

CREATE TABLE wa_biz_integrity_signals(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,dhash TEXT,fb_linked_page_number_of_likes REAL,ig_linked_page_number_of_followers REAL,is_suspicious INTEGER DEFAULT 0,join_date_ms REAL,is_banned INTEGER DEFAULT 0,phone_country_code TEXT,trust_tier TEXT, mv_friction_eligibility INTEGER, integrity_tags TEXT, chat_row_id INTEGER, last_sync_ts REAL);

CREATE UNIQUE INDEX biz_integrity_signals_jid_index ON wa_biz_integrity_signals (jid);
CREATE UNIQUE INDEX biz_integrity_signals_chat_row_id_index ON wa_biz_integrity_signals (chat_row_id);


-- wa_biz_profile_to_service_offerings definition

CREATE TABLE wa_biz_profile_to_service_offerings(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,wa_biz_category_service_offerings_id TEXT NOT NULL,is_offered BOOLEAN NOT NULL);

CREATE UNIQUE INDEX biz_profile_id_service_offerings_index ON wa_biz_profile_to_service_offerings (wa_biz_profile_id, wa_biz_category_service_offerings_id);


-- wa_biz_profiles definition

CREATE TABLE wa_biz_profiles(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,email TEXT,address TEXT,business_description TEXT,latitude REAL,longitude REAL,tag TEXT,vertical TEXT,time_zone TEXT,hours_note TEXT,has_catalog BOOLEAN DEFAULT 0,address_postal_code TEXT,address_city_id TEXT,address_city_name TEXT,commerce_experience TEXT,shop_url TEXT,cart_enabled BOOLEAN DEFAULT 0,commerce_manager_url TEXT,direct_connection_enabled BOOLEAN DEFAULT 0,is_shop_banned BOOLEAN DEFAULT 0,default_postcode TEXT,location_name TEXT,galaxy_business_enabled BOOLEAN DEFAULT 0,cover_photo_url TEXT,cover_photo_id TEXT,custom_url TEXT,member_since TEXT,capi_calling_enabled BOOLEAN DEFAULT 0,is_responsive BOOLEAN DEFAULT 0,postcode_type TEXT,price_tier_id TEXT,business_blocked_status TEXT,survey_sampling_rate INTEGER,is_offerings_eligible BOOLEAN DEFAULT 0,automated_type INTEGER,welcome_message_protocol_mode INTEGER,is_typing_indicator_enabled INTEGER,calling_hidden_entry_points INTEGER, business_has_shopping_flow INTEGER, is_callback_permissions_enabled INTEGER, call_hours_time_zone TEXT, call_hours_unavailable_message TEXT, is_business_initiated_calling_enabled INTEGER, business_call_permission_params TEXT, automated_greeting_message_body TEXT, automated_greeting_message_type TEXT, automated_greeting_message_payload TEXT, is_video_calling_enabled BOOLEAN DEFAULT 0, limit_to_user_countries TEXT, call_icon_visibility INTEGER, is_authorized_agent BOOLEAN DEFAULT 0, parent_company_name TEXT, parent_company_logo_url TEXT, oba_phone_number TEXT, hide_pn BOOLEAN DEFAULT 0);

CREATE UNIQUE INDEX biz_profile_jid_index ON wa_biz_profiles (jid);


-- wa_biz_profiles_bot_attributes definition

CREATE TABLE wa_biz_profiles_bot_attributes(wa_biz_profile_id INTEGER PRIMARY KEY NOT NULL,sub_description TEXT,commands TEXT,prompts TEXT,commands_description TEXT,bot_description TEXT);


-- wa_biz_profiles_call_hours definition

CREATE TABLE wa_biz_profiles_call_hours(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,day_of_week INTEGER NOT NULL,holiday_date TEXT,unavailable_message TEXT,mode INTEGER NOT NULL,start_time INTEGER NOT NULL DEFAULT 0,end_time INTEGER NOT NULL DEFAULT 0);

CREATE INDEX biz_profile_id_call_hours_index
            ON wa_biz_profiles_call_hours(wa_biz_profile_id);


-- wa_biz_profiles_categories definition

CREATE TABLE wa_biz_profiles_categories(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,category_id TEXT NOT NULL,category_name TEXT NOT NULL);

CREATE INDEX biz_profile_id_category_index ON wa_biz_profiles_categories(wa_biz_profile_id, category_id);


-- wa_biz_profiles_dc_enabled_features definition

CREATE TABLE wa_biz_profiles_dc_enabled_features(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,catalog_feature_type TEXT);

CREATE INDEX dc_enabled_features_biz_profile_id_index ON wa_biz_profiles_dc_enabled_features(wa_biz_profile_id);


-- wa_biz_profiles_direct_connection_allowed_country_codes definition

CREATE TABLE wa_biz_profiles_direct_connection_allowed_country_codes(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,country_code TEXT);

CREATE INDEX biz_profile_id_index ON wa_biz_profiles_direct_connection_allowed_country_codes(wa_biz_profile_id);


-- wa_biz_profiles_hours definition

CREATE TABLE wa_biz_profiles_hours(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,day_of_week INTEGER,mode INTEGER,open_time INTEGER,close_time INTEGER);

CREATE INDEX biz_profile_id_hours_index ON wa_biz_profiles_hours(wa_biz_profile_id);


-- wa_biz_profiles_linked_accounts_table definition

CREATE TABLE wa_biz_profiles_linked_accounts_table(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,account_id TEXT NOT NULL,account_type INTEGER NOT NULL,account_display_name TEXT NOT NULL,account_fan_count INTEGER NOT NULL,account_has_media_post INTEGER NOT NULL DEFAULT 0);

CREATE INDEX wa_biz_profiles_linked_accounts_index ON wa_biz_profiles_linked_accounts_table(wa_biz_profile_id);


-- wa_biz_profiles_price_tiers definition

CREATE TABLE wa_biz_profiles_price_tiers(_id TEXT PRIMARY KEY,name TEXT,symbol TEXT);

CREATE INDEX biz_profile_id_price_tier_index ON wa_biz_profiles_price_tiers(_id);


-- wa_biz_profiles_service_areas definition

CREATE TABLE wa_biz_profiles_service_areas(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,area_description TEXT,radius INTEGER NOT NULL,center_latitude REAL NOT NULL,center_longitude REAL NOT NULL);

CREATE INDEX biz_profile_id_service_area_index ON wa_biz_profiles_service_areas(wa_biz_profile_id);


-- wa_biz_profiles_websites definition

CREATE TABLE wa_biz_profiles_websites(_id INTEGER PRIMARY KEY AUTOINCREMENT,wa_biz_profile_id INTEGER NOT NULL,websites TEXT);

CREATE INDEX biz_profile_id_website_index ON wa_biz_profiles_websites(wa_biz_profile_id, websites);


-- wa_block_list definition

CREATE TABLE wa_block_list(jid TEXT NOT NULL);

CREATE UNIQUE INDEX block_list_jid_index ON wa_block_list (jid);


-- wa_block_list_interop definition

CREATE TABLE wa_block_list_interop(jid TEXT NOT NULL);

CREATE UNIQUE INDEX interop_block_list_jid_index ON wa_block_list_interop (jid);


-- wa_bot_commands definition

CREATE TABLE wa_bot_commands(_id INTEGER PRIMARY KEY AUTOINCREMENT,bot_jid TEXT NOT NULL,command_id TEXT NOT NULL,name TEXT NOT NULL,description TEXT NOT NULL,prompt TEXT);


-- wa_bot_profiles definition

CREATE TABLE wa_bot_profiles(jid TEXT PRIMARY KEY NOT NULL,tag INTEGER DEFAULT 0,name TEXT,is_default BOOLEAN,attributes TEXT,description TEXT,category TEXT,prompts TEXT,behavior_graph TEXT,persona_id TEXT,commands_description TEXT,commands TEXT,avatar_url TEXT,is_meta_created INTEGER,creator_name TEXT,creator_profile_url TEXT, card_title TEXT, count INTEGER, capabilities LONG, last_updated_time_ms LONG, created_by_me INTEGER, is_created_on_whatsapp INTEGER, is_creator_verified INTEGER, is_voice_enabled INTEGER, animated_background_image_url TEXT, animated_foreground_image_url TEXT, welcome_message_video_intro_uri TEXT, is_posing_as_professional INTEGER, parody_label TEXT, proactive_message_control_status INTEGER, profile_pic_thumb_url TEXT, profile_pic_full_url TEXT, is_embodiment_enabled INTEGER);


-- wa_chat_ranking_scores definition

CREATE TABLE wa_chat_ranking_scores(_id INTEGER PRIMARY KEY AUTOINCREMENT,model_id INTEGER NOT NULL,chat_jid TEXT NOT NULL,ranking_score DOUBLE NOT NULL,ranking_score_update_ts DATETIME NOT NULL);

CREATE INDEX wa_chat_ranking_scores_model_and_jid_index
      ON wa_chat_ranking_scores(model_id, chat_jid);
CREATE INDEX wa_chat_ranking_scores_model_and_score_index
      ON wa_chat_ranking_scores(model_id, ranking_score);


-- wa_coex_properties definition

CREATE TABLE wa_coex_properties(jid TEXT PRIMARY KEY NOT NULL,calling_non_e2ee_disclaimer_seen INTEGER NOT NULL DEFAULT 0, calling_non_e2ee_incoming_label_seen INTEGER NOT NULL DEFAULT 0);


-- wa_contact_server_signals definition

CREATE TABLE wa_contact_server_signals(_id INTEGER PRIMARY KEY AUTOINCREMENT,chat_jid TEXT NOT NULL,signal_type INTEGER NOT NULL,value DOUBLE NOT NULL,update_ts INTEGER NOT NULL);

CREATE UNIQUE INDEX contact_server_signals_jid_signal_index
      ON wa_contact_server_signals(chat_jid, signal_type);


-- wa_contact_storage_usage definition

CREATE TABLE wa_contact_storage_usage(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,conversation_size INTEGER NOT NULL,conversation_message_count INTEGER NOT NULL);

CREATE INDEX wa_contact_storage_usage_index ON wa_contact_storage_usage (jid, conversation_size DESC);


-- wa_contacts definition

CREATE TABLE wa_contacts(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,is_whatsapp_user BOOLEAN NOT NULL,status TEXT,status_timestamp INTEGER,number TEXT,raw_contact_id INTEGER,display_name TEXT,phone_type INTEGER,phone_label TEXT,photo_ts INTEGER,thumb_ts INTEGER,photo_id_timestamp INTEGER,given_name TEXT,family_name TEXT,wa_name TEXT,sort_name TEXT,nickname TEXT,company TEXT,title TEXT,status_autodownload_disabled INTEGER,keep_timestamp INTEGER,is_spam_reported INTEGER,is_sidelist_synced BOOLEAN DEFAULT 0,is_business_synced BOOLEAN DEFAULT 0,disappearing_mode_duration INTEGER,disappearing_mode_timestamp LONG,history_sync_initial_phash TEXT,is_starred BOOLEAN,is_wa_created_contact BOOLEAN,sync_policy INTEGER,status_emoji TEXT,is_contact_synced INTEGER, is_reachable INTEGER, external_user_state INTEGER, disappearing_mode_support_disabled INTEGER);

CREATE INDEX is_wa_index ON wa_contacts (is_whatsapp_user);
CREATE INDEX jid_index ON wa_contacts (jid);
CREATE INDEX is_contact_synced_index ON wa_contacts (is_contact_synced);


-- wa_contacts_created_within_wa definition

CREATE TABLE wa_contacts_created_within_wa(_id TEXT PRIMARY KEY NOT NULL);


-- wa_contacts_fts definition

CREATE VIRTUAL TABLE wa_contacts_fts USING FTS4 (
            search_content,
            fts_namespace
          );


-- wa_contacts_fts_content definition

CREATE TABLE 'wa_contacts_fts_content'(docid INTEGER PRIMARY KEY, 'c0search_content', 'c1fts_namespace');


-- wa_contacts_fts_docsize definition

CREATE TABLE 'wa_contacts_fts_docsize'(docid INTEGER PRIMARY KEY, size BLOB);


-- wa_contacts_fts_segdir definition

CREATE TABLE 'wa_contacts_fts_segdir'(level INTEGER,idx INTEGER,start_block INTEGER,leaves_end_block INTEGER,end_block INTEGER,root BLOB,PRIMARY KEY(level, idx));


-- wa_contacts_fts_segments definition

CREATE TABLE 'wa_contacts_fts_segments'(blockid INTEGER PRIMARY KEY, block BLOB);


-- wa_contacts_fts_stat definition

CREATE TABLE 'wa_contacts_fts_stat'(id INTEGER PRIMARY KEY, value BLOB);


-- wa_cover_photo_block_list definition

CREATE TABLE wa_cover_photo_block_list(jid TEXT NOT NULL);


-- wa_group_add_allow_list definition

CREATE TABLE wa_group_add_allow_list(jid TEXT NOT NULL);

CREATE UNIQUE INDEX group_add_allow_list_jid_index 
            ON wa_group_add_allow_list (jid);


-- wa_group_add_black_list definition

CREATE TABLE wa_group_add_black_list(jid TEXT NOT NULL);

CREATE UNIQUE INDEX group_add_black_list_jid_index ON wa_group_add_black_list (jid);


-- wa_group_admin_settings definition

CREATE TABLE wa_group_admin_settings(jid TEXT NOT NULL,restrict_mode BOOLEAN NOT NULL,announcement_group BOOLEAN NOT NULL DEFAULT 0,no_frequently_forwarded BOOLEAN NOT NULL DEFAULT 0,ephemeral_duration INTEGER DEFAULT NULL,creator_jid TEXT,in_app_support BOOLEAN NOT NULL DEFAULT 0,is_suspended BOOLEAN,group_state INTEGER NOT NULL DEFAULT 0,require_membership_approval BOOLEAN NOT NULL DEFAULT 0,member_add_mode INTEGER NOT NULL DEFAULT 0,incognito BOOLEAN,is_pending_requests_banner_acknowledged BOOLEAN NOT NULL DEFAULT 0,is_report_to_admin_enabled BOOLEAN,addressing_mode TEXT NOT NULL DEFAULT 'pn',allow_non_admin_subgroup_creation BOOLEAN,is_pending_suggestions_banner_acknowledged BOOLEAN NOT NULL DEFAULT 0,is_name_group_banner_dismissed BOOLEAN NOT NULL DEFAULT 0,history_enabled BOOLEAN,auto_add_disabled BOOLEAN,is_hidden_subgroup INTEGER,e2ee_state INTEGER, member_link_mode INTEGER NOT NULL DEFAULT 0, allow_member_labels INTEGER NOT NULL DEFAULT 0, move_to_suspend_folder INTEGER, group_history_toggle_mode INTEGER, is_empty_group_banner_acknowledged BOOLEAN, group_join_via_link_system_message_displayed_at INTEGER, share_group_history_setting_mode INTEGER);

CREATE UNIQUE INDEX group_admin_settings_jid_index ON wa_group_admin_settings (jid);


-- wa_group_descriptions definition

CREATE TABLE wa_group_descriptions(jid TEXT NOT NULL,description TEXT NOT NULL,description_id INTEGER,description_time INTEGER,description_setter_jid TEXT NOT NULL,description_id_string TEXT NOT NULL DEFAULT '');

CREATE UNIQUE INDEX wa_group_descriptions_jid_index ON wa_group_descriptions(jid);


-- wa_last_entry_point definition

CREATE TABLE wa_last_entry_point(jid TEXT NOT NULL,entry_point_type TEXT NOT NULL,entry_point_id TEXT,entry_point_time INTEGER, first_entry_point_type TEXT);

CREATE UNIQUE INDEX entry_point_jid_index ON wa_last_entry_point (jid);


-- wa_last_seen_block_list definition

CREATE TABLE wa_last_seen_block_list(jid TEXT NOT NULL);


-- wa_logging_entry_point definition

CREATE TABLE wa_logging_entry_point(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT,lid TEXT,entry_point_type INTEGER NOT NULL,timestamp INTEGER NOT NULL,payload BLOB);

CREATE UNIQUE INDEX logging_entry_point_jid_index ON wa_logging_entry_point (jid, entry_point_type);
CREATE UNIQUE INDEX logging_entry_point_lid_index ON wa_logging_entry_point (lid, entry_point_type);
CREATE INDEX logging_entry_point_timestamp_index ON wa_logging_entry_point (entry_point_type, timestamp);


-- wa_ml_models definition

CREATE TABLE wa_ml_models(_id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,version INTEGER NOT NULL,content BLOB NOT NULL);

CREATE UNIQUE INDEX ml_model_name_version_index
      ON wa_ml_models(name, version);


-- wa_newsletter_props definition

CREATE TABLE wa_newsletter_props(_id INTEGER PRIMARY KEY AUTOINCREMENT,prop_name TEXT UNIQUE,prop_value TEXT);


-- wa_pix_block_list definition

CREATE TABLE wa_pix_block_list(jid TEXT NOT NULL);


-- wa_profile_links_deny_list definition

CREATE TABLE wa_profile_links_deny_list(jid TEXT NOT NULL);


-- wa_profile_photo_block_list definition

CREATE TABLE wa_profile_photo_block_list(jid TEXT NOT NULL);


-- wa_props definition

CREATE TABLE wa_props(_id INTEGER PRIMARY KEY AUTOINCREMENT,prop_name TEXT UNIQUE,prop_value TEXT);


-- wa_stickers_allow_list definition

CREATE TABLE wa_stickers_allow_list(jid TEXT NOT NULL);


-- wa_storage_monitoring_data definition

CREATE TABLE wa_storage_monitoring_data(_id INTEGER PRIMARY KEY AUTOINCREMENT,db_name TEXT NOT NULL,table_name TEXT NOT NULL,table_size_bytes INTEGER,index_size_bytes INTEGER, table_unused_bytes INTEGER, index_unused_bytes INTEGER);

CREATE UNIQUE INDEX wa_storage_monitoring_data_db_table_index
            ON wa_storage_monitoring_data (db_name, table_name);


-- wa_storage_monitoring_jobs definition

CREATE TABLE wa_storage_monitoring_jobs(_id INTEGER PRIMARY KEY AUTOINCREMENT,db_name TEXT NOT NULL,collection_timestamp INTEGER,status TEXT);

CREATE UNIQUE INDEX wa_storage_monitoring_jobs_db_name_index
            ON wa_storage_monitoring_jobs (db_name);


-- wa_subscriptions definition

CREATE TABLE wa_subscriptions(id TEXT PRIMARY KEY NOT NULL,status TEXT NOT NULL,start_time DATETIME,end_time DATETIME,is_platform_changed INTEGER DEFAULT 0,subscription_source TEXT,creation_time DATETIME,tier INTEGER);

CREATE INDEX status_subscription_source_composite_index ON wa_subscriptions (status,subscription_source);


-- wa_trusted_contacts definition

CREATE TABLE wa_trusted_contacts(jid TEXT PRIMARY KEY NOT NULL,incoming_tc_token BLOB NOT NULL,incoming_tc_token_timestamp LONG NOT NULL);

CREATE INDEX incoming_tc_token_timestamp_index ON wa_trusted_contacts (incoming_tc_token_timestamp);


-- wa_trusted_contacts_send definition

CREATE TABLE wa_trusted_contacts_send(jid TEXT PRIMARY KEY NOT NULL,sent_tc_token_timestamp LONG NOT NULL,real_issue_timestamp LONG);

CREATE INDEX sent_tc_token_timestamp_index ON wa_trusted_contacts_send (sent_tc_token_timestamp);
CREATE INDEX sent_real_issue_timestamp_index ON wa_trusted_contacts_send (real_issue_timestamp);


-- wa_vnames definition

CREATE TABLE wa_vnames(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,serial INTEGER NOT NULL,issuer TEXT NOT NULL,expires INTEGER,verified_name TEXT NOT NULL,industry TEXT,city TEXT,country TEXT,verified_level INTEGER,identity_unconfirmed_since INTEGER,cert_blob BLOB,host_storage INTEGER DEFAULT 0,actual_actors INTEGER DEFAULT 0,privacy_mode_ts INTEGER DEFAULT 0);

CREATE UNIQUE INDEX vname_jid_index ON wa_vnames (jid);


-- wa_vnames_localized definition

CREATE TABLE wa_vnames_localized(_id INTEGER PRIMARY KEY AUTOINCREMENT,jid TEXT NOT NULL,lg TEXT NOT NULL,lc TEXT NOT NULL,verified_name TEXT NOT NULL);

CREATE UNIQUE INDEX vname_localized_index ON wa_vnames_localized (jid, lg, lc);