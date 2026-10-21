$hostIP = "100.113.89.21"
$remoteUser = "nguyennam"
$target = "${remoteUser}@${hostIP}"

Write-Host "Creating remote folders..."
ssh -o StrictHostKeyChecking=no $target "mkdir -p /home/nguyennam/photoedit/web/vendor /home/nguyennam/photoedit/backend"

Write-Host "Uploading updated files to remote..."
scp -o StrictHostKeyChecking=no C:\Users\NamNV.dpt\Documents\photoedit\web\index.html "${target}:/home/nguyennam/photoedit/web/index.html"
scp -o StrictHostKeyChecking=no C:\Users\NamNV.dpt\Documents\photoedit\web\style.css "${target}:/home/nguyennam/photoedit/web/style.css"
scp -o StrictHostKeyChecking=no C:\Users\NamNV.dpt\Documents\photoedit\web\app.mjs "${target}:/home/nguyennam/photoedit/web/app.mjs"
scp -o StrictHostKeyChecking=no C:\Users\NamNV.dpt\Documents\photoedit\web\server.mjs "${target}:/home/nguyennam/photoedit/web/server.mjs"
scp -o StrictHostKeyChecking=no -r C:\Users\NamNV.dpt\Documents\photoedit\web\vendor "${target}:/home/nguyennam/photoedit/web/"
scp -o StrictHostKeyChecking=no C:\Users\NamNV.dpt\Documents\photoedit\backend\server.mjs "${target}:/home/nguyennam/photoedit/backend/server.mjs"

Write-Host "Installing files to production directory and restarting services..."
ssh -o StrictHostKeyChecking=no $target "echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/index.html /var/www/iro-studio/frontend/index.html && echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/style.css /var/www/iro-studio/frontend/style.css && echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/app.mjs /var/www/iro-studio/frontend/app.mjs && echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/web/server.mjs /var/www/iro-studio/frontend/server.mjs && echo 'nam123' | sudo -S cp -r /home/nguyennam/photoedit/web/vendor /var/www/iro-studio/frontend/ && echo 'nam123' | sudo -S cp /home/nguyennam/photoedit/backend/server.mjs /var/www/iro-studio/backend/server.mjs && echo 'nam123' | sudo -S systemctl restart iro-frontend iro-backend"

Write-Host "VPS deployment successfully completed!" -ForegroundColor Green
