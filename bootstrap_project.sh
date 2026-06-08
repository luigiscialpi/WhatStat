#!/bin/bash

set -euo pipefail

log_info() {
    echo "[INFO] $*" >&2
}

log_error() {
    echo "[ERROR] $*" >&2
}

log_success() {
    echo "[OK] $*" >&2
}

ensure_android_sdk_env() {
    if [ -z "${ANDROID_HOME:-}" ]; then
        if [ -d "$HOME/Library/Android/sdk" ]; then
            export ANDROID_HOME="$HOME/Library/Android/sdk"
        elif [ -d "$(pwd)/android-sdk" ]; then
            export ANDROID_HOME="$(pwd)/android-sdk"
        else
            log_error "ANDROID_HOME non impostata e SDK non trovato."
            log_error "Imposta ANDROID_HOME oppure installa Android SDK."
            exit 1
        fi
    fi

    export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
}

require_command() {
    local cmd_name="$1"
    if ! command -v "$cmd_name" >/dev/null 2>&1; then
        log_error "Comando mancante: $cmd_name"
        exit 1
    fi
}

wait_for_emulator() {
    local emulator_serial="$1"

    log_info "Attendo boot completo per $emulator_serial..."
    adb -s "$emulator_serial" wait-for-device

    local boot_completed=""
    until [ "$boot_completed" = "1" ]; do
        boot_completed=$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
        [ "$boot_completed" = "1" ] || sleep 2
    done

    log_success "Emulatore pronto: $emulator_serial"
}

get_physical_device() {
    adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}'
}

get_running_emulator() {
    adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1; exit}'
}

start_emulator() {
    local avd_list=()

    while IFS= read -r line; do
        [ -n "$line" ] && avd_list+=("$line")
    done < <(emulator -list-avds)

    if [ ${#avd_list[@]} -eq 0 ]; then
        log_error "Nessun AVD disponibile. Creane uno in Android Studio."
        exit 1
    fi

    echo "Emulatori disponibili:" >&2
    for i in "${!avd_list[@]}"; do
        echo "  $((i + 1))) ${avd_list[$i]}" >&2
    done

    local avd_choice
    read -r -p "Scegli emulatore [1-${#avd_list[@]}]: " avd_choice

    if ! [[ "$avd_choice" =~ ^[0-9]+$ ]] || [ "$avd_choice" -lt 1 ] || [ "$avd_choice" -gt ${#avd_list[@]} ]; then
        log_error "Scelta non valida."
        exit 1
    fi

    local avd_name="${avd_list[$((avd_choice - 1))]}"
    log_info "Avvio emulatore: $avd_name"
    nohup emulator -avd "$avd_name" -no-snapshot-load -no-boot-anim >/dev/null 2>&1 &

    local emulator_serial=""
    until [ -n "$emulator_serial" ]; do
        emulator_serial=$(get_running_emulator)
        [ -n "$emulator_serial" ] || sleep 2
    done

    wait_for_emulator "$emulator_serial"
    echo "$emulator_serial"
}

install_debug_on_target() {
    local target_serial="$1"
    log_info "Installo build Debug su target: $target_serial"

    ANDROID_SERIAL="$target_serial" ./gradlew :app:installDebug

    log_success "Installazione completata su $target_serial"
}

install_release_on_target() {
    local target_serial="$1"
    log_info "Installo build Release su target: $target_serial"

    ANDROID_SERIAL="$target_serial" ./gradlew :app:installRelease

    log_success "Installazione completata su $target_serial"
}

launch_app_on_target() {
    local target_serial="$1"
    local app_id_debug="com.whatstats.debug"
    local app_id_release="com.whatstats"

    log_info "Avvio automatico app su $target_serial"

    if adb -s "$target_serial" shell monkey -p "$app_id_debug" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; then
        log_success "App avviata: $app_id_debug"
        return 0
    fi

    if adb -s "$target_serial" shell monkey -p "$app_id_release" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; then
        log_success "App avviata: $app_id_release"
        return 0
    fi

    log_error "Installazione OK ma avvio automatico fallito."
    log_error "Prova manuale: adb -s $target_serial shell monkey -p $app_id_debug -c android.intent.category.LAUNCHER 1"
    return 1
}

set_firebase_analytics_debug() {
    local target_serial="$1"
    local build_variant="$2"
    local mode="$3"

    local app_id="com.whatstats.debug"
    if [ "$build_variant" = "release" ]; then
        app_id="com.whatstats"
    fi

    if [ "$mode" = "on" ]; then
        adb -s "$target_serial" shell setprop debug.firebase.analytics.app "$app_id"
        log_success "Firebase DebugView ON per $app_id su $target_serial"
    elif [ "$mode" = "off" ]; then
        adb -s "$target_serial" shell setprop debug.firebase.analytics.app .none.
        log_success "Firebase DebugView OFF su $target_serial"
    fi
}

deploy_once() {
    local build_variant="$1"
    local firebase_debug_mode="$2"
    local target_serial=""

    target_serial=$(get_physical_device)
    if [ -n "$target_serial" ]; then
        log_info "Device fisico rilevato: $target_serial"
    else
        target_serial=$(get_running_emulator)
        if [ -n "$target_serial" ]; then
            log_info "Emulatore gia attivo: $target_serial"
        else
            log_info "Nessun target attivo. Avvio emulatore..."
            target_serial=$(start_emulator)
        fi
    fi

    if [ "$build_variant" = "release" ]; then
        install_release_on_target "$target_serial"
    else
        install_debug_on_target "$target_serial"
    fi

    launch_app_on_target "$target_serial"
    set_firebase_analytics_debug "$target_serial" "$build_variant" "$firebase_debug_mode"
}

watch_loop() {
    local build_variant="$1"
    local firebase_debug_mode="$2"

    # Porta il terminale in raw mode per leggere un singolo tasto senza Enter
    # e ripristina lo stato originale all'uscita
    local old_stty
    old_stty=$(stty -g)
    trap 'stty "$old_stty"; echo' EXIT INT TERM

    stty raw -echo

    echo -e "\r[INFO] Premi  r  per reinstallare e riavviare l'app.  Premi  q  per uscire.\r" >&2

    while true; do
        # Legge un singolo byte senza timeout
        local key
        key=$(dd bs=1 count=1 2>/dev/null)

        case "$key" in
            r|R)
                # Ripristina echo per l'output di gradlew/adb
                stty "$old_stty"
                echo -e "\r" >&2
                log_info "Reinstall richiesto dall'utente..."
                deploy_once "$build_variant" "$firebase_debug_mode"
                # Torna in raw mode e ricorda all'utente i comandi
                stty raw -echo
                echo -e "\r[INFO] Premi  r  per reinstallare e riavviare l'app.  Premi  q  per uscire.\r" >&2
                ;;
            q|Q)
                stty "$old_stty"
                echo >&2
                log_info "Uscita richiesta dall'utente."
                exit 0
                ;;
        esac
    done
}

interactive_menu() {
    echo "" >&2
    echo "===== WhatStat Bootstrap Menu =====" >&2

    local choice=""
    local default_build_choice="1"
    local default_watch_choice="2"
    local default_firebase_choice="1"

    if [ "$build_variant" = "release" ]; then
        default_build_choice="2"
    fi
    if [ "$watch_mode" = "true" ]; then
        default_watch_choice="1"
    fi
    if [ "$firebase_debug_mode" = "on" ]; then
        default_firebase_choice="2"
    elif [ "$firebase_debug_mode" = "off" ]; then
        default_firebase_choice="3"
    fi

    while true; do
        echo "" >&2
        echo "Build variant:" >&2
        echo "  1) Debug" >&2
        echo "  2) Release" >&2
        read -r -p "Scelta [1-2, default $default_build_choice]: " choice
        case "${choice:-$default_build_choice}" in
            1)
                build_variant="debug"
                break
                ;;
            2)
                build_variant="release"
                break
                ;;
            *)
                log_error "Scelta non valida."
                ;;
        esac
    done

    while true; do
        echo "" >&2
        echo "Watch mode (premi r per reinstallare, q per uscire):" >&2
        echo "  1) Si" >&2
        echo "  2) No" >&2
        read -r -p "Scelta [1-2, default $default_watch_choice]: " choice
        case "${choice:-$default_watch_choice}" in
            1)
                watch_mode="true"
                break
                ;;
            2)
                watch_mode="false"
                break
                ;;
            *)
                log_error "Scelta non valida."
                ;;
        esac
    done

    while true; do
        echo "" >&2
        echo "Firebase Analytics DebugView:" >&2
        echo "  1) Nessuna modifica" >&2
        echo "  2) ON" >&2
        echo "  3) OFF" >&2
        read -r -p "Scelta [1-3, default $default_firebase_choice]: " choice
        case "${choice:-$default_firebase_choice}" in
            1)
                firebase_debug_mode="none"
                break
                ;;
            2)
                firebase_debug_mode="on"
                break
                ;;
            3)
                firebase_debug_mode="off"
                break
                ;;
            *)
                log_error "Scelta non valida."
                ;;
        esac
    done

    echo "" >&2
    echo "Configurazione selezionata:" >&2
    echo "  build_variant=$build_variant" >&2
    echo "  watch_mode=$watch_mode" >&2
    echo "  firebase_debug_mode=$firebase_debug_mode" >&2
}

main() {
    local build_variant="debug"
    local watch_mode="false"
    local firebase_debug_mode="none"
    local use_menu="true"

    while [ $# -gt 0 ]; do
        case "$1" in
            --menu)
                use_menu="true"
                ;;
            --no-menu)
                use_menu="false"
                ;;
            --debug)
                build_variant="debug"
                ;;
            --release)
                build_variant="release"
                ;;
            --watch)
                watch_mode="true"
                ;;
            --firebase-debug-on)
                firebase_debug_mode="on"
                ;;
            --firebase-debug-off)
                firebase_debug_mode="off"
                ;;
            *)
                log_error "Argomento non riconosciuto: $1"
                log_error "Uso: ./bootstrap_project.sh [--menu|--no-menu] [--debug|--release] [--watch] [--firebase-debug-on|--firebase-debug-off]"
                exit 1
                ;;
        esac
        shift
    done

    if [ "$use_menu" = "true" ]; then
        interactive_menu
    fi

    ensure_android_sdk_env
    require_command adb
    require_command emulator

    if [ "$watch_mode" = "true" ]; then
        # fswatch non più necessario: il loop usa input da tastiera
        deploy_once "$build_variant" "$firebase_debug_mode"
        watch_loop "$build_variant" "$firebase_debug_mode"
    else
        deploy_once "$build_variant" "$firebase_debug_mode"
    fi
}

main "$@"