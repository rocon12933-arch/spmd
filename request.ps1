Invoke-WebRequest -Method 'POST' -InFile 'data.txt' http://localhost:8749/task | Select-Object -ExpandProperty Content


Invoke-WebRequest -Method 'GET' http://localhost:8749/tasks?pretty | Select-Object -ExpandProperty Content


Invoke-WebRequest -Method 'DELETE' http://localhost:8749/task/1/ | Select-Object -ExpandProperty Content


Invoke-WebRequest -Method 'POST' http://localhost:8749/task/1/reset | Select-Object -ExpandProperty Content