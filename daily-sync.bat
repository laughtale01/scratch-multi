@echo off
REM Daily sync script for syncing improvements from basic version
REM Usage: daily-sync.bat [days]
REM Example: daily-sync.bat 1  (sync changes from last 1 day)

setlocal enabledelayedexpansion

set DAYS=%1
if "%DAYS%"=="" set DAYS=1

echo.
echo ========================================
echo   Daily Sync: Basic -^> Multi Version
echo ========================================
echo.

REM Check if we're in the right directory
if not exist ".git" (
    echo Error: Not in a git repository
    exit /b 1
)

echo [1/5] Fetching latest changes from basic-version...
git fetch basic-version
if errorlevel 1 (
    echo Error: Failed to fetch from basic-version
    exit /b 1
)

echo.
echo [2/5] Checking for uncommitted changes...
git diff-index --quiet HEAD
if errorlevel 1 (
    echo.
    echo WARNING: You have uncommitted changes.
    echo Please commit or stash them first.
    echo.
    git status --short
    echo.
    set /p choice="Continue anyway? (y/n): "
    if /i not "!choice!"=="y" exit /b 0
)

echo.
echo [3/5] Recent commits in basic-version (last %DAYS% day(s)):
echo ----------------------------------------
git log basic-version/main --oneline --since="%DAYS% days ago" --date=short --pretty=format:"%%h - %%s (%%ad)"

REM Count commits
for /f %%i in ('git rev-list --count basic-version/main --since="%DAYS% days ago"') do set COMMIT_COUNT=%%i

echo.
echo.
echo Found %COMMIT_COUNT% commit(s) in the last %DAYS% day(s)
echo.

if %COMMIT_COUNT%==0 (
    echo No new commits to sync. You're up to date!
    exit /b 0
)

echo [4/5] Detailed changes:
echo ----------------------------------------
git log basic-version/main --since="%DAYS% days ago" --stat --pretty=format:"%%nCommit: %%h%%nAuthor: %%an%%nDate: %%ad%%nMessage: %%s%%n"

echo.
echo ========================================
echo.
echo Select sync method:
echo   1) Cherry-pick all commits from last %DAYS% day(s)
echo   2) Cherry-pick specific commit
echo   3) Show detailed diff
echo   4) Import specific files only
echo   5) Skip (do nothing)
echo.
set /p choice="Enter choice (1-5): "

if "%choice%"=="1" (
    echo.
    echo Cherry-picking all commits...
    for /f "tokens=1" %%h in ('git log basic-version/main --since="%DAYS% days ago" --pretty=format:%%h --reverse') do (
        echo.
        echo Picking commit %%h...
        git cherry-pick %%h
        if errorlevel 1 (
            echo.
            echo ERROR: Conflict detected while cherry-picking %%h
            echo Please resolve conflicts and run: git cherry-pick --continue
            echo Or abort with: git cherry-pick --abort
            exit /b 1
        )
        echo OK: Successfully picked %%h
    )
    echo.
    echo [5/5] All commits synced successfully!
    goto :success
)

if "%choice%"=="2" (
    echo.
    set /p commit_hash="Enter commit hash: "
    echo Cherry-picking !commit_hash!...
    git cherry-pick !commit_hash!
    if errorlevel 1 (
        echo ERROR: Failed to cherry-pick. Resolve conflicts manually.
        exit /b 1
    )
    echo.
    echo [5/5] Commit synced successfully!
    goto :success
)

if "%choice%"=="3" (
    echo.
    echo Showing detailed diff...
    git diff main basic-version/main --stat
    echo.
    echo Run this command for full diff:
    echo   git diff main basic-version/main
    exit /b 0
)

if "%choice%"=="4" (
    echo.
    echo Common files to sync:
    echo   1) scratch-client/scratch-vm/src/extensions/scratch3_minecraft/index.js
    echo   2) minecraft-mod/src/main/java/com/github/minecraftedu/commands/CommandExecutor.java
    echo   3) docs/BLOCK_REFERENCE.md
    echo   4) Custom path
    echo.
    set /p file_choice="Select file (1-4): "

    if "!file_choice!"=="1" set FILE_PATH=scratch-client/scratch-vm/src/extensions/scratch3_minecraft/index.js
    if "!file_choice!"=="2" set FILE_PATH=minecraft-mod/src/main/java/com/github/minecraftedu/commands/CommandExecutor.java
    if "!file_choice!"=="3" set FILE_PATH=docs/BLOCK_REFERENCE.md
    if "!file_choice!"=="4" (
        set /p FILE_PATH="Enter file path: "
    )

    echo.
    echo Importing !FILE_PATH! from basic-version...
    git checkout basic-version/main -- "!FILE_PATH!"
    if errorlevel 1 (
        echo ERROR: Failed to import file
        exit /b 1
    )

    git add "!FILE_PATH!"
    git commit -m "sync: Import !FILE_PATH! from basic-version"
    echo.
    echo [5/5] File synced successfully!
    goto :success
)

if "%choice%"=="5" (
    echo Skipped. No changes made.
    exit /b 0
)

echo Invalid choice.
exit /b 1

:success
echo.
echo ========================================
echo   Sync Completed Successfully!
echo ========================================
echo.
echo Next steps:
echo   1. Review changes: git log --oneline -5
echo   2. Test the changes
echo   3. Push to remote: git push origin main
echo.
exit /b 0
