@echo off
cd /d D:\mc\Leader-Lite-main\Leader-Lite-main
call gradlew.bat compileJava --offline > build_log.txt 2>&1
echo EXIT=%ERRORLEVEL%
