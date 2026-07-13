@echo off
cd /d "C:\Users\yang_\Desktop\桌面备份\工作\1 工作 pending\43 apk逆向\SynxVipUnlocker"
echo Pushing to GitHub...
git add -A
git commit -m "Auto push from WorkBuddy" 2>nul
git push origin main
echo.
echo Done! Check: https://github.com/jiasheny/SynxVipUnlocker/actions
pause
