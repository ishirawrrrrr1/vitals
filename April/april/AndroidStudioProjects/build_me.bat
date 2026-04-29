@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=C:\Users\raiji\AppData\Local\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
.\gradlew.bat assembleDebug
