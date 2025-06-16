# Поисковая система

## Описание проекта

Приложение "Поисковая система" разработано для обхода указанных веб-сайтов,
индексации контента и предоставления релевантных результатов поиска 
на основе пользовательских запросов.

## Стек используемых технологий
![Static Badge](https://img.shields.io/badge/Java-17-blue)
![Static Badge](https://img.shields.io/badge/Spring_Boot-3-green)
![Static Badge](https://img.shields.io/badge/Apache_Maven-grey)
![Static Badge](https://img.shields.io/badge/MySQL-grey)
![Static Badge](https://img.shields.io/badge/Thymeleaf-grey)
![Static Badge](https://img.shields.io/badge/Lombok-grey)
![Static Badge](https://img.shields.io/badge/Jsoup-grey)

## Инструкция по локальному запуску проекта

### Предварительные требования:

- Установленный JDK (рекомендуется JDK 17)
- Установленный Maven
- MySQL
- Git

### Шаги для запуска:

1. *Клонирование репозитория:*

```sh
  git clone https://github.com/DimTiabu/search-engine.git
```

2. *Установка списка сайтов для индексации в файле [application.yml](application.yml).* Например:

```yaml
indexing-settings:
  sites:
    - url: https://volochek.life/
      name: volochek.life
    - url: https://www.playback.ru
      name: PlayBack.Ru
    - url: https://ipfran.ru
      name: Ipfran.Ru
```

3. *Установка логина и пароля для доступа к БД в файле [application.yml](application.yml).* Например:
```yaml
spring:
  datasource:
    username: testUsername
    password: testPassword
```
4. *Создание базы данных search_engine_db в MySQL (если она ещё не создана).*


5. *Переход в директорию репозитория:*

```sh
  cd search-engine
```

6. *Сборка проекта:*

```sh
  mvn clean install
```

7. *Запуск приложения:*

```sh
  mvn spring-boot:run
```

---
### Пользовательский интерфейс и API

Приложение предоставляет REST API для взаимодействия
с функционалом поисковой системы.

Для начала работы с приложением перейдите в браузере по адресу: http://localhost:8080/.  
В открывшемся окне вы увидите три вкладки:
![Главный экран приложения](src/main/resources/app_screen.png)

1. **Вкладка DASHBOARD:** 
   * Предоставление информации об индексированных сайтах, страницах и леммах.


2. **Вкладка MANAGEMENT:** 
   * Запуск полной индексации сайтов, указанных в файле [application.yml](application.yml). 
   * Запуск индексации конкретной страницы.
   * Остановка запущенной индексации.


3. **Вкладка SEARCH:**
   * Поиск страниц по запросу среди всех индексированных сайтов.
   * Поиск страниц по запросу на конкретном сайте.