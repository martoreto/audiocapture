pkg_name=com.github.martoreto.audiocapture
pkg_label=AudioCapture

log_print() {
    log -p i -t "audiocapture-systemize" "$*"
}

[ -d /system/priv-app ] || log_print "No access to /system/priv-app!"
[ -d /data/app ] || log_print "No access to /data/app!"

mount -o rw,remount /system

for i in /data/app/${pkg_name}-*/base.apk; do
    if [ "$i" != "/data/app/${pkg_name}-*/base.apk" ]; then
        [ -e "/system/priv-app/${pkg_label}" ] && { log_print "Ignoring ${pkg_name}: already a system app."; continue; }
        mkdir -p "/system/priv-app/${pkg_label}" 2>/dev/null
        if cp "$i" "/system/priv-app/${pkg_label}/${pkg_name}.apk"; then
            log_print "Systemized ${pkg_name}: change will take effect after reboot."
        else
            log_print "Copy Failed: cp $i /system/priv-app/${pkg_label}/${pkg_name}.apk"
            [ -e /system/priv-app/${pkg_label} ] && rm -rf /system/priv-app/${pkg_label}
            exit 1
        fi
        chown 0:0 "/system/priv-app/${pkg_label}"
        chmod 0755 "/system/priv-app/${pkg_label}"
        chown 0:0 "/system/priv-app/${pkg_label}/${pkg_name}.apk"
        chmod 0644 "/system/priv-app/${pkg_label}/${pkg_name}.apk"
    elif [ -n "$STOREDLIST" ]; then
        log_print "${pkg_name} not found"
        exit 2
    fi
done

mount -o ro,remount /system
sync

reboot