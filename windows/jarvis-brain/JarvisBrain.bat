@echo off
rem JARVIS brain — Windows viewer. Serves this folder and opens the browser.
rem Requires Python 3 on PATH. Close the console window to stop it.
cd /d "%~dp0"
start "" http://127.0.0.1:8773/
python -m http.server 8773 --bind 127.0.0.1
