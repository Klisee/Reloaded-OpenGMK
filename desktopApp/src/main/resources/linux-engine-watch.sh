#!/bin/sh
pid="$1"
icon="$2"
log="$3"
app="$4"
title="$5"

set_title_icon() {
  w="$1"
  if [ -n "$title" ]; then
    xprop -id "$w" -f _NET_WM_NAME 8u -set _NET_WM_NAME "$title" >/dev/null 2>&1 || true
    xprop -id "$w" -f WM_NAME 8s -set WM_NAME "$title" >/dev/null 2>&1 || true
  fi
  if [ -n "$icon" ] && [ -s "$icon" ]; then
    xprop -id "$w" -f _NET_WM_ICON 32c -set _NET_WM_ICON "$(cat "$icon")" >/dev/null 2>&1 || true
  fi
}

while kill -0 "$pid" 2>/dev/null; do
  if command -v xprop >/dev/null 2>&1; then
    if command -v xdotool >/dev/null 2>&1; then
      for w in $(xdotool search --pid "$pid" 2>/dev/null); do
        set_title_icon "$w"
      done
    else
      for w in $(xprop -root _NET_CLIENT_LIST 2>/dev/null | sed 's/.*# //' | tr ',' ' '); do
        wpid=$(xprop -id "$w" _NET_WM_PID 2>/dev/null | awk '{print $3}')
        [ "$wpid" = "$pid" ] && set_title_icon "$w"
      done
    fi
  fi
  sleep 1
done

printf 'Motor terminado\n' >> "$log"
sleep 1
if [ -n "$app" ] && [ -x "$app" ]; then
  if ! pgrep -f -- "$app" | grep -v -x "$$" >/dev/null 2>&1; then
    nohup "$app" >/dev/null 2>&1 &
  fi
fi
