#!/usr/bin/env bash
#
# test-wgpu.sh - comprueba que el backend de renderizado wgpu del motor OpenGMK
# arranca, sin necesidad de una GPU real.
#
# Compila gm8emulator con la feature `backend-wgpu`, lo ejecuta bajo Xvfb con
# OPENGMK_RENDERER=gl|wgpu y analiza el log. En Linux sin GPU se apoya en
# lavapipe (Vulkan software) y llvmpipe (OpenGL software), ambos de Mesa.
#
# Códigos de salida:
#   0  todos los backends pedidos arrancaron y no hubo panic.
#   1  la compilación falló o algún backend no arrancó / paniqueó.
#   2  faltan dependencias obligatorias o los argumentos no son válidos.
#
set -euo pipefail

SCRIPT_NAME=$(basename -- "$0")
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
ENGINE_DIR="$REPO_ROOT/engine/src/OpenGMK"

EXE=""
FRAMES=300
RENDERERS=()
STARTUP_GRACE=20
LOG_DIR=""
BINARY=""

LAVAPIPE_ICD=""
VULKAN_SUMMARY=""
HAS_LAVAPIPE=0
HAS_REAL_GPU=0
WANT_WGPU=0

usage() {
    cat <<EOF
Uso: $SCRIPT_NAME --exe <ruta al juego> [opciones]

Comprueba que el backend wgpu del motor arranca. Compila una sola vez con la
feature backend-wgpu y ejecuta el motor bajo Xvfb con cada backend pedido.

Opciones:
  --exe <ruta>       Juego GM8 (.exe) que se pasa al motor. Obligatorio.
  --frames <N>       Fotogramas objetivo (por defecto: 300). El motor no tiene
                     una opción para salir tras N fotogramas, así que se usa
                     para calcular un tiempo máximo: N/30 s + 20 s de arranque.
  --renderer <modo>  gl, wgpu, o ambos (por defecto: ambos, en secuencia).
                     Se puede repetir para elegir varios.
  -h, --help         Muestra esta ayuda.

Variables de entorno:
  TMPDIR                          Directorio donde se crean los logs.
  VK_DRIVER_FILES                 Si está definida, se respeta (ICD Vulkan).
  VK_ICD_FILENAMES                Igual que la anterior (loaders antiguos).
  OPENGMK_TEST_FORCE_LAVAPIPE=1   Fuerza lavapipe aunque haya GPU real.
  OPENGMK_TEST_FORCE_LLVMPIPE=1   Fuerza llvmpipe (OpenGL software).

Códigos de salida:
  0  Todos los backends pedidos arrancaron y no hubo panic.
  1  La compilación o algún backend falló.
  2  Faltan dependencias (cargo, xvfb-run, vulkaninfo) o los argumentos no
     son válidos.

Ejemplos:
  $SCRIPT_NAME --exe "/juegos/Pokemon Reloaded.exe"
  $SCRIPT_NAME --exe "/juegos/Pokemon Reloaded.exe" --renderer wgpu
  $SCRIPT_NAME --exe "/juegos/Pokemon Reloaded.exe" --renderer wgpu --frames 600
EOF
}

info() {
    printf '[INFO] %s\n' "$*"
}

warn() {
    printf '[AVISO] %s\n' "$*" >&2
}

fail() {
    printf '[ERROR] %s\n' "$*" >&2
}

add_renderer() {
    local candidate=$1
    local existing

    case "$candidate" in
        gl | wgpu) ;;
        *)
            fail "backend no válido: '$candidate' (usa gl o wgpu)"
            exit 2
            ;;
    esac

    if ((${#RENDERERS[@]} > 0)); then
        for existing in "${RENDERERS[@]}"; do
            if [[ $existing == "$candidate" ]]; then
                return 0
            fi
        done
    fi
    RENDERERS+=("$candidate")
}

parse_args() {
    while (($# > 0)); do
        case "$1" in
            --exe)
                if (($# < 2)); then
                    fail "--exe necesita una ruta"
                    exit 2
                fi
                EXE=$2
                shift 2
                ;;
            --exe=*)
                EXE=${1#*=}
                shift
                ;;
            --frames)
                if (($# < 2)); then
                    fail "--frames necesita un número"
                    exit 2
                fi
                FRAMES=$2
                shift 2
                ;;
            --frames=*)
                FRAMES=${1#*=}
                shift
                ;;
            --renderer)
                if (($# < 2)); then
                    fail "--renderer necesita un valor"
                    exit 2
                fi
                add_renderer "$2"
                shift 2
                ;;
            --renderer=*)
                add_renderer "${1#*=}"
                shift
                ;;
            -h | --help)
                usage
                exit 0
                ;;
            *)
                fail "opción desconocida: '$1'"
                usage >&2
                exit 2
                ;;
        esac
    done

    if [[ -z $EXE ]]; then
        fail "falta --exe <ruta al juego>"
        usage >&2
        exit 2
    fi
    if [[ ! -f $EXE ]]; then
        fail "el juego no existe o no es un archivo: '$EXE'"
        exit 2
    fi
    if [[ ! -r $EXE ]]; then
        fail "el juego no se puede leer: '$EXE'"
        exit 2
    fi
    EXE=$(cd -- "$(dirname -- "$EXE")" && pwd)/$(basename -- "$EXE")

    case "$FRAMES" in
        '' | *[!0-9]*)
            fail "--frames debe ser un entero positivo (recibido: '$FRAMES')"
            exit 2
            ;;
    esac
    FRAMES=$((10#$FRAMES))
    if ((FRAMES <= 0)); then
        fail "--frames debe ser mayor que cero"
        exit 2
    fi

    if ((${#RENDERERS[@]} == 0)); then
        RENDERERS=(gl wgpu)
    fi
    local renderer
    for renderer in "${RENDERERS[@]}"; do
        if [[ $renderer == wgpu ]]; then
            WANT_WGPU=1
        fi
    done
}

find_lavapipe() {
    local dir candidate
    for dir in /usr/share/vulkan/icd.d /etc/vulkan/icd.d; do
        for candidate in "$dir"/lvp_icd*.json; do
            if [[ -f $candidate ]]; then
                LAVAPIPE_ICD=$candidate
                return 0
            fi
        done
    done
    return 1
}

detect_vulkan_devices() {
    local line name

    if [[ -z $VULKAN_SUMMARY ]]; then
        return 0
    fi

    # vulkaninfo --summary imprime "deviceName = ...". El dispositivo de lavapipe
    # se llama "llvmpipe (LLVM ...)", no "lavapipe": es el nombre del ICD.
    if grep -qiE 'lavapipe|llvmpipe' <<<"$VULKAN_SUMMARY"; then
        HAS_LAVAPIPE=1
    fi

    # vulkaninfo --summary imprime líneas "deviceName = ...".
    while IFS= read -r line; do
        [[ $line == *deviceName* ]] || continue
        name=${line#*=}
        name=${name#"${name%%[![:space:]]*}"}
        if [[ -n $name ]] && ! grep -qiE 'lavapipe|llvmpipe|swiftshader|software' <<<"$name"; then
            HAS_REAL_GPU=1
        fi
    done <<<"$VULKAN_SUMMARY"
}

check_dependencies() {
    local missing=0
    local tool

    # cargo y xvfb-run son imprescindibles para cualquier backend.
    for tool in cargo xvfb-run; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            fail "falta '$tool' en PATH"
            missing=1
        fi
    done

    if ! command -v timeout >/dev/null 2>&1; then
        warn "falta 'timeout' (coreutils); se ejecutará sin límite de tiempo"
    fi

    if command -v vulkaninfo >/dev/null 2>&1; then
        if ! VULKAN_SUMMARY=$(vulkaninfo --summary 2>&1); then
            warn "'vulkaninfo --summary' falló; no se puede verificar el driver Vulkan"
            VULKAN_SUMMARY=""
        fi
    else
        fail "falta 'vulkaninfo' (paquete vulkan-tools)"
        missing=1
    fi

    if ! command -v xauth >/dev/null 2>&1; then
        warn "falta 'xauth'; algunas versiones de xvfb-run lo necesitan"
    fi

    if ! find_lavapipe; then
        warn "no se encontró el ICD de lavapipe (/usr/share/vulkan/icd.d/lvp_icd*.json)"
        warn "instala mesa-vulkan-drivers si no hay una GPU Vulkan real (solo afecta a wgpu)"
    fi

    detect_vulkan_devices

    if ((!HAS_LAVAPIPE && !HAS_REAL_GPU)); then
        warn "vulkaninfo no detecta lavapipe ni una GPU Vulkan real (solo afecta a wgpu)"
    elif ((HAS_LAVAPIPE)); then
        info "Vulkan: lavapipe detectado"
    fi
    if ((HAS_REAL_GPU)); then
        info "Vulkan: GPU real detectada"
    fi

    if ((missing)); then
        fail "faltan dependencias; ver scripts/README-wgpu-test.md"
        return 1
    fi
    return 0
}

apply_software_env() {
    if ((WANT_WGPU)) && [[ -z ${VK_DRIVER_FILES:-} && -z ${VK_ICD_FILENAMES:-} ]]; then
        if [[ ${OPENGMK_TEST_FORCE_LAVAPIPE:-0} == 1 ]]; then
            if [[ -n $LAVAPIPE_ICD ]]; then
                export VK_DRIVER_FILES=$LAVAPIPE_ICD
                export VK_ICD_FILENAMES=$LAVAPIPE_ICD
                info "Vulkan: lavapipe forzado ($LAVAPIPE_ICD)"
            else
                warn "OPENGMK_TEST_FORCE_LAVAPIPE=1 pero no se encontró el ICD de lavapipe"
            fi
        elif ((!HAS_REAL_GPU)) && [[ -n $LAVAPIPE_ICD ]]; then
            export VK_DRIVER_FILES=$LAVAPIPE_ICD
            export VK_ICD_FILENAMES=$LAVAPIPE_ICD
            info "Vulkan: sin GPU real, usando lavapipe ($LAVAPIPE_ICD)"
        elif [[ $HAS_LAVAPIPE == 1 ]]; then
            info "Vulkan: lavapipe disponible; no se fuerza (también hay GPU real)"
        fi
    fi

    if [[ ${OPENGMK_TEST_FORCE_LLVMPIPE:-0} == 1 ]]; then
        export LIBGL_ALWAYS_SOFTWARE=1
        export GALLIUM_DRIVER=llvmpipe
        info "OpenGL: forzando llvmpipe (LIBGL_ALWAYS_SOFTWARE=1, GALLIUM_DRIVER=llvmpipe)"
    fi
}

resolve_binary() {
    local target_dir

    if [[ -n ${CARGO_TARGET_DIR:-} ]]; then
        case "$CARGO_TARGET_DIR" in
            /*) target_dir=$CARGO_TARGET_DIR ;;
            *) target_dir="$ENGINE_DIR/$CARGO_TARGET_DIR" ;;
        esac
    else
        target_dir="$ENGINE_DIR/target"
    fi
    BINARY="$target_dir/release/gm8emulator"
}

build_engine() {
    info "compilando gm8emulator con la feature backend-wgpu (una sola vez)..."
    # Se compila desde el directorio del workspace para que rustup encuentre
    # rust-toolchain.toml (1.99.0) y cargo use su target/.
    if ! (
        cd "$ENGINE_DIR"
        cargo build --release -p gm8emulator --features backend-wgpu
    ) 2>&1 | tee "$LOG_DIR/build.log"; then
        fail "la compilación falló; revisa $LOG_DIR/build.log"
        return 1
    fi

    if [[ ! -x $BINARY ]]; then
        fail "no se encontró el binario compilado: $BINARY"
        return 1
    fi
    return 0
}

run_seconds() {
    # El motor no se autodestruye tras N fotogramas: se le da un margen amplio
    # a 30 pasos/s (velocidad típica de las rooms de GM8) más el arranque.
    local base=$(((FRAMES + 29) / 30))
    if ((base < 5)); then
        base=5
    fi
    printf '%s' "$((base + STARTUP_GRACE))"
}

run_backend() {
    local backend=$1
    local log="$LOG_DIR/$backend.log"
    local stdout_log="$LOG_DIR/$backend.stdout.log"
    local limit
    local status=0

    limit=$(run_seconds)
    info "ejecutando backend '$backend' bajo xvfb-run (máximo ${limit}s)..."

    # stderr va al log del backend (ahí escribe el motor los avisos de wgpu y
    # los panics); stdout se guarda aparte para no perderlo.
    # OPENGMK_RENDER_LOG=1 añade las líneas "[render] N dibujos/s" al stderr.
    # `timeout` devuelve 124 cuando el motor sigue vivo al agotarse el tiempo,
    # que es el caso normal: el juego no se cierra solo.
    local -a cmd=(xvfb-run -a -s "-screen 0 1280x720x24" "$BINARY" "$EXE")
    if command -v timeout >/dev/null 2>&1; then
        cmd=(timeout --signal=TERM --kill-after=10 "$limit" "${cmd[@]}")
    fi

    status=0
    OPENGMK_RENDERER=$backend \
        OPENGMK_RENDER_LOG=1 \
        "${cmd[@]}" >"$stdout_log" 2>"$log" || status=$?

    analyze_backend "$backend" "$log" "$stdout_log" "$status"
}

count_lines() {
    local pattern=$1
    local file=$2
    local value
    value=$(grep -c -i -E "$pattern" "$file" 2>/dev/null || true)
    [[ $value =~ ^[0-9]+$ ]] || value=0
    printf '%s' "$value"
}

analyze_backend() {
    local backend=$1
    local log=$2
    local stdout_log=$3
    local status=$4
    local backend_ok=0
    local panicked=0
    local wgpu_line=""
    local surface_line=""
    local chosen=""
    local gl_version=""
    local panic_hits=""
    local error_hits=""
    local fatal_hits=""
    local error_count=0
    local render_count=0
    local last_render=""
    local status_text=""

    wgpu_line=$(grep -m1 '^wgpu: backend ' "$log" 2>/dev/null || true)
    surface_line=$(grep -m1 '^wgpu: surface ' "$log" 2>/dev/null || true)
    gl_version=$(sed -n 's/.*GL_VERSION: "\([^"]*\)".*/\1/p' "$log" 2>/dev/null | head -n 1 || true)

    panic_hits=$(grep -n -i -E 'panicked at|thread .* panicked|not yet implemented' "$log" "$stdout_log" 2>/dev/null | head -n 5 || true)
    error_hits=$(grep -n -i -E '\berror\b' "$log" "$stdout_log" 2>/dev/null | head -n 5 || true)
    fatal_hits=$(grep -n -E 'Runtime error:|Failed to launch game:' "$log" "$stdout_log" 2>/dev/null | head -n 5 || true)
    error_count=$(( $(count_lines '\berror\b' "$log") + $(count_lines '\berror\b' "$stdout_log") ))
    render_count=$(count_lines '^\[render\]' "$log")
    last_render=$(grep '^\[render\]' "$log" 2>/dev/null | tail -n 1 || true)

    if [[ -n $panic_hits ]]; then
        panicked=1
    fi

    case "$status" in
        0) status_text="0 (el motor terminó por sí mismo)" ;;
        124) status_text="124 (tiempo agotado: el motor seguía vivo, es lo normal)" ;;
        143) status_text="143 (SIGTERM: tiempo agotado)" ;;
        137) status_text="137 (tiempo agotado y SIGKILL)" ;;
        125) status_text="125 (fallo de timeout)" ;;
        126) status_text="126 (no se pudo ejecutar el binario)" ;;
        127) status_text="127 (orden no encontrada)" ;;
        *) status_text="$status" ;;
    esac

    echo
    echo "--- Resumen: $backend ---"
    echo "  Log stderr:      $log"
    echo "  Log stdout:      $stdout_log"
    echo "  Estado proceso:  $status_text"

    if [[ $backend == wgpu ]]; then
        if [[ -n $wgpu_line ]]; then
            backend_ok=1
            echo "  ¿wgpu arrancó?:  SÍ"
            echo "    $wgpu_line"
            chosen=$(sed -E 's/^wgpu: backend ([^ |]+).*/\1/' <<<"$wgpu_line")
            echo "    API elegida:   ${chosen:-desconocida}"
            if [[ $chosen == Gl* || $chosen == Gles* ]]; then
                echo "    Aviso:         wgpu cayó a OpenGL/GLES (Vulkan no disponible)"
            fi
        else
            echo "  ¿wgpu arrancó?:  NO (no apareció 'wgpu: backend ...')"
        fi
        if [[ -n $surface_line ]]; then
            echo "    $surface_line"
        fi
    else
        if [[ -n $gl_version ]] && grep -q 'creating graphics context' "$log" 2>/dev/null; then
            backend_ok=1
            echo "  ¿OpenGL arrancó?: SÍ"
            echo "    GL_VERSION:    $gl_version"
        else
            echo "  ¿OpenGL arrancó?: NO (no apareció 'creating graphics context' con GL_VERSION)"
        fi
    fi

    if ((panicked)); then
        echo "  Panics:          SÍ"
        printf '%s\n' "$panic_hits" | sed 's/^/    /'
    else
        echo "  Panics:          no"
    fi

    if ((${#fatal_hits} > 0)); then
        echo "  Errores fatales: SÍ"
        printf '%s\n' "$fatal_hits" | sed 's/^/    /'
    else
        echo "  Errores fatales: no"
    fi

    echo "  Líneas 'error':  $error_count"
    if ((error_count > 0)); then
        printf '%s\n' "$error_hits" | sed 's/^/    /'
    fi

    echo "  Líneas [render]: $render_count"
    if [[ -n $last_render ]]; then
        echo "    última:        $last_render"
    fi

    if ((backend_ok)) && ((!panicked)) && ((${#fatal_hits} == 0)); then
        return 0
    fi
    return 1
}

main() {
    parse_args "$@"
    if ! check_dependencies; then
        exit 2
    fi

    local tmp_root=${TMPDIR:-/tmp}
    tmp_root=${tmp_root%/}
    LOG_DIR="$tmp_root/opengmk-wgpu-test-$(date +%Y%m%d-%H%M%S)-$$"
    if ! mkdir -p "$LOG_DIR"; then
        fail "no se pudo crear el directorio de logs: $LOG_DIR"
        exit 1
    fi

    resolve_binary
    apply_software_env

    info "repo:    $REPO_ROOT"
    info "juego:   $EXE"
    info "logs:    $LOG_DIR"

    echo
    if ! build_engine; then
        echo
        echo "Logs en: $LOG_DIR"
        exit 1
    fi

    local failures=0
    local backend
    for backend in "${RENDERERS[@]}"; do
        if ! run_backend "$backend"; then
            failures=$((failures + 1))
        fi
    done

    echo
    echo "=== Resultado global ==="
    if ((failures == 0)); then
        echo "  OK: todos los backends pedidos arrancaron sin panic."
    else
        echo "  FALLO: $failures de ${#RENDERERS[@]} backend(s) no arrancaron o paniquearon."
    fi
    echo "  Logs para inspección:"
    printf '    %s\n' "$LOG_DIR"/*.log
    echo "  Directorio: $LOG_DIR"

    if ((failures == 0)); then
        exit 0
    fi
    exit 1
}

# Permite cargar las funciones (`source test-wgpu.sh`) sin ejecutar el test.
if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    main "$@"
fi
