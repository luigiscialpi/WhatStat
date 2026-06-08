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

deploy_once() {
    local build_variant="$1"
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
}

main() {
    local build_variant="debug"
    local watch_mode="false"

    while [ $# -gt 0 ]; do
        case "$1" in
            --debug)
                build_variant="debug"
                ;;
            --release)
                build_variant="release"
                ;;
            --watch)
                watch_mode="true"
                ;;
            *)
                log_error "Argomento non riconosciuto: $1"
                log_error "Uso: ./bootstrap_project.sh [--debug|--release] [--watch]"
                exit 1
                ;;
        esac
        shift
    done

    ensure_android_sdk_env
    require_command adb
    require_command emulator

    if [ "$watch_mode" = "true" ]; then
        require_command fswatch
        log_info "Watch mode attivo. Stop con Ctrl+C"
        deploy_once "$build_variant"

        while fswatch -1 app/src app/build.gradle.kts; do
            log_info "Modifica rilevata. Reinstallo..."
            deploy_once "$build_variant"
        done
    else
        deploy_once "$build_variant"
    fi
}

main "$@"
