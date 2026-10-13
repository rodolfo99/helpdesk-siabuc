@echo off
setlocal DisableDelayedExpansion
rem Run from CMD or PowerShell; preserve arguments and paths with spaces.
py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3,10) else 1)" >nul 2>&1
if not errorlevel 1 goto use_py
python -c "import sys; sys.exit(0 if sys.version_info >= (3,10) else 1)" >nul 2>&1
if not errorlevel 1 goto use_python
echo Error: instale Python 3.10 o posterior y agreguelo a PATH. 1>&2
exit /b 1
:use_py
py -3 -X utf8 "%~dp0scripts\manage.py" config %*
exit /b %errorlevel%
:use_python
python -X utf8 "%~dp0scripts\manage.py" config %*
exit /b %errorlevel%
