@echo off
rem ND2.0 - doppio clic per pubblicare una nuova versione (avvia release.ps1)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1"
