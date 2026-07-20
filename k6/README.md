# 1. Установить k6
winget install k6

# 2. Запустить приложение + Docker (Redis + Postgres)
# docker-compose up -d (в папке docker/)

# 3. Запустить тест
k6 run docks\k6-cache-stampede.js