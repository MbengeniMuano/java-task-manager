Minimalistic Dark‑Mode Task Manager (Java)

Overview
- A lightweight task management app with a clean, minimal dark/light UI and a pure Java backend (no frameworks). It provides RESTful CRUD for tasks and serves a zero‑dependency frontend (HTML/CSS/JS). Ideal for quick full‑stack demos and learning.

Key Features
- Minimal, fast, and framework‑free: Java `HttpServer` + vanilla HTML/CSS/JS
- Dark/light theme toggle with localStorage persistence
- REST CRUD endpoints: list, create, update, delete tasks
- In‑memory storage for simplicity (resets on restart)
- Ephemeral port; prints the actual URL on startup (optionally set `PORT`)

Technologies
- Java 17+ (JDK `com.sun.net.httpserver.HttpServer`)
- HTML, CSS, JavaScript (vanilla)

Prerequisites
- JDK 17 or newer (`java` and `javac` in PATH)
- Windows PowerShell or any terminal
- Optional: Git (to clone/push)

Project Structure
- `src/Main.java` – Java HTTP server, static serving, and `/api/tasks` REST
- `public/index.html` – UI markup with theme toggle and task list
- `public/styles.css` – Minimalistic dark/light styles
- `public/app.js` – Frontend logic: fetch tasks, CRUD actions, theme handling

Installation
- Clone or download:
  - Clone: `git clone https://github.com/MbengeniMuano/java-task-manager.git`
  - Or download ZIP and extract
- Change into the project directory.

Build & Run
1) Compile: `javac -d out src/Main.java`
2) Run: `java -cp out Main`
3) Open the printed URL (e.g., `http://localhost:61577/`).

Configuration
- `PORT`: Set to a specific port (e.g., `setx PORT 8080` then restart terminal). If unset, the OS assigns an ephemeral port and the server prints it.

API Endpoints
- `GET /api/tasks` – List tasks
- `POST /api/tasks` – Create task
  - Body: `{ "title": "Write docs", "completed": false }`
- `PUT /api/tasks/{id}` – Update task
  - Body: `{ "title": "New title", "completed": true }`
- `DELETE /api/tasks/{id}` – Delete task

Example Requests (PowerShell curl)
- List: `curl http://localhost:61577/api/tasks`
- Create: `curl -X POST -H "Content-Type: application/json" -d '{"title":"Try the app"}' http://localhost:61577/api/tasks`
- Update: `curl -X PUT -H "Content-Type: application/json" -d '{"completed":true}' http://localhost:61577/api/tasks/1`
- Delete: `curl -X DELETE http://localhost:61577/api/tasks/1`

Usage
- Add tasks via the input field and “Add” button.
- Toggle completion with the checkbox, edit via the “Edit” button, and delete tasks.
- Use the 🌗 button to switch between dark and light modes (saved in localStorage).

Notes
- Data is stored in memory and resets when the server restarts.
- The frontend is intentionally minimal for clarity and learning.

