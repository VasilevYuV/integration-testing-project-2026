# Интеграционное тестирование

КТ-2: два REST-сервиса для пользователей и заказов. Сервис заказов получает данные
пользователя по HTTP. У каждого приложения своя H2-база в памяти.

## Структура

```text
integration-testing/
├── pom.xml
├── README.md
├── user-service/
│   ├── pom.xml
│   └── src/main/
├── order-service/
│   ├── pom.xml
│   └── src/main/
└── integration-tests/
    ├── pom.xml
    └── src/test/
```

Корневой `pom.xml` объединяет три Maven-модуля. В сервисах код разделён на предметные
модули `user` и `order`, внутри которых находятся слои:

- `api` — контроллеры, входные и выходные DTO, обработка ошибок;
- `application` — создание и получение объектов, контракт обращения к пользователям;
- `domain` — модели и контракты репозиториев;
- `infrastructure` — SQL и HTTP-клиент.

Сервисы не зависят от Java-классов друг друга и не читают чужую БД.
Имя и email пользователя добавляются в заказ при каждом HTTP-запросе.

## IntelliJ IDEA

Требования: JDK 25, Maven 3.9+; Spring Boot 4.1.1.

1. Открыть корневой `pom.xml` через **File → Open → Open as Project**.
2. Выбрать **Project SDK: JDK 25**.
3. В настройках **Build Tools → Maven** выбрать встроенный **Bundled Maven**;
   для Maven Importer и Runner указать JDK 25.
4. Выполнить **Reload All Maven Projects** в окне Maven.

Если проект уже открыт, достаточно повторно загрузить Maven-проект из корневого `pom.xml`.
В Maven должны отображаться модули `user-service`, `order-service` и `integration-tests`.
Версия IDEA должна поддерживать выбранный JDK для сборки встроенными средствами IDE;
сборка через Maven использует JDK, выбранный для Maven Runner.

## Сборка и тесты

В окне **Maven** запустить `clean verify` для корневого проекта `integration-testing`.
При первой сборке нужен доступ к Maven Central. Docker и внешний сервер БД не требуются.

При установленном Maven команда из корня репозитория:

```powershell
mvn clean verify
```

`verify` собирает оба JAR, запускает приложения в отдельных JVM на свободных портах
и выполняет 12 интеграционных тестов. Цель `test` не запускает эти проверки,
поскольку им нужны собранные JAR.

- `ServiceApiIT`: создание и чтение пользователя и заказа, валидация, ошибки 404/422,
  пять измерений времени чтения заказа через оба сервиса.
- `UserServiceFailureIT`: остановка настоящего users, HTTP 500, неверный JSON,
  чужой ID, неполный ответ, тайм-аут и допустимая задержка.

Управляемые сбои и задержки задаются тестовым HTTP-сервером `UserServiceStub`.
Тесты останавливают запущенные ими процессы. Результаты находятся в
`integration-tests/target/failsafe-reports/`, измерения — в
`integration-tests/target/response-times.csv`. Папки `target` не входят в Git.

Проверено 23.10.2026 на встроенном Maven IDEA 3.9.8 и JDK 25:
**12 тестов, 0 ошибок, 0 пропусков; BUILD SUCCESS**.

## Запуск приложений

Запустить классы `UserServiceApplication` и `OrderServiceApplication` как Java-приложения.
Либо после сборки выполнить в двух терминалах из корня репозитория:

```powershell
& 'C:\Program Files\Java\jdk-25\bin\java.exe' -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
```

```powershell
& 'C:\Program Files\Java\jdk-25\bin\java.exe' -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
```

Users слушает `http://127.0.0.1:18081`, orders — `http://127.0.0.1:18082`.
Остановка — кнопка Stop в IDEA или Ctrl+C в терминале.
После перезапуска сервиса его данные теряются: H2 работает в памяти.

## HTTP API

| Сервис | Запрос | Ответ |
| --- | --- | --- |
| users | `POST /users` | 201, пользователь и Location |
| users | `GET /users/{id}` | 200, пользователь |
| orders | `POST /orders` | 201, заказ с пользователем и Location |
| orders | `GET /orders/{id}` | 200, заказ с пользователем |

Пользователь: `name` — непустая строка до 100 символов; `email` — email до 254 символов.

Заказ: `userId` — UUID; `product` — непустая строка до 200 символов;
`quantity` — целое число от 1 до 1000; `unitPrice` — от 0.01 до 99999999.99,
не более двух знаков после точки. Сумма `total` вычисляется через `BigDecimal`.
Заказ содержит один товар. Имя, email и название товара очищаются от пробелов по краям.

Проверка пользователя выполняется до сохранения заказа.
Ошибки возвращаются как `application/problem+json` с полями `status`, `detail`, `code`:

| HTTP | Код | Причина |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Невалидные поля, JSON или UUID |
| 404 | `USER_NOT_FOUND` / `ORDER_NOT_FOUND` | Ресурс не найден |
| 422 | `USER_NOT_FOUND` | Пользователь для нового заказа отсутствует |
| 503 | `USER_SERVICE_UNAVAILABLE` | Ошибка соединения, тайм-аут или некорректный ответ users |

## Настройки

| Переменная окружения | По умолчанию |
| --- | --- |
| `USER_SERVICE_PORT` | `18081` |
| `ORDER_SERVICE_PORT` | `18082` |
| `USER_SERVICE_URL` | `http://127.0.0.1:18081` |
| `USER_SERVICE_CONNECT_TIMEOUT` | `1s` |
| `USER_SERVICE_READ_TIMEOUT` | `2s` |

При смене порта users нужно изменить `USER_SERVICE_URL` в orders.
Тайм-ауты должны быть положительными; автоматических повторов нет.
Измерения тестов относятся к локальным последовательным запросам, а не к нагрузочному тесту.

## Материалы КТ-2

Отчёт и коллекция Postman находятся рядом с репозиторием, в папке `КТ2`:
`КТ2_Отчет.pdf`, `reports/kt02.md`, `postman/KT2_Service_Integration.postman_collection.json`
и `postman/README.md` с инструкцией запуска.

Результат проверки 23.10.2026: **12/12 интеграционных тестов** и **46/46 проверок Postman**
в двух прогонах: 38 при работающих сервисах и 8 при остановленном users.
Перед вторым прогоном должны быть сохранены `userId` и `orderId`; orders остаётся работающим.
Скриншоты и источники подтверждения результатов приведены в отчёте.
