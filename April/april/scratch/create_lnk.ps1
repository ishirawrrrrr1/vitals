$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("c:\xampp\htdocs\projects systems\April\april\SHORTCUT_TO_THE_APK.lnk")
$Shortcut.TargetPath = "c:\xampp\htdocs\projects systems\April\april\SHORTCUT_TO_THE_APK_HERE\VitalsHub_v6.8_STABLE.apk"
$Shortcut.Save()
