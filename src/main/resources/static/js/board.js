/* ── State ────────────────────────────────────────────────────────────────── */
let editMode = false;
let currentTaskId = null;
let columnSortable = null;
const taskSortables = [];

/* ── Init ─────────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  initTaskSortables();

  document.getElementById('btnEditMode').addEventListener('click', toggleEditMode);
  document.getElementById('btnCreateTask').addEventListener('click', openCreateModal);

  document.querySelectorAll('.quick-add-input').forEach(input => {
    input.addEventListener('keydown', e => {
      if (e.key === 'Enter') quickAddFromInput(input);
    });
  });
});

/* ── Drag-and-drop tasks ──────────────────────────────────────────────────── */
function initTaskSortables() {
  document.querySelectorAll('.task-list').forEach(list => {
    const s = Sortable.create(list, {
      group: 'tasks',
      animation: 150,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      handle: '.task-card',
      onEnd(evt) {
        const taskId = evt.item.dataset.taskId;
        const targetColId = evt.to.dataset.colId;
        const newPosition = evt.newIndex;
        api('POST', `/api/tasks/${taskId}/move`, { targetColumnId: parseInt(targetColId), newPosition });
      }
    });
    taskSortables.push(s);
  });
}

/* ── Edit mode (column drag) ──────────────────────────────────────────────── */
function toggleEditMode() {
  editMode = !editMode;
  const btn = document.getElementById('btnEditMode');
  btn.textContent = editMode ? 'Listo' : 'Editar columnas';
  btn.classList.toggle('btn-primary', editMode);
  btn.classList.toggle('btn-ghost', !editMode);

  document.querySelectorAll('.column-edit-actions').forEach(el => {
    el.style.display = editMode ? 'flex' : 'none';
  });
  document.getElementById('addColumnBtn').style.display = editMode ? 'flex' : 'none';

  if (editMode) {
    columnSortable = Sortable.create(document.getElementById('board'), {
      animation: 150,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      handle: '.drag-handle-col',
      filter: '.add-column-btn',
      onEnd() { persistColumnOrder(); }
    });
  } else {
    columnSortable && columnSortable.destroy();
    columnSortable = null;
  }
}

function persistColumnOrder() {
  const ids = [...document.querySelectorAll('.column[data-col-id]')]
    .map(c => parseInt(c.dataset.colId));
  api('POST', '/api/columns/reorder', ids);
}

/* ── Column operations ────────────────────────────────────────────────────── */
function addColumn() {
  const name = prompt('Nombre de la columna:');
  if (!name || !name.trim()) return;
  api('POST', '/api/columns', { name }).then(() => location.reload());
}

function renameColumn(btn) {
  const colId = btn.dataset.colId;
  const colEl = document.querySelector(`.column[data-col-id="${colId}"]`);
  const title = colEl.querySelector('.column-title');
  const current = title.textContent;
  const name = prompt('Nuevo nombre:', current);
  if (!name || name.trim() === current) return;
  api('PUT', `/api/columns/${colId}`, { name }).then(() => {
    title.textContent = name.trim();
  });
}

function deleteColumn(btn) {
  const colId = btn.dataset.colId;
  if (!confirm('¿Eliminar esta columna y todas sus tareas?')) return;
  api('DELETE', `/api/columns/${colId}`).then(() => {
    document.querySelector(`.column[data-col-id="${colId}"]`).remove();
  });
}

/* ── Quick add ────────────────────────────────────────────────────────────── */
function quickAdd(btn) {
  const colId = btn.dataset.colId;
  const input = document.querySelector(`.quick-add-input[data-col-id="${colId}"]`);
  quickAddFromInput(input);
}

function quickAddFromInput(input) {
  const title = input.value.trim();
  if (!title) return;
  const colId = input.dataset.colId;
  api('POST', '/api/tasks/quick', { title, columnId: parseInt(colId) })
    .then(() => location.reload());
}

/* ── Task modal ───────────────────────────────────────────────────────────── */
function openCreateModal() {
  const firstCol = document.querySelector('.column[data-col-id]');
  if (!firstCol) return;
  currentTaskId = null;
  document.getElementById('modalTitle').textContent = 'Nueva tarea';
  document.getElementById('modalTaskTitle').value = '';
  document.getElementById('modalTaskNotes').value = '';
  document.getElementById('modalTaskDue').value = '';
  renderLabelPicker([], parseInt(firstCol.dataset.colId));
  document.getElementById('taskModal').style.display = 'flex';
  setTimeout(() => document.getElementById('modalTaskTitle').focus(), 50);
}

function openTask(btn) {
  const taskId = btn.dataset.taskId;
  fetch(`/api/tasks/${taskId}`)
    .then(r => r.json())
    .then(task => {
      currentTaskId = task.id;
      document.getElementById('modalTitle').textContent = 'Editar tarea';
      document.getElementById('modalTaskTitle').value = task.title;
      document.getElementById('modalTaskNotes').value = task.notes || '';
      document.getElementById('modalTaskDue').value = task.dueDate || '';
      const selectedIds = (task.labels || []).map(l => l.id);
      renderLabelPicker(selectedIds, task.column?.id);
      document.getElementById('taskModal').style.display = 'flex';
    });
}

function renderLabelPicker(selectedIds, columnId) {
  const container = document.getElementById('modalLabels');
  container.innerHTML = '';
  container.dataset.columnId = columnId;
  (window.KANDO.labels || []).forEach(lbl => {
    const chip = document.createElement('span');
    chip.className = 'label-pick-item' + (selectedIds.includes(lbl.id) ? ' selected' : '');
    chip.textContent = lbl.name;
    chip.style.background = lbl.color + '22';
    chip.style.color = lbl.color;
    chip.dataset.labelId = lbl.id;
    chip.addEventListener('click', () => chip.classList.toggle('selected'));
    container.appendChild(chip);
  });
}

function saveTask() {
  const title = document.getElementById('modalTaskTitle').value.trim();
  if (!title) { document.getElementById('modalTaskTitle').focus(); return; }

  const notes = document.getElementById('modalTaskNotes').value || null;
  const dueDate = document.getElementById('modalTaskDue').value || null;
  const labelIds = [...document.querySelectorAll('.label-pick-item.selected')]
    .map(el => parseInt(el.dataset.labelId));

  if (currentTaskId) {
    api('PUT', `/api/tasks/${currentTaskId}`, { title, notes, dueDate, labelIds })
      .then(() => location.reload());
  } else {
    const colId = parseInt(document.getElementById('modalLabels').dataset.columnId);
    api('POST', '/api/tasks/quick', { title, columnId: colId })
      .then(task => {
        if (notes || dueDate || labelIds.length) {
          return api('PUT', `/api/tasks/${task.id}`, { title, notes, dueDate, labelIds });
        }
      })
      .then(() => location.reload());
  }
}

function deleteCurrentTask() {
  if (!currentTaskId) return;
  if (!confirm('¿Eliminar esta tarea?')) return;
  api('DELETE', `/api/tasks/${currentTaskId}`).then(() => location.reload());
}

function closeModal() {
  document.getElementById('taskModal').style.display = 'none';
  currentTaskId = null;
}

function closeModalOutside(e) {
  if (e.target === document.getElementById('taskModal')) closeModal();
}

/* ── Helpers ──────────────────────────────────────────────────────────────── */
function api(method, url, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' }
  };
  if (body !== undefined) opts.body = JSON.stringify(body);
  return fetch(url, opts).then(r => r.ok ? (r.status === 204 ? null : r.json()) : Promise.reject(r));
}
