#!/bin/bash
set -e

cd /var/www/jcclubtour
git pull origin master

./gradlew build -x test --no-daemon
JAR=/var/www/jcclubtour/build/libs/jcclub-0.0.1-SNAPSHOT.jar

pm2 stop jcclub 2>/dev/null || true
pm2 delete jcclub 2>/dev/null || true
# pm2 delete 후에도 살아남은 고아 java 프로세스가 8081을 점유하면 기동 실패하므로 강제 종료
PORT_PID=$(ss -tlnp "sport = :8081" | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2 || true)
if [ -n "$PORT_PID" ]; then
    echo "Killing orphan process on port 8081: $PORT_PID"
    kill $PORT_PID
    sleep 3
    kill -9 $PORT_PID 2>/dev/null || true
fi
pm2 start "java -Xmx512m -jar $JAR" --name "jcclub" --cwd /var/www/jcclubtour

echo "Deploy complete"
