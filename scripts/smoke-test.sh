#!/usr/bin/env bash
# Проверка уже запущенного приложения (docker compose up -d): регистрация -> вход -> задачи -> права ADMIN.
#
#   ./scripts/smoke-test.sh                      первый прогон: создаёт пользователя и 3 задачи, проверяет права
#   ./scripts/smoke-test.sh --expect-persisted   после "docker compose down" + "up -d": пользователь и задачи на месте
#
# Переменные: BASE_URL (по умолчанию http://localhost:8080), ADMIN_EMAIL / ADMIN_PASSWORD (как в .env).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin12345}"
SMOKE_EMAIL="smoke-user@example.com"
SMOKE_PASSWORD="password123"
EXPECT_PERSISTED=false
[ "${1:-}" = "--expect-persisted" ] && EXPECT_PERSISTED=true

fail() { echo "FAIL: $*" >&2; exit 1; }
step() { echo "==> $*"; }

# достаёт строковое поле из плоского JSON: json_str accessToken
json_str() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"; }

# то же для числового поля: json_num id
json_num() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" | head -1; }

# expect_status 204 -X DELETE URL ...: сравнивает HTTP-код ответа
expect_status() {
  local expected="$1"; shift
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$code" = "$expected" ] || fail "ожидали HTTP $expected, получили $code: curl $*"
}

json_post() { # url, body, [curl args...]
  local url="$1" body="$2"; shift 2
  curl -sS -X POST "$url" -H 'Content-Type: application/json' -d "$body" "$@"
}

step "Ждём запуска приложения ($BASE_URL)"
for attempt in $(seq 1 60); do
  curl -fsS "$BASE_URL/login" > /dev/null 2>&1 && break
  [ "$attempt" -eq 60 ] && fail "приложение не ответило за 3 минуты (docker compose logs app)"
  sleep 3
done

step "Без токена защищённый эндпоинт отвечает 401"
expect_status 401 "$BASE_URL/tasks"

REGISTER_BODY="{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}"
if [ "$EXPECT_PERSISTED" = true ]; then
  step "Пользователь не должен регистрироваться заново (данные сохранились в volume)"
  expect_status 409 -X POST "$BASE_URL/auth/register" -H 'Content-Type: application/json' -d "$REGISTER_BODY"
  step "Вход тем же пользователем"
  TOKEN=$(json_post "$BASE_URL/auth/login" "$REGISTER_BODY" | json_str accessToken)
  [ -n "$TOKEN" ] || fail "вход не удался: пользователь не сохранился"
  step "Задачи сохранились"
  COUNT=$(curl -fsS "$BASE_URL/tasks/my" -H "Authorization: Bearer $TOKEN" | grep -o '"title"' | wc -l)
  # в первом прогоне создано 3 задачи и одну удалил ADMIN: после перезапуска должно остаться не меньше двух
  [ "$COUNT" -ge 2 ] || fail "ожидали не меньше 2 задач после перезапуска, найдено: $COUNT"
  echo "OK: пользователь и $COUNT задач пережили перезапуск"
  exit 0
fi

step "Регистрация $SMOKE_EMAIL (если уже есть - вход)"
RESPONSE=$(json_post "$BASE_URL/auth/register" "$REGISTER_BODY")
TOKEN=$(echo "$RESPONSE" | json_str accessToken)
if [ -z "$TOKEN" ]; then
  TOKEN=$(json_post "$BASE_URL/auth/login" "$REGISTER_BODY" | json_str accessToken)
fi
[ -n "$TOKEN" ] || fail "не удалось получить токен: $RESPONSE"

step "Создаём 3 задачи"
FIRST_ID=""
for title in "Купить молоко" "Написать отчёт" "Сдать домашнее задание"; do
  CREATED=$(json_post "$BASE_URL/tasks" "{\"title\":\"$title\",\"priority\":\"HIGH\"}" -H "Authorization: Bearer $TOKEN")
  ID=$(echo "$CREATED" | json_num id)
  [ -n "$ID" ] || fail "задача не создана: $CREATED"
  [ -n "$FIRST_ID" ] || FIRST_ID="$ID"
done

step "GET /tasks/my возвращает задачи пользователя"
COUNT=$(curl -fsS "$BASE_URL/tasks/my" -H "Authorization: Bearer $TOKEN" | grep -o '"title"' | wc -l)
[ "$COUNT" -ge 3 ] || fail "ожидали не меньше 3 задач, найдено: $COUNT"

step "Вход ADMIN ($ADMIN_EMAIL) и удаление задачи как ADMIN"
ADMIN_TOKEN=$(json_post "$BASE_URL/auth/login" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | json_str accessToken)
[ -n "$ADMIN_TOKEN" ] || fail "ADMIN не вошёл (проверьте ADMIN_EMAIL / ADMIN_PASSWORD)"
expect_status 204 -X DELETE "$BASE_URL/tasks/$FIRST_ID" -H "Authorization: Bearer $ADMIN_TOKEN"

step "Чужую задачу обычный пользователь не удалит (403)"
OTHER_ID=$(curl -fsS "$BASE_URL/tasks?size=100" -H "Authorization: Bearer $ADMIN_TOKEN" | json_num id)
if [ -n "$OTHER_ID" ]; then
  REG2=$(json_post "$BASE_URL/auth/register" "{\"email\":\"smoke-other-$(date +%s)-$RANDOM@example.com\",\"password\":\"$SMOKE_PASSWORD\"}")
  OTHER_TOKEN=$(echo "$REG2" | json_str accessToken)
  expect_status 403 -X DELETE "$BASE_URL/tasks/$OTHER_ID" -H "Authorization: Bearer $OTHER_TOKEN"
fi

echo "OK: регистрация, вход, задачи и права работают"
