const apiBase = "/api/tasks";

const els = {
  themeToggle: document.getElementById("themeToggle"),
  newTaskForm: document.getElementById("newTaskForm"),
  newTaskInput: document.getElementById("newTaskInput"),
  taskList: document.getElementById("taskList"),
};

// Theme toggle: switches root class between default (dark) and light
const THEME_KEY = "taskapp-theme";
function applySavedTheme() {
  const theme = localStorage.getItem(THEME_KEY);
  document.documentElement.classList.toggle("light", theme === "light");
}
applySavedTheme();

els.themeToggle.addEventListener("click", () => {
  const isLight = document.documentElement.classList.toggle("light");
  localStorage.setItem(THEME_KEY, isLight ? "light" : "dark");
});

// Fetch and render tasks
async function loadTasks() {
  const res = await fetch(apiBase);
  const list = await res.json();
  renderTasks(list);
}

function renderTasks(list) {
  els.taskList.innerHTML = "";
  list
    .sort((a, b) => Number(a.completed) - Number(b.completed) || b.createdAt - a.createdAt)
    .forEach((t) => {
      const li = document.createElement("li");
      li.className = "task-item";

      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.checked = !!t.completed;
      checkbox.addEventListener("change", async () => {
        await updateTask(t.id, { completed: checkbox.checked });
        await loadTasks();
      });

      const title = document.createElement("div");
      title.className = "task-title" + (t.completed ? " task-completed" : "");
      title.textContent = t.title;

      const actions = document.createElement("div");
      actions.className = "task-actions";

      const editBtn = document.createElement("button");
      editBtn.textContent = "Edit";
      editBtn.addEventListener("click", async () => {
        const next = prompt("Edit task title", t.title);
        if (next && next.trim() && next !== t.title) {
          await updateTask(t.id, { title: next.trim() });
          await loadTasks();
        }
      });

      const delBtn = document.createElement("button");
      delBtn.textContent = "Delete";
      delBtn.addEventListener("click", async () => {
        await deleteTask(t.id);
        await loadTasks();
      });

      actions.appendChild(editBtn);
      actions.appendChild(delBtn);

      li.appendChild(checkbox);
      li.appendChild(title);
      li.appendChild(actions);
      els.taskList.appendChild(li);
    });
}

els.newTaskForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const value = els.newTaskInput.value.trim();
  if (!value) return;
  await createTask({ title: value });
  els.newTaskInput.value = "";
  await loadTasks();
});

async function createTask(data) {
  await fetch(apiBase, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

async function updateTask(id, data) {
  await fetch(`${apiBase}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

async function deleteTask(id) {
  await fetch(`${apiBase}/${id}`, { method: "DELETE" });
}

loadTasks();