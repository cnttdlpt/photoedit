$hostIP = "100.113.89.21"
$remoteUser = "nguyennam"
$target = "${remoteUser}@${hostIP}"
$localWeb = "C:\Users\NamNV.dpt\Documents\photoedit\web"
$localBackend = "C:\Users\NamNV.dpt\Documents\photoedit\backend"

Write-Host "-> [1/4] Ensuring remote directories exist..." -ForegroundColor Cyan
ssh -o StrictHostKeyChecking=no $target "mkdir -p /home/nguyennam/photoedit/web/vendor /home/nguyennam/photoedit/backend"

Write-Host "-> [2/4] Uploading all web frontend & backend files..." -ForegroundColor Cyan
scp -o StrictHostKeyChecking=no "${localWeb}\index.html" "${target}:/home/nguyennam/photoedit/web/index.html"
scp -o StrictHostKeyChecking=no "${localWeb}\style.css" "${target}:/home/nguyennam/photoedit/web/style.css"
scp -o StrictHostKeyChecking=no "${localWeb}\app.mjs" "${target}:/home/nguyennam/photoedit/web/app.mjs"
scp -o StrictHostKeyChecking=no "${localWeb}\engine.mjs" "${target}:/home/nguyennam/photoedit/web/engine.mjs"
scp -o StrictHostKeyChecking=no "${localWeb}\dashboard.html" "${target}:/home/nguyennam/photoedit/web/dashboard.html"
scp -o StrictHostKeyChecking=no "${localWeb}\recipes.json" "${target}:/home/nguyennam/photoedit/web/recipes.json"
scp -o StrictHostKeyChecking=no "${localWeb}\manifest.json" "${target}:/home/nguyennam/photoedit/web/manifest.json"
scp -o StrictHostKeyChecking=no "${localWeb}\worker.mjs" "${target}:/home/nguyennam/photoedit/web/worker.mjs"
scp -o StrictHostKeyChecking=no "${localWeb}\server.mjs" "${target}:/home/nguyennam/photoedit/web/server.mjs"
scp -o StrictHostKeyChecking=no "${localWeb}\api.php" "${target}:/home/nguyennam/photoedit/web/api.php"
scp -o StrictHostKeyChecking=no -r "${localWeb}\vendor" "${target}:/home/nguyennam/photoedit/web/"
scp -o StrictHostKeyChecking=no "${localBackend}\server.mjs" "${target}:/home/nguyennam/photoedit/backend/server.mjs"

Write-Host "-> [3/4] Copying to /var/www/iro-studio, fixing ownership & permissions..." -ForegroundColor Cyan
$remoteCommands = @'
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/index.html /var/www/iro-studio/frontend/index.html
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/style.css /var/www/iro-studio/frontend/style.css
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/app.mjs /var/www/iro-studio/frontend/app.mjs
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/engine.mjs /var/www/iro-studio/frontend/engine.mjs
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/dashboard.html /var/www/iro-studio/frontend/dashboard.html
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/recipes.json /var/www/iro-studio/frontend/recipes.json
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/manifest.json /var/www/iro-studio/frontend/manifest.json
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/worker.mjs /var/www/iro-studio/frontend/worker.mjs
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/server.mjs /var/www/iro-studio/frontend/server.mjs
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/api.php /var/www/iro-studio/frontend/api.php
echo 'nam123' | sudo -S cp -r /home/nguyennam/photoedit/web/vendor /var/www/iro-studio/frontend/
echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/backend/server.mjs /var/www/iro-studio/backend/server.mjs

echo 'nam123' | sudo -S chown -R www-data:www-data /var/www/iro-studio/frontend /var/www/iro-studio/backend
echo 'nam123' | sudo -S chmod -R 755 /var/www/iro-studio/frontend /var/www/iro-studio/backend
echo 'nam123' | sudo -S systemctl restart iro-frontend iro-backend
'@

ssh -o StrictHostKeyChecking=no $target $remoteCommands

Write-Host "-> [4/4] Verifying services health..." -ForegroundColor Cyan
ssh -o StrictHostKeyChecking=no $target "systemctl is-active iro-frontend iro-backend"

Write-Host "Deployment completed successfully!" -ForegroundColor Green
