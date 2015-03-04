debug:
	ant debug
release:
	ant release
install_debug:debug
	adb install -r bin/PulseDroid-debug.apk
